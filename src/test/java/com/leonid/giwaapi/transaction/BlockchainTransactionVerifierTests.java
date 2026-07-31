package com.leonid.giwaapi.transaction;

import com.leonid.giwaapi.receivable.ReceivableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockchainTransactionVerifierTests {

    private static final long CHAIN_ID = 91342L;
    private static final String CHAIN_ID_HEX = "0x164ce";
    private static final String CONTRACT =
            "0x3333333333333333333333333333333333333333";
    private static final String SELLER =
            "0x1111111111111111111111111111111111111111";
    private static final String BUYER =
            "0x2222222222222222222222222222222222222222";
    private static final String TX_HASH = "0x" + "a".repeat(64);
    private static final String BLOCK_HASH = "0x" + "b".repeat(64);
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

    private final StubRpcClient rpcClient = new StubRpcClient();
    private BlockchainRpcProperties properties;
    private BlockchainTransactionVerifier verifier;
    private ReceivableResponse receivable;

    @BeforeEach
    void setUp() {
        properties = new BlockchainRpcProperties();
        properties.setRpcUrl("http://127.0.0.1:8545");
        properties.setChainId(CHAIN_ID);
        properties.setReceivableFinanceAddress(CONTRACT);
        properties.setMinConfirmations(2);
        verifier = new BlockchainTransactionVerifier(rpcClient, properties);
        receivable = receivable();
    }

    @Test
    void verifiesCreateCalldataReceiptCanonicalBlockAndFullEvent() {
        BlockchainTransactionResponse transaction =
                journal("CREATE_RECEIVABLE", "createReceivable", SELLER);
        rpcClient.proof = createProof();

        VerifiedBlockchainTransaction verified = verifier.verify(
                transaction,
                receivable,
                7L
        );

        assertThat(verified.chainId()).isEqualTo(CHAIN_ID);
        assertThat(verified.blockNumber()).isEqualTo(100L);
        assertThat(verified.blockHash()).isEqualTo(BLOCK_HASH);
        assertThat(verified.gasUsed()).isEqualTo(21000L);
        assertThat(verified.effectiveGasPrice())
                .isEqualByComparingTo("1000000000");
        assertThat(verified.eventReceivableId()).isEqualTo(7L);
        assertThat(verified.eventTokenId()).isNull();
    }

    @Test
    void verifiesBuyerVerificationEventAndOnchainId() {
        receivable.setOnchainReceivableId(7L);
        BlockchainTransactionResponse transaction =
                journal("VERIFY_RECEIVABLE", "verifyReceivable", BUYER);
        rpcClient.proof = verifyProof();

        VerifiedBlockchainTransaction verified = verifier.verify(
                transaction,
                receivable,
                7L
        );

        assertThat(verified.eventReceivableId()).isEqualTo(7L);
        assertThat(verified.eventTokenId()).isNull();
    }

    @Test
    void verifiesTokenizationEventAndEscrowMintTransfer() {
        receivable.setOnchainReceivableId(7L);
        BlockchainTransactionResponse transaction =
                journal("TOKENIZE_RECEIVABLE", "tokenizeReceivable", SELLER);
        rpcClient.proof = tokenizeProof();

        VerifiedBlockchainTransaction verified = verifier.verify(
                transaction,
                receivable,
                7L
        );

        assertThat(verified.eventReceivableId()).isEqualTo(7L);
        assertThat(verified.eventTokenId()).isEqualTo(9L);
    }

    @Test
    void bindsClaimedCreateIdToTheVerifiedEvent() {
        rpcClient.proof = createProof();

        assertCode(
                () -> verifier.verify(
                        journal(
                                "CREATE_RECEIVABLE",
                                "createReceivable",
                                SELLER
                        ),
                        receivable,
                        8L
                ),
                "BLOCKCHAIN_EVENT_MISMATCH",
                true
        );
    }

    @Test
    void keepsMissingReceiptAndInsufficientConfirmationsRetryable() {
        GiwaRpcProof proof = createProof();
        rpcClient.proof = new GiwaRpcProof(
                proof.chainId(),
                proof.latestBlockNumber(),
                proof.transaction(),
                null,
                null
        );
        assertCode(
                () -> verifier.verify(
                        journal(
                                "CREATE_RECEIVABLE",
                                "createReceivable",
                                SELLER
                        ),
                        receivable,
                        null
                ),
                "BLOCKCHAIN_TRANSACTION_PENDING",
                false
        );

        rpcClient.proof = new GiwaRpcProof(
                proof.chainId(),
                "0x64",
                proof.transaction(),
                proof.receipt(),
                proof.canonicalBlock()
        );
        assertCode(
                () -> verifier.verify(
                        journal(
                                "CREATE_RECEIVABLE",
                                "createReceivable",
                                SELLER
                        ),
                        receivable,
                        null
                ),
                "BLOCKCHAIN_CONFIRMATIONS_PENDING",
                false
        );
    }

    @Test
    void rejectsWrongChainAndNonCanonicalReceiptWithoutTerminalFailure() {
        GiwaRpcProof proof = createProof();
        rpcClient.proof = new GiwaRpcProof(
                "0x1",
                proof.latestBlockNumber(),
                proof.transaction(),
                proof.receipt(),
                proof.canonicalBlock()
        );
        assertCode(
                () -> verifier.verify(
                        journal(
                                "CREATE_RECEIVABLE",
                                "createReceivable",
                                SELLER
                        ),
                        receivable,
                        null
                ),
                "BLOCKCHAIN_RPC_CONFIGURATION_MISMATCH",
                false
        );

        rpcClient.proof = new GiwaRpcProof(
                proof.chainId(),
                proof.latestBlockNumber(),
                proof.transaction(),
                proof.receipt(),
                new GiwaRpcProof.Block("0x64", "0x" + "c".repeat(64))
        );
        assertCode(
                () -> verifier.verify(
                        journal(
                                "CREATE_RECEIVABLE",
                                "createReceivable",
                                SELLER
                        ),
                        receivable,
                        null
                ),
                "BLOCKCHAIN_REORG_DETECTED",
                false
        );

        GiwaRpcProof.Transaction differentBlockTransaction =
                new GiwaRpcProof.Transaction(
                        proof.transaction().hash(),
                        proof.transaction().from(),
                        proof.transaction().to(),
                        proof.transaction().input(),
                        proof.transaction().value(),
                        proof.transaction().chainId(),
                        "0x65",
                        "0x" + "d".repeat(64),
                        proof.transaction().gasPrice()
                );
        rpcClient.proof = withTransaction(proof, differentBlockTransaction);
        assertCode(
                () -> verifyCreate(),
                "BLOCKCHAIN_REORG_DETECTED",
                false
        );
    }

    @Test
    void rejectsRevertedWrongSignerCalldataAndDuplicateEventAsTerminal() {
        GiwaRpcProof proof = createProof();
        GiwaRpcProof.Receipt revertedReceipt = new GiwaRpcProof.Receipt(
                proof.receipt().transactionHash(),
                proof.receipt().from(),
                proof.receipt().to(),
                proof.receipt().blockNumber(),
                proof.receipt().blockHash(),
                "0x0",
                proof.receipt().gasUsed(),
                proof.receipt().effectiveGasPrice(),
                proof.receipt().logs()
        );
        rpcClient.proof = withReceipt(proof, revertedReceipt);
        assertCode(
                () -> verifyCreate(),
                "BLOCKCHAIN_TRANSACTION_REVERTED",
                true
        );

        GiwaRpcProof.Transaction wrongSigner = new GiwaRpcProof.Transaction(
                proof.transaction().hash(),
                BUYER,
                proof.transaction().to(),
                proof.transaction().input(),
                proof.transaction().value(),
                proof.transaction().chainId(),
                proof.transaction().blockNumber(),
                proof.transaction().blockHash(),
                proof.transaction().gasPrice()
        );
        GiwaRpcProof.Receipt wrongSignerReceipt = new GiwaRpcProof.Receipt(
                proof.receipt().transactionHash(),
                BUYER,
                proof.receipt().to(),
                proof.receipt().blockNumber(),
                proof.receipt().blockHash(),
                proof.receipt().status(),
                proof.receipt().gasUsed(),
                proof.receipt().effectiveGasPrice(),
                proof.receipt().logs()
        );
        rpcClient.proof = new GiwaRpcProof(
                proof.chainId(),
                proof.latestBlockNumber(),
                wrongSigner,
                wrongSignerReceipt,
                proof.canonicalBlock()
        );
        assertCode(
                () -> verifyCreate(),
                "BLOCKCHAIN_TRANSACTION_VERIFICATION_FAILED",
                true
        );

        GiwaRpcProof.Transaction wrongCalldata = new GiwaRpcProof.Transaction(
                proof.transaction().hash(),
                proof.transaction().from(),
                proof.transaction().to(),
                VERIFY_SELECTOR + word(7),
                proof.transaction().value(),
                proof.transaction().chainId(),
                proof.transaction().blockNumber(),
                proof.transaction().blockHash(),
                proof.transaction().gasPrice()
        );
        rpcClient.proof = withTransaction(proof, wrongCalldata);
        assertCode(
                () -> verifyCreate(),
                "BLOCKCHAIN_TRANSACTION_VERIFICATION_FAILED",
                true
        );

        List<GiwaRpcProof.Log> duplicateLogs =
                new ArrayList<>(proof.receipt().logs());
        duplicateLogs.add(proof.receipt().logs().get(0));
        GiwaRpcProof.Receipt duplicateReceipt = new GiwaRpcProof.Receipt(
                proof.receipt().transactionHash(),
                proof.receipt().from(),
                proof.receipt().to(),
                proof.receipt().blockNumber(),
                proof.receipt().blockHash(),
                proof.receipt().status(),
                proof.receipt().gasUsed(),
                proof.receipt().effectiveGasPrice(),
                List.copyOf(duplicateLogs)
        );
        rpcClient.proof = withReceipt(proof, duplicateReceipt);
        assertCode(
                () -> verifyCreate(),
                "BLOCKCHAIN_EVENT_MISMATCH",
                true
        );
    }

    @Test
    void convertsMissingConfigurationAndRpcFailureToSafeRetryableErrors() {
        properties.setRpcUrl("");
        assertCode(
                () -> verifyCreate(),
                "BLOCKCHAIN_RPC_NOT_CONFIGURED",
                false
        );

        properties.setRpcUrl("http://127.0.0.1:8545");
        rpcClient.failure = new GiwaRpcClientException("secret raw RPC error");
        assertCode(
                () -> verifyCreate(),
                "BLOCKCHAIN_RPC_UNAVAILABLE",
                false
        );
    }

    private VerifiedBlockchainTransaction verifyCreate() {
        return verifier.verify(
                journal(
                        "CREATE_RECEIVABLE",
                        "createReceivable",
                        SELLER
                ),
                receivable,
                null
        );
    }

    private GiwaRpcProof createProof() {
        String input = CREATE_SELECTOR
                + addressWord(BUYER)
                + word(1_000_000)
                + word(900_000)
                + word(epoch(receivable.getIssueDate()))
                + word(epoch(receivable.getMaturityDate()))
                + "0".repeat(64);
        GiwaRpcProof.Log event = new GiwaRpcProof.Log(
                CONTRACT,
                List.of(
                        CREATED_TOPIC,
                        topic(7),
                        addressTopic(SELLER),
                        addressTopic(BUYER)
                ),
                "0x"
                        + word(1_000_000)
                        + word(900_000)
                        + word(epoch(receivable.getIssueDate()))
                        + word(epoch(receivable.getMaturityDate()))
                        + "0".repeat(64),
                false
        );
        return proof(SELLER, input, List.of(event));
    }

    private GiwaRpcProof verifyProof() {
        GiwaRpcProof.Log event = new GiwaRpcProof.Log(
                CONTRACT,
                List.of(
                        VERIFIED_TOPIC,
                        topic(7),
                        addressTopic(BUYER)
                ),
                "0x",
                false
        );
        return proof(BUYER, VERIFY_SELECTOR + word(7), List.of(event));
    }

    private GiwaRpcProof tokenizeProof() {
        GiwaRpcProof.Log tokenized = new GiwaRpcProof.Log(
                CONTRACT,
                List.of(
                        TOKENIZED_TOPIC,
                        topic(7),
                        topic(9),
                        addressTopic(CONTRACT)
                ),
                "0x",
                false
        );
        GiwaRpcProof.Log transfer = new GiwaRpcProof.Log(
                CONTRACT,
                List.of(
                        TRANSFER_TOPIC,
                        addressTopic("0x" + "0".repeat(40)),
                        addressTopic(CONTRACT),
                        topic(9)
                ),
                "0x",
                false
        );
        return proof(
                SELLER,
                TOKENIZE_SELECTOR + word(7),
                List.of(transfer, tokenized)
        );
    }

    private GiwaRpcProof proof(
            String from,
            String input,
            List<GiwaRpcProof.Log> logs
    ) {
        GiwaRpcProof.Transaction transaction = new GiwaRpcProof.Transaction(
                TX_HASH,
                from,
                CONTRACT,
                input,
                "0x0",
                CHAIN_ID_HEX,
                "0x64",
                BLOCK_HASH,
                "0x3b9aca00"
        );
        GiwaRpcProof.Receipt receipt = new GiwaRpcProof.Receipt(
                TX_HASH,
                from,
                CONTRACT,
                "0x64",
                BLOCK_HASH,
                "0x1",
                "0x5208",
                "0x3b9aca00",
                logs
        );
        return new GiwaRpcProof(
                CHAIN_ID_HEX,
                "0x65",
                transaction,
                receipt,
                new GiwaRpcProof.Block("0x64", BLOCK_HASH)
        );
    }

    private GiwaRpcProof withReceipt(
            GiwaRpcProof proof,
            GiwaRpcProof.Receipt receipt
    ) {
        return new GiwaRpcProof(
                proof.chainId(),
                proof.latestBlockNumber(),
                proof.transaction(),
                receipt,
                proof.canonicalBlock()
        );
    }

    private GiwaRpcProof withTransaction(
            GiwaRpcProof proof,
            GiwaRpcProof.Transaction transaction
    ) {
        return new GiwaRpcProof(
                proof.chainId(),
                proof.latestBlockNumber(),
                transaction,
                proof.receipt(),
                proof.canonicalBlock()
        );
    }

    private BlockchainTransactionResponse journal(
            String type,
            String functionName,
            String wallet
    ) {
        BlockchainTransactionResponse transaction =
                new BlockchainTransactionResponse();
        transaction.setReceivableId(1L);
        transaction.setCompanyId(1L);
        transaction.setWalletAddress(wallet);
        transaction.setTransactionType(type);
        transaction.setChainId(1337L);
        transaction.setContractAddress(CONTRACT);
        transaction.setFunctionName(functionName);
        transaction.setTxHash(TX_HASH);
        transaction.setTxStatus("PENDING");
        return transaction;
    }

    private ReceivableResponse receivable() {
        ReceivableResponse value = new ReceivableResponse();
        value.setReceivableId(1L);
        value.setSellerWalletAddress(SELLER);
        value.setBuyerWalletAddress(BUYER);
        value.setFaceValue(new BigDecimal("1000000"));
        value.setFundingAmount(new BigDecimal("900000"));
        value.setIssueDate(LocalDate.of(2026, 7, 30));
        value.setMaturityDate(LocalDate.of(2026, 8, 30));
        value.setDocumentHash(null);
        value.setContractAddress(CONTRACT);
        return value;
    }

    private void assertCode(
            Runnable invocation,
            String code,
            boolean terminal
    ) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(BlockchainVerificationException.class)
                .satisfies(error -> {
                    BlockchainVerificationException exception =
                            (BlockchainVerificationException) error;
                    assertThat(exception.code()).isEqualTo(code);
                    assertThat(exception.terminal()).isEqualTo(terminal);
                    assertThat(exception.getMessage())
                            .doesNotContain("secret raw RPC error");
                });
    }

    private static String addressWord(String address) {
        return "0".repeat(24) + address.substring(2).toLowerCase();
    }

    private static String addressTopic(String address) {
        return "0x" + addressWord(address);
    }

    private static String topic(long value) {
        return "0x" + word(value);
    }

    private static String word(long value) {
        return word(BigInteger.valueOf(value));
    }

    private static String word(BigInteger value) {
        return String.format("%064x", value);
    }

    private static long epoch(LocalDate value) {
        return value.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    private static class StubRpcClient implements GiwaRpcClient {
        private GiwaRpcProof proof;
        private GiwaRpcClientException failure;

        @Override
        public GiwaRpcProof getTransactionProof(String txHash) {
            if (failure != null) throw failure;
            return proof;
        }
    }
}
