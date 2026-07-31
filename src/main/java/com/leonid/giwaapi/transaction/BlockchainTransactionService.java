package com.leonid.giwaapi.transaction;

import com.leonid.giwaapi.auth.User;
import com.leonid.giwaapi.auth.UserMapper;
import com.leonid.giwaapi.common.error.ApiException;
import com.leonid.giwaapi.receivable.ReceivableMapper;
import com.leonid.giwaapi.receivable.ReceivableResponse;
import com.leonid.giwaapi.wallet.Wallet;
import com.leonid.giwaapi.wallet.WalletMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class BlockchainTransactionService {

    private static final String PENDING = "PENDING";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String FAILED = "FAILED";
    private static final Pattern TX_HASH_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{64}$");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final Pattern POSITIVE_LONG_PATTERN = Pattern.compile("^[1-9][0-9]{0,18}$");
    private static final Pattern UNSIGNED_DECIMAL_PATTERN = Pattern.compile("^(0|[1-9][0-9]{0,64})$");
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private final UserMapper userMapper;
    private final WalletMapper walletMapper;
    private final ReceivableMapper receivableMapper;
    private final BlockchainTransactionMapper transactionMapper;
    private final BlockchainTransactionVerifier transactionVerifier;
    private final BlockchainTransactionFailureRecorder failureRecorder;

    public BlockchainTransactionService(
            UserMapper userMapper,
            WalletMapper walletMapper,
            ReceivableMapper receivableMapper,
            BlockchainTransactionMapper transactionMapper,
            BlockchainTransactionVerifier transactionVerifier,
            BlockchainTransactionFailureRecorder failureRecorder
    ) {
        this.userMapper = userMapper;
        this.walletMapper = walletMapper;
        this.receivableMapper = receivableMapper;
        this.transactionMapper = transactionMapper;
        this.transactionVerifier = transactionVerifier;
        this.failureRecorder = failureRecorder;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BlockchainTransactionResponse create(
            String email,
            BlockchainTransactionCreateRequest request
    ) {
        User user = findUser(email);
        ReceivableResponse receivable = findReceivable(request.receivableId());
        BlockchainTransactionType transactionType = parseTransactionType(request.transactionType());
        String walletAddress = requireActorAndGetWalletAddress(
                user.companyId(),
                receivable,
                transactionType
        );
        String contractAddress = normalizeContractAddress(request.contractAddress());
        String txHash = normalizeTxHash(request.txHash());

        BlockchainTransactionResponse existing = transactionMapper.findByTxHash(txHash).orElse(null);
        if (existing != null) {
            if (sameSubmissionMetadata(
                    existing,
                    receivable.getReceivableId(),
                    user.companyId(),
                    walletAddress,
                    transactionType,
                    contractAddress,
                    txHash
            )) {
                return existing;
            }
            throw transactionConflict();
        }

        requireReceivableState(receivable, transactionType);
        requireContractMatches(receivable, transactionType, contractAddress);
        Wallet wallet = findStoredWallet(walletAddress, user.companyId());

        BlockchainTransaction transaction = new BlockchainTransaction();
        transaction.setReceivableId(receivable.getReceivableId());
        transaction.setCompanyId(user.companyId());
        transaction.setWalletAddress(walletAddress);
        transaction.setTransactionType(transactionType.name());
        transaction.setChainId(wallet.chainId());
        transaction.setContractAddress(contractAddress);
        transaction.setFunctionName(transactionType.functionName());
        transaction.setTxHash(txHash);
        transaction.setTxStatus(PENDING);

        try {
            transactionMapper.insert(transaction);
        } catch (DataIntegrityViolationException exception) {
            BlockchainTransactionResponse latest = transactionMapper.findByTxHash(txHash).orElse(null);
            if (latest != null && sameSubmissionMetadata(
                    latest,
                    receivable.getReceivableId(),
                    user.companyId(),
                    walletAddress,
                    transactionType,
                    contractAddress,
                    txHash
            )) {
                return latest;
            }
            throw transactionConflict();
        }
        return findTransaction(txHash);
    }

    public BlockchainTransactionResponse markConfirmed(
            String email,
            String txHashValue,
            BlockchainTransactionConfirmedRequest request
    ) {
        User user = findUser(email);
        String txHash = normalizeTxHash(txHashValue);
        parsePositiveLong(request.blockNumber());
        parsePositiveLong(request.gasUsed());
        parseUnsignedDecimal(request.effectiveGasPrice());
        BlockchainTransactionResponse transaction = findOwnedTransaction(txHash, user.companyId());

        if (CONFIRMED.equals(transaction.getTxStatus())
                && transaction.getRpcVerifiedAt() != null) {
            return transaction;
        }
        if (!PENDING.equals(transaction.getTxStatus())
                && !CONFIRMED.equals(transaction.getTxStatus())) {
            throw invalidTransactionStatus(PENDING, transaction.getTxStatus());
        }

        ReceivableResponse receivable = findReceivable(transaction.getReceivableId());
        VerifiedBlockchainTransaction verified;
        try {
            verified = transactionVerifier.verify(
                    transaction,
                    receivable,
                    null
            );
        } catch (BlockchainVerificationException exception) {
            if (!recordTerminalVerificationFailure(transaction, exception)) {
                throw verificationRetryRequired();
            }
            throw exception.toApiException();
        }

        int updated = saveRpcConfirmation(
                transaction,
                verified
        );
        BlockchainTransactionResponse latest = findOwnedTransaction(txHash, user.companyId());
        if ((updated == 1 || CONFIRMED.equals(latest.getTxStatus()))
                && sameRpcVerification(latest, verified)) {
            return latest;
        }
        throw invalidTransactionStatus(PENDING, latest.getTxStatus());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BlockchainTransactionResponse markFailed(
            String email,
            String txHashValue,
            BlockchainTransactionFailedRequest request
    ) {
        User user = findUser(email);
        String txHash = normalizeTxHash(txHashValue);
        String errorCode = normalizeFailureDetail(request.errorCode(), 100);
        String errorMessage = normalizeFailureDetail(request.errorMessage(), 2000);
        BlockchainTransactionResponse transaction = findOwnedTransaction(txHash, user.companyId());

        if (FAILED.equals(transaction.getTxStatus())) {
            if (Objects.equals(transaction.getErrorCode(), errorCode)
                    && Objects.equals(transaction.getErrorMessage(), errorMessage)) {
                return transaction;
            }
            throw transactionConflict();
        }
        if (!PENDING.equals(transaction.getTxStatus())) {
            throw invalidTransactionStatus(PENDING, transaction.getTxStatus());
        }

        int updated = transactionMapper.markFailed(
                txHash,
                user.companyId(),
                errorCode,
                errorMessage
        );
        BlockchainTransactionResponse latest = findOwnedTransaction(txHash, user.companyId());
        if (updated == 1 || FAILED.equals(latest.getTxStatus())
                && Objects.equals(latest.getErrorCode(), errorCode)
                && Objects.equals(latest.getErrorMessage(), errorMessage)) {
            return latest;
        }
        throw invalidTransactionStatus(PENDING, latest.getTxStatus());
    }

    public List<BlockchainTransactionResponse> getAllByReceivable(
            String email,
            Long receivableId
    ) {
        User user = findUser(email);
        if (receivableMapper.findRelatedById(
                receivableId,
                user.companyId()
        ).isPresent()) {
            return transactionMapper.findAllByReceivableId(receivableId);
        }

        List<BlockchainTransactionResponse> ownFundingTransactions =
                transactionMapper.findFundingByReceivableIdAndCompany(
                        receivableId,
                        user.companyId()
                );
        if (!ownFundingTransactions.isEmpty()) {
            return ownFundingTransactions;
        }
        if (receivableMapper.findVisibleById(
                receivableId,
                user.companyId()
        ).isPresent()) {
            return List.of();
        }
        throw new ApiException(
                HttpStatus.NOT_FOUND,
                "RECEIVABLE_NOT_FOUND",
                "매출채권을 찾을 수 없습니다."
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BlockchainTransactionResponse requireConfirmed(
            Long receivableId,
            Long companyId,
            String transactionTypeValue,
            String walletAddressValue,
            String contractAddressValue,
            String txHashValue,
            Long expectedOnchainReceivableId
    ) {
        BlockchainTransactionType transactionType = parseTransactionType(transactionTypeValue);
        String walletAddress = normalizeWalletAddress(walletAddressValue);
        String contractAddress = normalizeContractAddress(contractAddressValue);
        String txHash = normalizeTxHash(txHashValue);
        BlockchainTransactionResponse transaction = transactionMapper.findByTxHash(txHash)
                .orElseThrow(this::transactionNotConfirmed);

        if (!sameSubmissionMetadata(
                transaction,
                receivableId,
                companyId,
                walletAddress,
                transactionType,
                contractAddress,
                txHash
        )) {
            throw transactionConflict();
        }
        if (!CONFIRMED.equals(transaction.getTxStatus())) {
            throw transactionNotConfirmed();
        }

        ReceivableResponse receivable = findReceivable(receivableId);
        VerifiedBlockchainTransaction verified;
        try {
            verified = transactionVerifier.verify(
                    transaction,
                    receivable,
                    null
            );
        } catch (BlockchainVerificationException exception) {
            if (!recordTerminalVerificationFailure(transaction, exception)) {
                throw verificationRetryRequired();
            }
            throw exception.toApiException();
        }

        if (saveRpcConfirmation(transaction, verified) != 1) {
            throw verificationRetryRequired();
        }
        BlockchainTransactionResponse verifiedTransaction = findTransaction(txHash);
        if (!sameRpcVerification(verifiedTransaction, verified)) {
            throw transactionConflict();
        }

        if (!Objects.equals(
                verifiedTransaction.getEventReceivableId(),
                expectedOnchainReceivableId
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "BLOCKCHAIN_SYNCHRONIZATION_EVENT_MISMATCH",
                    "검증된 이벤트의 온체인 채권 ID가 요청과 일치하지 않습니다."
            );
        }
        return verifiedTransaction;
    }

    private boolean recordTerminalVerificationFailure(
            BlockchainTransactionResponse transaction,
            BlockchainVerificationException exception
    ) {
        if (!exception.terminal()) return true;
        int updated = failureRecorder.record(transaction, exception);
        if (updated == 0) {
            updated = transactionMapper.markVerificationFailed(
                    transaction.getTxHash(),
                    transaction.getCompanyId(),
                    exception.code(),
                    exception.getMessage(),
                    transaction.getVerificationVersion()
            );
        }
        if (updated == 1) return true;
        BlockchainTransactionResponse latest = findTransaction(
                transaction.getTxHash()
        );
        return FAILED.equals(latest.getTxStatus())
                && Objects.equals(latest.getErrorCode(), exception.code());
    }

    private int saveRpcConfirmation(
            BlockchainTransactionResponse transaction,
            VerifiedBlockchainTransaction verified
    ) {
        return transactionMapper.markRpcConfirmed(
                transaction.getTxHash(),
                transaction.getCompanyId(),
                verified.chainId(),
                verified.blockNumber(),
                verified.blockHash(),
                verified.gasUsed(),
                verified.effectiveGasPrice(),
                verified.eventReceivableId(),
                verified.eventTokenId(),
                transaction.getVerificationVersion()
        );
    }

    private User findUser(String email) {
        return userMapper.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private ReceivableResponse findReceivable(Long receivableId) {
        return receivableMapper.findById(receivableId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "RECEIVABLE_NOT_FOUND",
                        "매출채권을 찾을 수 없습니다."
                ));
    }

    private BlockchainTransactionResponse findTransaction(String txHash) {
        return transactionMapper.findByTxHash(txHash)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "BLOCKCHAIN_TRANSACTION_SAVE_FAILED",
                        "블록체인 트랜잭션을 저장하지 못했습니다."
                ));
    }

    private BlockchainTransactionResponse findOwnedTransaction(String txHash, Long companyId) {
        return transactionMapper.findByTxHash(txHash)
                .filter(transaction -> Objects.equals(transaction.getCompanyId(), companyId))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BLOCKCHAIN_TRANSACTION_NOT_FOUND",
                        "블록체인 트랜잭션을 찾을 수 없습니다."
                ));
    }

    private BlockchainTransactionType parseTransactionType(String value) {
        if (value == null) {
            throw invalidTransactionType();
        }
        try {
            return BlockchainTransactionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidTransactionType();
        }
    }

    private String requireActorAndGetWalletAddress(
            Long companyId,
            ReceivableResponse receivable,
            BlockchainTransactionType transactionType
    ) {
        return switch (transactionType.requiredActor()) {
            case SELLER -> {
                requireCompany(
                        companyId,
                        receivable.getSellerCompanyId(),
                        "ONLY_SELLER",
                        "Seller 회사만 이 트랜잭션을 등록할 수 있습니다."
                );
                yield normalizeWalletAddress(
                        receivable.getSellerWalletAddress()
                );
            }
            case BUYER -> {
                requireCompany(
                        companyId,
                        receivable.getBuyerCompanyId(),
                        "ONLY_BUYER",
                        "Buyer 회사만 이 트랜잭션을 등록할 수 있습니다."
                );
                yield normalizeWalletAddress(
                        receivable.getBuyerWalletAddress()
                );
            }
            case FUNDER -> {
                if (Objects.equals(companyId, receivable.getSellerCompanyId())
                        || Objects.equals(
                        companyId,
                        receivable.getBuyerCompanyId()
                )) {
                    throw new ApiException(
                            HttpStatus.FORBIDDEN,
                            "RELATED_PARTY_CANNOT_FUND",
                            "Seller와 Buyer 회사는 해당 채권에 자금을 공급할 수 없습니다."
                    );
                }
                Wallet wallet = walletMapper.findByCompanyId(companyId)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.CONFLICT,
                                "FUNDER_WALLET_NOT_CONNECTED",
                                "자금 공급 전에 Funder 회사 지갑을 연결해 주세요."
                        ));
                yield normalizeWalletAddress(wallet.walletAddress());
            }
        };
    }

    private void requireCompany(Long actualCompanyId, Long expectedCompanyId, String code, String message) {
        if (!Objects.equals(actualCompanyId, expectedCompanyId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private Wallet findStoredWallet(String walletAddress, Long companyId) {
        Wallet wallet = walletMapper.findByWalletAddress(walletAddress)
                .filter(candidate -> Objects.equals(candidate.companyId(), companyId))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "RECEIVABLE_WALLET_NOT_MAPPED",
                        "채권에 저장된 지갑이 현재 회사에 등록되어 있지 않습니다."
                ));
        if (wallet.chainId() == null || wallet.chainId() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_WALLET_CHAIN",
                    "등록 지갑의 체인 정보를 확인해 주세요."
            );
        }
        return wallet;
    }

    private void requireReceivableState(
            ReceivableResponse receivable,
            BlockchainTransactionType transactionType
    ) {
        if (!transactionType.requiredReceivableStatus().equals(receivable.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_RECEIVABLE_STATUS",
                    "채권 상태가 올바르지 않습니다. 필요 상태: "
                            + transactionType.requiredReceivableStatus()
                            + ", 현재 상태: "
                            + receivable.getStatus()
            );
        }
        if (transactionType != BlockchainTransactionType.CREATE_RECEIVABLE
                && (receivable.getOnchainReceivableId() == null
                || receivable.getContractAddress() == null
                || receivable.getCreateTxHash() == null)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RECEIVABLE_NOT_ONCHAIN",
                    "Seller의 GIWA 온체인 채권 생성이 먼저 완료되어야 합니다."
            );
        }
        if (transactionType == BlockchainTransactionType.TOKENIZE_RECEIVABLE
                && receivable.getVerifyTxHash() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RECEIVABLE_NOT_VERIFIED_ONCHAIN",
                    "Buyer의 GIWA 온체인 검증이 먼저 완료되어야 합니다."
            );
        }
        if (transactionType == BlockchainTransactionType.FUND_RECEIVABLE
                && (receivable.getVerifyTxHash() == null
                || receivable.getTokenId() == null
                || receivable.getTokenizeTxHash() == null)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RECEIVABLE_NOT_TOKENIZED_ONCHAIN",
                    "Seller의 GIWA 채권 토큰화가 먼저 완료되어야 합니다."
            );
        }
        if (transactionType == BlockchainTransactionType.REPAY_RECEIVABLE
                && (receivable.getVerifyTxHash() == null
                || receivable.getTokenId() == null
                || receivable.getTokenizeTxHash() == null
                || receivable.getFunderCompanyId() == null
                || receivable.getFunderWalletAddress() == null
                || receivable.getMockTokenAddress() == null
                || receivable.getFundingTxHash() == null)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RECEIVABLE_NOT_FUNDED_ONCHAIN",
                    "Funder의 GIWA 채권 펀딩이 먼저 완료되어야 합니다."
            );
        }
    }

    private void requireContractMatches(
            ReceivableResponse receivable,
            BlockchainTransactionType transactionType,
            String contractAddress
    ) {
        if (transactionType == BlockchainTransactionType.CREATE_RECEIVABLE) {
            return;
        }
        if (!equalsHex(receivable.getContractAddress(), contractAddress)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONTRACT_ADDRESS_MISMATCH",
                    "채권에 저장된 컨트랙트 주소와 트랜잭션 컨트랙트 주소가 일치하지 않습니다."
            );
        }
    }

    private boolean sameSubmissionMetadata(
            BlockchainTransactionResponse transaction,
            Long receivableId,
            Long companyId,
            String walletAddress,
            BlockchainTransactionType transactionType,
            String contractAddress,
            String txHash
    ) {
        return Objects.equals(transaction.getReceivableId(), receivableId)
                && Objects.equals(transaction.getCompanyId(), companyId)
                && equalsHex(transaction.getWalletAddress(), walletAddress)
                && transactionType.name().equals(transaction.getTransactionType())
                && equalsHex(transaction.getContractAddress(), contractAddress)
                && transactionType.functionName().equals(transaction.getFunctionName())
                && equalsHex(transaction.getTxHash(), txHash);
    }

    private boolean sameRpcVerification(
            BlockchainTransactionResponse transaction,
            VerifiedBlockchainTransaction verified
    ) {
        return Objects.equals(transaction.getChainId(), verified.chainId())
                && Objects.equals(transaction.getBlockNumber(), verified.blockNumber())
                && equalsHex(transaction.getBlockHash(), verified.blockHash())
                && Objects.equals(transaction.getGasUsed(), verified.gasUsed())
                && transaction.getEffectiveGasPrice() != null
                && transaction.getEffectiveGasPrice().compareTo(
                        verified.effectiveGasPrice()
                ) == 0
                && Objects.equals(
                        transaction.getEventReceivableId(),
                        verified.eventReceivableId()
                )
                && Objects.equals(
                        transaction.getEventTokenId(),
                        verified.eventTokenId()
                )
                && transaction.getRpcVerifiedAt() != null;
    }

    private String normalizeTxHash(String value) {
        if (value == null || !TX_HASH_PATTERN.matcher(value).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TRANSACTION_HASH",
                    "트랜잭션 해시 형식을 확인해 주세요."
            );
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizeContractAddress(String value) {
        String address = normalizeWalletAddress(value);
        if (ZERO_ADDRESS.equals(address)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CONTRACT_ADDRESS",
                    "0 주소는 컨트랙트 주소로 사용할 수 없습니다."
            );
        }
        return address;
    }

    private String normalizeWalletAddress(String value) {
        if (value == null || !ADDRESS_PATTERN.matcher(value).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WALLET_ADDRESS",
                    "지갑 주소 형식을 확인해 주세요."
            );
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private Long parsePositiveLong(String value) {
        if (value == null || !POSITIVE_LONG_PATTERN.matcher(value).matches()) {
            throw invalidReceiptMetadata();
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw invalidReceiptMetadata();
        }
    }

    private BigDecimal parseUnsignedDecimal(String value) {
        if (value == null || !UNSIGNED_DECIMAL_PATTERN.matcher(value).matches()) {
            throw invalidReceiptMetadata();
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw invalidReceiptMetadata();
        }
    }

    private String normalizeFailureDetail(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TRANSACTION_FAILURE",
                    "트랜잭션 실패 정보 길이를 확인해 주세요."
            );
        }
        return normalized;
    }

    private boolean equalsHex(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private ApiException invalidTransactionType() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_TRANSACTION_TYPE",
                "지원하지 않는 블록체인 트랜잭션 종류입니다."
        );
    }

    private ApiException invalidReceiptMetadata() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_RECEIPT_METADATA",
                "블록체인 receipt 숫자 형식을 확인해 주세요."
        );
    }

    private ApiException transactionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_CONFLICT",
                "이미 저장된 블록체인 트랜잭션 정보와 요청 정보가 일치하지 않습니다."
        );
    }

    private ApiException invalidTransactionStatus(String expected, String actual) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVALID_BLOCKCHAIN_TRANSACTION_STATUS",
                "트랜잭션 상태가 올바르지 않습니다. 필요 상태: "
                        + expected
                        + ", 현재 상태: "
                        + actual
        );
    }

    private ApiException transactionNotConfirmed() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_NOT_CONFIRMED",
                "확인 완료된 블록체인 트랜잭션이 필요합니다."
        );
    }

    private ApiException verificationRetryRequired() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_VERIFICATION_RETRY_REQUIRED",
                "블록체인 검증 상태가 동시에 변경되었습니다. 잠시 후 다시 시도해 주세요."
        );
    }
}
