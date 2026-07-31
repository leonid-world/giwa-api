package com.leonid.giwaapi.transaction;

import com.leonid.giwaapi.receivable.ReceivableResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class BlockchainTransactionVerifier {

    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final Pattern HASH_PATTERN =
            Pattern.compile("^0x[a-fA-F0-9]{64}$");
    private static final Pattern DATA_PATTERN =
            Pattern.compile("^0x(?:[a-fA-F0-9]{2})*$");
    private static final Pattern QUANTITY_PATTERN =
            Pattern.compile("^0x(?:0|[1-9a-fA-F][a-fA-F0-9]*)$");
    private static final String ZERO_ADDRESS =
            "0x0000000000000000000000000000000000000000";
    private static final String ZERO_HASH =
            "0x" + "0".repeat(64);

    private static final String CREATE_SELECTOR = "0x7e76b240";
    private static final String VERIFY_SELECTOR = "0xdc427644";
    private static final String TOKENIZE_SELECTOR = "0x220f6023";

    private static final String CREATED_TOPIC =
            "0x1bdd8be99eb9596b98b73c8a3332842b0d72ad22d401c34ec8f9713c5a131b83";
    private static final String VERIFIED_TOPIC =
            "0x16e60068e1ac09e3fe4ab4768c3d6e11881d9c6dbfbac9dbd309d43279708a1d";
    private static final String TOKENIZED_TOPIC =
            "0xc6175902bb25116fdbe490fcc358b7e2466cd0c1404c77dd33dbf9e1ca784ff6";
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private final GiwaRpcClient rpcClient;
    private final BlockchainRpcProperties properties;

    public BlockchainTransactionVerifier(
            GiwaRpcClient rpcClient,
            BlockchainRpcProperties properties
    ) {
        this.rpcClient = rpcClient;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VerifiedBlockchainTransaction verify(
            BlockchainTransactionResponse transaction,
            ReceivableResponse receivable,
            Long expectedOnchainReceivableId
    ) {
        long configuredChainId = configuredChainId();
        String configuredContract = configuredContract();
        if (transaction.getContractAddress() == null
                || !ADDRESS_PATTERN.matcher(
                transaction.getContractAddress()
        ).matches()
                || !sameHex(
                transaction.getContractAddress(),
                configuredContract
        )) {
            throw configurationMismatch(
                    "저널의 컨트랙트 주소가 서버 설정과 일치하지 않습니다."
            );
        }

        GiwaRpcProof proof;
        try {
            proof = rpcClient.getTransactionProof(transaction.getTxHash());
        } catch (GiwaRpcClientException exception) {
            throw unavailable();
        }
        if (proof == null) {
            throw invalidRpcResponse("RPC 검증 응답을 확인할 수 없습니다.");
        }

        long rpcChainId = positiveLongQuantity(
                proof.chainId(),
                "RPC 체인 ID"
        );
        if (rpcChainId != configuredChainId) {
            throw configurationMismatch(
                    "연결된 RPC 네트워크가 설정된 GIWA 체인과 일치하지 않습니다."
            );
        }

        GiwaRpcProof.Transaction rpcTransaction = proof.transaction();
        GiwaRpcProof.Receipt receipt = proof.receipt();
        if (rpcTransaction == null || receipt == null) {
            throw retryable(
                    "BLOCKCHAIN_TRANSACTION_PENDING",
                    "블록체인 트랜잭션이 아직 채굴되지 않았습니다."
            );
        }

        requireRpcHash(
                rpcTransaction.hash(),
                transaction.getTxHash(),
                "RPC 트랜잭션 해시가 저널과 일치하지 않습니다."
        );
        requireRpcHash(
                receipt.transactionHash(),
                transaction.getTxHash(),
                "RPC receipt 해시가 저널과 일치하지 않습니다."
        );

        long blockNumber = positiveLongQuantity(
                receipt.blockNumber(),
                "receipt 블록 번호"
        );
        long transactionBlockNumber = positiveLongQuantity(
                rpcTransaction.blockNumber(),
                "트랜잭션 블록 번호"
        );
        String receiptBlockHash = normalizeHash(receipt.blockHash());
        String transactionBlockHash = normalizeHash(rpcTransaction.blockHash());
        if (blockNumber != transactionBlockNumber
                || !sameHex(transactionBlockHash, receiptBlockHash)) {
            throw retryable(
                    "BLOCKCHAIN_REORG_DETECTED",
                    "RPC 트랜잭션과 receipt가 서로 다른 블록을 가리킵니다. "
                            + "잠시 후 다시 확인해 주세요."
            );
        }

        requireCanonicalBlock(proof, blockNumber, receiptBlockHash);
        requireConfirmations(proof.latestBlockNumber(), blockNumber);

        BigInteger receiptStatus = unsignedQuantity(
                receipt.status(),
                "receipt 상태"
        );
        if (BigInteger.ZERO.equals(receiptStatus)) {
            throw terminal(
                    "BLOCKCHAIN_TRANSACTION_REVERTED",
                    "블록체인에서 트랜잭션 실행이 실패했습니다."
            );
        }
        if (!BigInteger.ONE.equals(receiptStatus)) {
            throw invalidRpcResponse("receipt 상태 값이 올바르지 않습니다.");
        }

        requireRpcAddressPair(
                rpcTransaction.from(),
                receipt.from(),
                "RPC 트랜잭션과 receipt의 서명 지갑이 일치하지 않습니다."
        );
        requireRpcAddressPair(
                rpcTransaction.to(),
                receipt.to(),
                "RPC 트랜잭션과 receipt의 대상 주소가 일치하지 않습니다."
        );
        requireSameAddress(
                rpcTransaction.from(),
                transaction.getWalletAddress(),
                "트랜잭션 서명 지갑이 등록 지갑과 일치하지 않습니다."
        );
        requireSameAddress(
                rpcTransaction.to(),
                configuredContract,
                "트랜잭션 대상 컨트랙트가 서버 설정과 일치하지 않습니다."
        );

        if (!BigInteger.ZERO.equals(unsignedQuantity(
                rpcTransaction.value(),
                "트랜잭션 value"
        ))) {
            throw verificationFailed(
                    "ReceivableFinance 호출에는 네이티브 토큰 value를 전송할 수 없습니다."
            );
        }
        if (rpcTransaction.chainId() != null
                && positiveLongQuantity(
                rpcTransaction.chainId(),
                "트랜잭션 체인 ID"
        ) != rpcChainId) {
            throw invalidRpcResponse(
                    "트랜잭션 체인 ID가 RPC 네트워크와 일치하지 않습니다."
            );
        }

        long gasUsed = positiveLongQuantity(receipt.gasUsed(), "사용된 가스");
        String gasPriceValue = receipt.effectiveGasPrice() != null
                ? receipt.effectiveGasPrice()
                : rpcTransaction.gasPrice();
        BigDecimal effectiveGasPrice = new BigDecimal(
                unsignedQuantity(gasPriceValue, "유효 가스 가격")
        );

        BlockchainTransactionType type = transactionType(
                transaction.getTransactionType()
        );
        EventValues event = switch (type) {
            case CREATE_RECEIVABLE -> verifyCreate(
                    rpcTransaction.input(),
                    receipt.logs(),
                    configuredContract,
                    transaction,
                    receivable
            );
            case VERIFY_RECEIVABLE -> verifyVerify(
                    rpcTransaction.input(),
                    receipt.logs(),
                    configuredContract,
                    transaction,
                    receivable
            );
            case TOKENIZE_RECEIVABLE -> verifyTokenize(
                    rpcTransaction.input(),
                    receipt.logs(),
                    configuredContract,
                    transaction,
                    receivable
            );
        };

        if (expectedOnchainReceivableId != null
                && !Objects.equals(
                expectedOnchainReceivableId,
                event.receivableId()
        )) {
            throw eventMismatch(
                    "요청한 온체인 채권 ID가 검증된 이벤트와 일치하지 않습니다."
            );
        }

        return new VerifiedBlockchainTransaction(
                rpcChainId,
                blockNumber,
                receiptBlockHash,
                gasUsed,
                effectiveGasPrice,
                event.receivableId(),
                event.tokenId()
        );
    }

    private EventValues verifyCreate(
            String input,
            List<GiwaRpcProof.Log> logs,
            String configuredContract,
            BlockchainTransactionResponse transaction,
            ReceivableResponse receivable
    ) {
        requireFunctionName(transaction, "createReceivable");
        List<String> arguments = calldataWords(input, CREATE_SELECTOR, 6);
        requireSameAddress(
                decodeAddress(arguments.get(0)),
                receivable.getBuyerWalletAddress(),
                "createReceivable Buyer가 DB 채권과 일치하지 않습니다."
        );
        requireUint(
                arguments.get(1),
                decimalInteger(receivable.getFaceValue(), "채권 금액"),
                "createReceivable 채권 금액이 DB와 일치하지 않습니다."
        );
        requireUint(
                arguments.get(2),
                decimalInteger(receivable.getFundingAmount(), "펀딩 금액"),
                "createReceivable 펀딩 금액이 DB와 일치하지 않습니다."
        );
        requireUint(
                arguments.get(3),
                dateEpoch(receivable.getIssueDate()),
                "createReceivable 발행일이 DB와 일치하지 않습니다."
        );
        requireUint(
                arguments.get(4),
                dateEpoch(receivable.getMaturityDate()),
                "createReceivable 만기일이 DB와 일치하지 않습니다."
        );
        requireHash(
                "0x" + arguments.get(5),
                expectedDocumentHash(receivable.getDocumentHash()),
                "createReceivable 문서 해시가 DB와 일치하지 않습니다."
        );

        GiwaRpcProof.Log log = singleEvent(
                logs,
                configuredContract,
                CREATED_TOPIC
        );
        requireTopicCount(log, 4);
        List<String> data = dataWords(log.data(), 5);
        Long receivableId = positiveLongWord(
                log.topics().get(1),
                "ReceivableCreated 채권 ID"
        );
        requireSameAddress(
                decodeAddress(topicWord(log, 2)),
                receivable.getSellerWalletAddress(),
                "ReceivableCreated Seller가 DB와 일치하지 않습니다."
        );
        requireSameAddress(
                decodeAddress(topicWord(log, 3)),
                receivable.getBuyerWalletAddress(),
                "ReceivableCreated Buyer가 DB와 일치하지 않습니다."
        );
        requireUint(
                data.get(0),
                decimalInteger(receivable.getFaceValue(), "채권 금액"),
                "ReceivableCreated 채권 금액이 DB와 일치하지 않습니다."
        );
        requireUint(
                data.get(1),
                decimalInteger(receivable.getFundingAmount(), "펀딩 금액"),
                "ReceivableCreated 펀딩 금액이 DB와 일치하지 않습니다."
        );
        requireUint(
                data.get(2),
                dateEpoch(receivable.getIssueDate()),
                "ReceivableCreated 발행일이 DB와 일치하지 않습니다."
        );
        requireUint(
                data.get(3),
                dateEpoch(receivable.getMaturityDate()),
                "ReceivableCreated 만기일이 DB와 일치하지 않습니다."
        );
        requireHash(
                "0x" + data.get(4),
                expectedDocumentHash(receivable.getDocumentHash()),
                "ReceivableCreated 문서 해시가 DB와 일치하지 않습니다."
        );
        return new EventValues(receivableId, null);
    }

    private EventValues verifyVerify(
            String input,
            List<GiwaRpcProof.Log> logs,
            String configuredContract,
            BlockchainTransactionResponse transaction,
            ReceivableResponse receivable
    ) {
        requireFunctionName(transaction, "verifyReceivable");
        Long expectedReceivableId = requiredOnchainId(receivable);
        List<String> arguments = calldataWords(input, VERIFY_SELECTOR, 1);
        requireUint(
                arguments.get(0),
                BigInteger.valueOf(expectedReceivableId),
                "verifyReceivable 채권 ID가 DB와 일치하지 않습니다."
        );

        GiwaRpcProof.Log log = singleEvent(
                logs,
                configuredContract,
                VERIFIED_TOPIC
        );
        requireTopicCount(log, 3);
        requireEmptyData(log);
        Long eventReceivableId = positiveLongWord(
                log.topics().get(1),
                "ReceivableVerified 채권 ID"
        );
        if (!Objects.equals(expectedReceivableId, eventReceivableId)) {
            throw eventMismatch(
                    "ReceivableVerified 채권 ID가 DB와 일치하지 않습니다."
            );
        }
        requireSameAddress(
                decodeAddress(topicWord(log, 2)),
                receivable.getBuyerWalletAddress(),
                "ReceivableVerified Buyer가 DB와 일치하지 않습니다."
        );
        return new EventValues(eventReceivableId, null);
    }

    private EventValues verifyTokenize(
            String input,
            List<GiwaRpcProof.Log> logs,
            String configuredContract,
            BlockchainTransactionResponse transaction,
            ReceivableResponse receivable
    ) {
        requireFunctionName(transaction, "tokenizeReceivable");
        Long expectedReceivableId = requiredOnchainId(receivable);
        List<String> arguments = calldataWords(input, TOKENIZE_SELECTOR, 1);
        requireUint(
                arguments.get(0),
                BigInteger.valueOf(expectedReceivableId),
                "tokenizeReceivable 채권 ID가 DB와 일치하지 않습니다."
        );

        GiwaRpcProof.Log tokenized = singleEvent(
                logs,
                configuredContract,
                TOKENIZED_TOPIC
        );
        requireTopicCount(tokenized, 4);
        requireEmptyData(tokenized);
        Long eventReceivableId = positiveLongWord(
                tokenized.topics().get(1),
                "ReceivableTokenized 채권 ID"
        );
        Long tokenId = positiveLongWord(
                tokenized.topics().get(2),
                "ReceivableTokenized 토큰 ID"
        );
        if (!Objects.equals(expectedReceivableId, eventReceivableId)) {
            throw eventMismatch(
                    "ReceivableTokenized 채권 ID가 DB와 일치하지 않습니다."
            );
        }
        requireSameAddress(
                decodeAddress(topicWord(tokenized, 3)),
                configuredContract,
                "ReceivableTokenized custodian이 컨트랙트와 일치하지 않습니다."
        );

        GiwaRpcProof.Log transfer = singleEvent(
                logs,
                configuredContract,
                TRANSFER_TOPIC
        );
        requireTopicCount(transfer, 4);
        requireEmptyData(transfer);
        requireSameAddress(
                decodeAddress(topicWord(transfer, 1)),
                ZERO_ADDRESS,
                "ERC-721 민팅 Transfer의 발신 주소가 0 주소가 아닙니다."
        );
        requireSameAddress(
                decodeAddress(topicWord(transfer, 2)),
                configuredContract,
                "ERC-721 민팅 대상이 escrow 컨트랙트가 아닙니다."
        );
        if (!Objects.equals(
                tokenId,
                positiveLongWord(
                        transfer.topics().get(3),
                        "ERC-721 Transfer 토큰 ID"
                )
        )) {
            throw eventMismatch(
                    "ERC-721 Transfer 토큰 ID가 ReceivableTokenized와 일치하지 않습니다."
            );
        }
        return new EventValues(eventReceivableId, tokenId);
    }

    private void requireCanonicalBlock(
            GiwaRpcProof proof,
            long receiptBlockNumber,
            String receiptBlockHash
    ) {
        GiwaRpcProof.Block block = proof.canonicalBlock();
        if (block == null) {
            throw retryable(
                    "BLOCKCHAIN_CANONICAL_BLOCK_PENDING",
                    "receipt 블록의 canonical 상태를 아직 확인할 수 없습니다."
            );
        }
        if (positiveLongQuantity(block.number(), "canonical 블록 번호")
                != receiptBlockNumber
                || !sameHex(block.hash(), receiptBlockHash)) {
            throw retryable(
                    "BLOCKCHAIN_REORG_DETECTED",
                    "블록 재구성 가능성이 감지되었습니다. 잠시 후 다시 확인해 주세요."
            );
        }
    }

    private void requireConfirmations(
            String latestBlockValue,
            long receiptBlockNumber
    ) {
        long latestBlock = positiveLongQuantity(
                latestBlockValue,
                "최신 블록 번호"
        );
        if (latestBlock < receiptBlockNumber) {
            throw invalidRpcResponse(
                    "최신 블록 번호가 receipt 블록보다 작습니다."
            );
        }
        long confirmations;
        try {
            confirmations = Math.addExact(
                    Math.subtractExact(latestBlock, receiptBlockNumber),
                    1L
            );
        } catch (ArithmeticException exception) {
            throw invalidRpcResponse("블록 confirmation 범위를 확인할 수 없습니다.");
        }
        int required = minimumConfirmations();
        if (confirmations < required) {
            throw retryable(
                    "BLOCKCHAIN_CONFIRMATIONS_PENDING",
                    "블록체인 confirmation이 부족합니다. 필요: "
                            + required
                            + ", 현재: "
                            + confirmations
            );
        }
    }

    private GiwaRpcProof.Log singleEvent(
            List<GiwaRpcProof.Log> logs,
            String contractAddress,
            String topic
    ) {
        List<GiwaRpcProof.Log> matches = new ArrayList<>();
        if (logs != null) {
            for (GiwaRpcProof.Log log : logs) {
                if (log != null
                        && !log.removed()
                        && sameHex(log.address(), contractAddress)
                        && log.topics() != null
                        && !log.topics().isEmpty()
                        && sameHex(log.topics().get(0), topic)) {
                    matches.add(log);
                }
            }
        }
        if (matches.size() != 1) {
            throw eventMismatch(
                    "예상한 컨트랙트 이벤트를 정확히 하나 찾지 못했습니다."
            );
        }
        return matches.get(0);
    }

    private List<String> calldataWords(
            String value,
            String selector,
            int wordCount
    ) {
        String normalized = normalizedData(value);
        int expectedLength = 2 + 8 + wordCount * 64;
        if (normalized.length() != expectedLength
                || !normalized.startsWith(selector)) {
            throw verificationFailed(
                    "트랜잭션 calldata 함수 또는 길이가 예상과 다릅니다."
            );
        }
        return splitWords(normalized.substring(10), wordCount);
    }

    private List<String> dataWords(String value, int wordCount) {
        String normalized = normalizedData(value);
        if (normalized.length() != 2 + wordCount * 64) {
            throw eventMismatch("이벤트 data 길이가 예상과 다릅니다.");
        }
        return splitWords(normalized.substring(2), wordCount);
    }

    private List<String> splitWords(String value, int wordCount) {
        List<String> words = new ArrayList<>();
        for (int index = 0; index < wordCount; index++) {
            words.add(value.substring(index * 64, (index + 1) * 64));
        }
        return words;
    }

    private String topicWord(GiwaRpcProof.Log log, int index) {
        String topic = log.topics().get(index);
        if (topic == null || !HASH_PATTERN.matcher(topic).matches()) {
            throw eventMismatch("이벤트 topic 형식이 올바르지 않습니다.");
        }
        return topic.substring(2).toLowerCase(Locale.ROOT);
    }

    private void requireTopicCount(GiwaRpcProof.Log log, int expected) {
        if (log.topics() == null || log.topics().size() != expected) {
            throw eventMismatch("이벤트 topic 개수가 예상과 다릅니다.");
        }
    }

    private void requireEmptyData(GiwaRpcProof.Log log) {
        if (!"0x".equalsIgnoreCase(log.data())) {
            throw eventMismatch("이벤트 data가 비어 있지 않습니다.");
        }
    }

    private void requireFunctionName(
            BlockchainTransactionResponse transaction,
            String expected
    ) {
        if (!expected.equals(transaction.getFunctionName())) {
            throw verificationFailed(
                    "저널의 컨트랙트 함수가 트랜잭션 종류와 일치하지 않습니다."
            );
        }
    }

    private BlockchainTransactionType transactionType(String value) {
        try {
            return BlockchainTransactionType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw verificationFailed("저널의 트랜잭션 종류를 확인할 수 없습니다.");
        }
    }

    private String decodeAddress(String word) {
        if (word == null
                || word.length() != 64
                || !word.substring(0, 24).equals("0".repeat(24))) {
            throw verificationFailed("ABI address word 형식이 올바르지 않습니다.");
        }
        return "0x" + word.substring(24).toLowerCase(Locale.ROOT);
    }

    private void requireUint(
            String word,
            BigInteger expected,
            String message
    ) {
        if (!decodeUint(word).equals(expected)) {
            throw verificationFailed(message);
        }
    }

    private BigInteger decodeUint(String word) {
        if (word == null
                || word.length() != 64
                || !word.matches("^[a-f0-9]{64}$")) {
            throw verificationFailed("ABI uint256 word 형식이 올바르지 않습니다.");
        }
        return new BigInteger(word, 16);
    }

    private Long positiveLongWord(String topic, String fieldName) {
        if (topic == null || !HASH_PATTERN.matcher(topic).matches()) {
            throw eventMismatch(fieldName + " topic 형식이 올바르지 않습니다.");
        }
        BigInteger value = new BigInteger(topic.substring(2), 16);
        if (value.signum() <= 0 || value.compareTo(
                BigInteger.valueOf(Long.MAX_VALUE)
        ) > 0) {
            throw eventMismatch(fieldName + " 범위가 Java Long을 초과합니다.");
        }
        return value.longValue();
    }

    private BigInteger decimalInteger(BigDecimal value, String fieldName) {
        if (value == null) {
            throw verificationFailed(fieldName + "을 확인할 수 없습니다.");
        }
        try {
            return value.toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw verificationFailed(fieldName + "이 정수가 아닙니다.");
        }
    }

    private BigInteger dateEpoch(java.time.LocalDate value) {
        if (value == null) {
            throw verificationFailed("채권 날짜를 확인할 수 없습니다.");
        }
        return BigInteger.valueOf(
                value.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        );
    }

    private Long requiredOnchainId(ReceivableResponse receivable) {
        Long value = receivable.getOnchainReceivableId();
        if (value == null || value <= 0) {
            throw verificationFailed("DB 온체인 채권 ID를 확인할 수 없습니다.");
        }
        return value;
    }

    private String expectedDocumentHash(String value) {
        if (value == null || value.isBlank()) return ZERO_HASH;
        if (!HASH_PATTERN.matcher(value).matches()) {
            throw verificationFailed("DB 문서 해시 형식이 올바르지 않습니다.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private long configuredChainId() {
        Long chainId = properties.getChainId();
        if (chainId == null || chainId <= 0) {
            throw notConfigured("GIWA_CHAIN_ID");
        }
        return chainId;
    }

    private String configuredContract() {
        String address = properties.getReceivableFinanceAddress();
        if (address == null
                || !ADDRESS_PATTERN.matcher(address).matches()
                || ZERO_ADDRESS.equalsIgnoreCase(address)) {
            throw notConfigured("GIWA_RECEIVABLE_FINANCE_ADDRESS");
        }
        String rpcUrl = properties.getRpcUrl();
        if (rpcUrl == null || rpcUrl.isBlank()) {
            throw notConfigured("GIWA_RPC_URL");
        }
        return address.toLowerCase(Locale.ROOT);
    }

    private int minimumConfirmations() {
        Integer value = properties.getMinConfirmations();
        if (value == null || value <= 0) {
            throw notConfigured("GIWA_MIN_CONFIRMATIONS");
        }
        return value;
    }

    private BigInteger unsignedQuantity(String value, String fieldName) {
        if (value == null || !QUANTITY_PATTERN.matcher(value).matches()) {
            throw invalidRpcResponse(fieldName + " 형식이 올바르지 않습니다.");
        }
        return new BigInteger(value.substring(2), 16);
    }

    private long positiveLongQuantity(String value, String fieldName) {
        BigInteger quantity = unsignedQuantity(value, fieldName);
        if (quantity.signum() <= 0
                || quantity.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw invalidRpcResponse(fieldName + " 범위를 확인할 수 없습니다.");
        }
        return quantity.longValue();
    }

    private String normalizedData(String value) {
        if (value == null || !DATA_PATTERN.matcher(value).matches()) {
            throw verificationFailed("트랜잭션 ABI data 형식이 올바르지 않습니다.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizeHash(String value) {
        if (value == null || !HASH_PATTERN.matcher(value).matches()) {
            throw invalidRpcResponse("블록 해시 형식이 올바르지 않습니다.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private void requireHash(String actual, String expected, String message) {
        if (!sameHex(actual, expected)
                || actual == null
                || !HASH_PATTERN.matcher(actual).matches()) {
            throw verificationFailed(message);
        }
    }

    private void requireRpcHash(String actual, String expected, String message) {
        if (actual == null
                || expected == null
                || !HASH_PATTERN.matcher(actual).matches()
                || !HASH_PATTERN.matcher(expected).matches()
                || !sameHex(actual, expected)) {
            throw invalidRpcResponse(message);
        }
    }

    private void requireRpcAddressPair(
            String first,
            String second,
            String message
    ) {
        if (first == null
                || second == null
                || !ADDRESS_PATTERN.matcher(first).matches()
                || !ADDRESS_PATTERN.matcher(second).matches()
                || !sameHex(first, second)) {
            throw invalidRpcResponse(message);
        }
    }

    private void requireSameAddress(
            String actual,
            String expected,
            String message
    ) {
        if (actual == null
                || expected == null
                || !ADDRESS_PATTERN.matcher(actual).matches()
                || !ADDRESS_PATTERN.matcher(expected).matches()
                || !sameHex(actual, expected)) {
            throw verificationFailed(message);
        }
    }

    private boolean sameHex(String first, String second) {
        return first != null
                && second != null
                && first.equalsIgnoreCase(second);
    }

    private BlockchainVerificationException notConfigured(String variable) {
        return new BlockchainVerificationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BLOCKCHAIN_RPC_NOT_CONFIGURED",
                variable + " 환경변수를 설정해 주세요.",
                false
        );
    }

    private BlockchainVerificationException unavailable() {
        return new BlockchainVerificationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BLOCKCHAIN_RPC_UNAVAILABLE",
                "GIWA RPC에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                false
        );
    }

    private BlockchainVerificationException configurationMismatch(String message) {
        return new BlockchainVerificationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BLOCKCHAIN_RPC_CONFIGURATION_MISMATCH",
                message,
                false
        );
    }

    private BlockchainVerificationException retryable(String code, String message) {
        return new BlockchainVerificationException(
                HttpStatus.CONFLICT,
                code,
                message,
                false
        );
    }

    private BlockchainVerificationException terminal(String code, String message) {
        return new BlockchainVerificationException(
                HttpStatus.CONFLICT,
                code,
                message,
                true
        );
    }

    private BlockchainVerificationException verificationFailed(String message) {
        return terminal(
                "BLOCKCHAIN_TRANSACTION_VERIFICATION_FAILED",
                message
        );
    }

    private BlockchainVerificationException eventMismatch(String message) {
        return terminal("BLOCKCHAIN_EVENT_MISMATCH", message);
    }

    private BlockchainVerificationException invalidRpcResponse(String message) {
        return new BlockchainVerificationException(
                HttpStatus.BAD_GATEWAY,
                "BLOCKCHAIN_RPC_INVALID_RESPONSE",
                message,
                false
        );
    }

    private record EventValues(Long receivableId, Long tokenId) {
    }
}
