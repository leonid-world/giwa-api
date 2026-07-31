package com.leonid.giwaapi.receivable;

import com.leonid.giwaapi.auth.AuthService;
import com.leonid.giwaapi.auth.SignupRequest;
import com.leonid.giwaapi.common.error.ApiException;
import com.leonid.giwaapi.transaction.BlockchainTransactionConfirmedRequest;
import com.leonid.giwaapi.transaction.BlockchainTransactionCreateRequest;
import com.leonid.giwaapi.transaction.BlockchainTransactionService;
import com.leonid.giwaapi.transaction.BlockchainTransactionVerifier;
import com.leonid.giwaapi.transaction.VerifiedBlockchainTransaction;
import com.leonid.giwaapi.wallet.WalletConnectRequest;
import com.leonid.giwaapi.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReceivableOnchainServiceIntegrationTests {

    private static final String SELLER_EMAIL = "onchain-seller@example.com";
    private static final String BUYER_EMAIL = "onchain-buyer@example.com";
    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";
    private static final String CONTRACT = "0x3333333333333333333333333333333333333333";
    private static final String CREATE_TX = "0x" + "a".repeat(64);
    private static final String VERIFY_TX = "0x" + "b".repeat(64);
    private static final String TOKENIZE_TX = "0x" + "d".repeat(64);
    private static final String OTHER_TOKENIZE_TX = "0x" + "e".repeat(64);
    private static final String MISSING_TOKEN_TX = "0x" + "f".repeat(64);
    private static final Long TOKEN_ID = 9L;

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private BlockchainTransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private BlockchainTransactionVerifier transactionVerifier;

    @BeforeEach
    void stubRpcVerification() {
        when(transactionVerifier.verify(any(), any(), any()))
                .thenAnswer(invocation -> {
                    com.leonid.giwaapi.transaction.BlockchainTransactionResponse transaction =
                            invocation.getArgument(0);
                    Long expectedReceivableId = invocation.getArgument(2);
                    Long eventReceivableId = expectedReceivableId;
                    if (eventReceivableId == null) {
                        eventReceivableId = transaction.getTxHash().equals(
                                "0x" + "c".repeat(64)
                        ) ? 2L : 1L;
                    }
                    Long eventTokenId =
                            "TOKENIZE_RECEIVABLE".equals(
                                    transaction.getTransactionType()
                            ) && !MISSING_TOKEN_TX.equals(transaction.getTxHash())
                                    ? TOKEN_ID
                                    : null;
                    return new VerifiedBlockchainTransaction(
                            1337L,
                            12345L,
                            "0x" + "1".repeat(64),
                            21000L,
                            new BigDecimal("1000000000"),
                            eventReceivableId,
                            eventTokenId
                    );
                });
    }

    @Test
    void synchronizesChainCreationAndBuyerVerificationIdempotently() {
        ReceivableResponse created = createFixture();

        confirmJournal(SELLER_EMAIL, created.getReceivableId(), "CREATE_RECEIVABLE", CREATE_TX);
        ReceivableResponse chainCreated = receivableService.markChainCreated(
                SELLER_EMAIL,
                created.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );
        assertThat(chainCreated.getStatus()).isEqualTo("CREATED");
        assertThat(chainCreated.getOnchainReceivableId()).isEqualTo(1L);
        assertThat(chainCreated.getContractAddress()).isEqualTo(CONTRACT);
        assertThat(chainCreated.getCreateTxHash()).isEqualTo(CREATE_TX);

        confirmJournal(BUYER_EMAIL, created.getReceivableId(), "VERIFY_RECEIVABLE", VERIFY_TX);
        ReceivableResponse verified = receivableService.markVerified(
                BUYER_EMAIL,
                created.getReceivableId(),
                new ReceivableVerifiedRequest(VERIFY_TX)
        );
        assertThat(verified.getStatus()).isEqualTo("VERIFIED");
        assertThat(verified.getVerifyTxHash()).isEqualTo(VERIFY_TX);

        ReceivableResponse retriedVerification = receivableService.markVerified(
                BUYER_EMAIL,
                created.getReceivableId(),
                new ReceivableVerifiedRequest(VERIFY_TX)
        );
        ReceivableResponse retriedChainCreation = receivableService.markChainCreated(
                SELLER_EMAIL,
                created.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );
        assertThat(retriedVerification.getStatus()).isEqualTo("VERIFIED");
        assertThat(retriedChainCreation.getStatus()).isEqualTo("VERIFIED");

        Integer historyCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM receivable_status_history
                 WHERE receivable_id = ?
                   AND previous_status = 'CREATED'
                   AND current_status = 'VERIFIED'
                   AND tx_hash = ?
                """,
                Integer.class,
                created.getReceivableId(),
                VERIFY_TX
        );
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void enforcesSellerBuyerAndOnchainPreconditions() {
        ReceivableResponse created = createFixture();

        assertApiError(
                () -> receivableService.markChainCreated(
                        BUYER_EMAIL,
                        created.getReceivableId(),
                        chainCreatedRequest(CREATE_TX)
                ),
                HttpStatus.FORBIDDEN,
                "ONLY_SELLER"
        );
        assertApiError(
                () -> receivableService.markVerified(
                        SELLER_EMAIL,
                        created.getReceivableId(),
                        new ReceivableVerifiedRequest(VERIFY_TX)
                ),
                HttpStatus.FORBIDDEN,
                "ONLY_BUYER"
        );
        assertApiError(
                () -> receivableService.markVerified(
                        BUYER_EMAIL,
                        created.getReceivableId(),
                        new ReceivableVerifiedRequest(VERIFY_TX)
                ),
                HttpStatus.CONFLICT,
                "RECEIVABLE_NOT_ONCHAIN"
        );
    }

    @Test
    void rejectsConflictingMetadataAndInvalidState() {
        ReceivableResponse created = createFixture();
        confirmJournal(SELLER_EMAIL, created.getReceivableId(), "CREATE_RECEIVABLE", CREATE_TX);
        receivableService.markChainCreated(
                SELLER_EMAIL,
                created.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );

        assertApiError(
                () -> receivableService.markChainCreated(
                        SELLER_EMAIL,
                        created.getReceivableId(),
                        chainCreatedRequest("0x" + "c".repeat(64))
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_METADATA_CONFLICT"
        );

        jdbcTemplate.update(
                "UPDATE receivables SET status = 'CANCELLED' WHERE receivable_id = ?",
                created.getReceivableId()
        );
        assertApiError(
                () -> receivableService.markVerified(
                        BUYER_EMAIL,
                        created.getReceivableId(),
                        new ReceivableVerifiedRequest(VERIFY_TX)
                ),
                HttpStatus.CONFLICT,
                "INVALID_RECEIVABLE_STATUS"
        );
    }

    @Test
    void rejectsBlockchainMetadataReusedByAnotherReceivable() {
        ReceivableResponse first = createFixture();
        ReceivableResponse second = createAdditionalReceivable();
        confirmJournal(SELLER_EMAIL, first.getReceivableId(), "CREATE_RECEIVABLE", CREATE_TX);
        receivableService.markChainCreated(
                SELLER_EMAIL,
                first.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );

        assertApiError(
                () -> receivableService.markChainCreated(
                        SELLER_EMAIL,
                        second.getReceivableId(),
                        chainCreatedRequest(CREATE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_CONFLICT"
        );
    }

    @Test
    void rejectsVerificationTransactionReusedByAnotherReceivable() {
        ReceivableResponse first = createFixture();
        ReceivableResponse second = createAdditionalReceivable();
        confirmJournal(SELLER_EMAIL, first.getReceivableId(), "CREATE_RECEIVABLE", CREATE_TX);
        receivableService.markChainCreated(
                SELLER_EMAIL,
                first.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );
        String secondCreateTx = "0x" + "c".repeat(64);
        confirmJournal(SELLER_EMAIL, second.getReceivableId(), "CREATE_RECEIVABLE", secondCreateTx);
        receivableService.markChainCreated(
                SELLER_EMAIL,
                second.getReceivableId(),
                new ReceivableChainCreatedRequest(
                        "2",
                        secondCreateTx,
                        CONTRACT
                )
        );
        confirmJournal(BUYER_EMAIL, first.getReceivableId(), "VERIFY_RECEIVABLE", VERIFY_TX);
        receivableService.markVerified(
                BUYER_EMAIL,
                first.getReceivableId(),
                new ReceivableVerifiedRequest(VERIFY_TX)
        );

        assertApiError(
                () -> receivableService.markVerified(
                        BUYER_EMAIL,
                        second.getReceivableId(),
                        new ReceivableVerifiedRequest(VERIFY_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_CONFLICT"
        );
    }

    @Test
    void rejectsCreateTransactionReusedAsVerificationTransaction() {
        ReceivableResponse created = createFixture();
        confirmJournal(SELLER_EMAIL, created.getReceivableId(), "CREATE_RECEIVABLE", CREATE_TX);
        receivableService.markChainCreated(
                SELLER_EMAIL,
                created.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );

        assertApiError(
                () -> receivableService.markVerified(
                        BUYER_EMAIL,
                        created.getReceivableId(),
                        new ReceivableVerifiedRequest(CREATE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_CONFLICT"
        );
    }

    @Test
    void requiresMatchingConfirmedJournalBeforeSynchronization() {
        ReceivableResponse created = createFixture();

        assertApiError(
                () -> receivableService.markChainCreated(
                        SELLER_EMAIL,
                        created.getReceivableId(),
                        chainCreatedRequest(CREATE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_NOT_CONFIRMED"
        );

        transactionService.create(
                SELLER_EMAIL,
                journalRequest(created.getReceivableId(), "CREATE_RECEIVABLE", CREATE_TX)
        );
        assertApiError(
                () -> receivableService.markChainCreated(
                        SELLER_EMAIL,
                        created.getReceivableId(),
                        chainCreatedRequest(CREATE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_NOT_CONFIRMED"
        );

        transactionService.markConfirmed(
                SELLER_EMAIL,
                CREATE_TX,
                confirmedRequest()
        );
        ReceivableResponse synchronizedReceivable = receivableService.markChainCreated(
                SELLER_EMAIL,
                created.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );
        assertThat(synchronizedReceivable.getCreateTxHash()).isEqualTo(CREATE_TX);
    }

    @Test
    void rpcBackfillsLegacyConfirmedJournalBeforeSynchronization() {
        ReceivableResponse created = createFixture();
        transactionService.create(
                SELLER_EMAIL,
                journalRequest(
                        created.getReceivableId(),
                        "CREATE_RECEIVABLE",
                        CREATE_TX
                )
        );
        jdbcTemplate.update(
                """
                UPDATE blockchain_transactions
                   SET tx_status = 'CONFIRMED',
                       block_number = 1,
                       gas_used = 1,
                       effective_gas_price = 1,
                       confirmed_at = CURRENT_TIMESTAMP
                 WHERE tx_hash = ?
                """,
                CREATE_TX
        );

        ReceivableResponse synchronizedReceivable =
                receivableService.markChainCreated(
                        SELLER_EMAIL,
                        created.getReceivableId(),
                        chainCreatedRequest(CREATE_TX)
                );

        assertThat(synchronizedReceivable.getOnchainReceivableId())
                .isEqualTo(1L);
        Integer verifiedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM blockchain_transactions
                 WHERE tx_hash = ?
                   AND rpc_verified_at IS NOT NULL
                   AND event_receivable_id = 1
                   AND block_hash = ?
                """,
                Integer.class,
                CREATE_TX,
                "0x" + "1".repeat(64)
        );
        assertThat(verifiedCount).isEqualTo(1);
    }

    @Test
    void synchronizesSellerTokenizationFromRpcProofIdempotently() {
        ReceivableResponse verified = createVerifiedFixture();
        confirmJournal(
                SELLER_EMAIL,
                verified.getReceivableId(),
                "TOKENIZE_RECEIVABLE",
                TOKENIZE_TX
        );

        ReceivableResponse tokenized = receivableService.markTokenized(
                SELLER_EMAIL,
                verified.getReceivableId(),
                new ReceivableTokenizedRequest(TOKENIZE_TX)
        );
        ReceivableResponse retried = receivableService.markTokenized(
                SELLER_EMAIL,
                verified.getReceivableId(),
                new ReceivableTokenizedRequest(TOKENIZE_TX)
        );

        assertThat(tokenized.getStatus()).isEqualTo("TOKENIZED");
        assertThat(tokenized.getTokenId()).isEqualTo(TOKEN_ID);
        assertThat(tokenized.getTokenizeTxHash()).isEqualTo(TOKENIZE_TX);
        assertThat(retried.getStatus()).isEqualTo("TOKENIZED");
        assertThat(retried.getTokenId()).isEqualTo(TOKEN_ID);

        Integer historyCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM receivable_status_history
                 WHERE receivable_id = ?
                   AND previous_status = 'VERIFIED'
                   AND current_status = 'TOKENIZED'
                   AND changed_by_wallet_address = ?
                   AND tx_hash = ?
                """,
                Integer.class,
                verified.getReceivableId(),
                SELLER_WALLET,
                TOKENIZE_TX
        );
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void rejectsBuyerAndWrongStatusTokenizationSynchronization() {
        ReceivableResponse verified = createVerifiedFixture();

        assertApiError(
                () -> receivableService.markTokenized(
                        BUYER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(TOKENIZE_TX)
                ),
                HttpStatus.FORBIDDEN,
                "ONLY_SELLER"
        );

        jdbcTemplate.update(
                "UPDATE receivables SET status = 'CREATED' WHERE receivable_id = ?",
                verified.getReceivableId()
        );
        assertApiError(
                () -> receivableService.markTokenized(
                        SELLER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(TOKENIZE_TX)
                ),
                HttpStatus.CONFLICT,
                "INVALID_RECEIVABLE_STATUS"
        );
    }

    @Test
    void requiresConfirmedTokenizationJournalAndAuthoritativeTokenId() {
        ReceivableResponse verified = createVerifiedFixture();

        assertApiError(
                () -> receivableService.markTokenized(
                        SELLER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(TOKENIZE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_NOT_CONFIRMED"
        );

        transactionService.create(
                SELLER_EMAIL,
                journalRequest(
                        verified.getReceivableId(),
                        "TOKENIZE_RECEIVABLE",
                        TOKENIZE_TX
                )
        );
        assertApiError(
                () -> receivableService.markTokenized(
                        SELLER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(TOKENIZE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_NOT_CONFIRMED"
        );

        confirmJournal(
                SELLER_EMAIL,
                verified.getReceivableId(),
                "TOKENIZE_RECEIVABLE",
                MISSING_TOKEN_TX
        );
        assertApiError(
                () -> receivableService.markTokenized(
                        SELLER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(MISSING_TOKEN_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_SYNCHRONIZATION_EVENT_MISMATCH"
        );
    }

    @Test
    void rejectsConflictingTokenizationMetadataAndLifecycleHashReuse() {
        ReceivableResponse verified = createVerifiedFixture();
        confirmJournal(
                SELLER_EMAIL,
                verified.getReceivableId(),
                "TOKENIZE_RECEIVABLE",
                TOKENIZE_TX
        );
        receivableService.markTokenized(
                SELLER_EMAIL,
                verified.getReceivableId(),
                new ReceivableTokenizedRequest(TOKENIZE_TX)
        );

        assertApiError(
                () -> receivableService.markTokenized(
                        SELLER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(OTHER_TOKENIZE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_METADATA_CONFLICT"
        );
    }

    @Test
    void rejectsTokenizationHashAlreadyStoredInAnotherLifecycleColumn() {
        ReceivableResponse verified = createVerifiedFixture();
        confirmJournal(
                SELLER_EMAIL,
                verified.getReceivableId(),
                "TOKENIZE_RECEIVABLE",
                TOKENIZE_TX
        );
        jdbcTemplate.update(
                "UPDATE receivables SET funding_tx_hash = ? WHERE receivable_id = ?",
                TOKENIZE_TX,
                verified.getReceivableId()
        );

        assertApiError(
                () -> receivableService.markTokenized(
                        SELLER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(TOKENIZE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_METADATA_CONFLICT"
        );
    }

    @Test
    void rejectsTokenIdAlreadyMappedToAnotherReceivableInTheContract() {
        ReceivableResponse verified = createVerifiedFixture();
        ReceivableResponse other = createAdditionalReceivable();
        jdbcTemplate.update(
                """
                UPDATE receivables
                   SET contract_address = ?,
                       token_id = ?
                 WHERE receivable_id = ?
                """,
                CONTRACT,
                TOKEN_ID,
                other.getReceivableId()
        );
        confirmJournal(
                SELLER_EMAIL,
                verified.getReceivableId(),
                "TOKENIZE_RECEIVABLE",
                TOKENIZE_TX
        );

        assertApiError(
                () -> receivableService.markTokenized(
                        SELLER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(TOKENIZE_TX)
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_METADATA_CONFLICT"
        );
    }

    @Test
    void atomicTokenizationUpdateRejectsAConcurrentStatusChange() {
        ReceivableResponse verified = createVerifiedFixture();
        confirmJournal(
                SELLER_EMAIL,
                verified.getReceivableId(),
                "TOKENIZE_RECEIVABLE",
                TOKENIZE_TX
        );
        doAnswer(invocation -> {
                    com.leonid.giwaapi.transaction.BlockchainTransactionResponse transaction =
                            invocation.getArgument(0);
                    ReceivableResponse current = invocation.getArgument(1);
                    jdbcTemplate.update(
                            "UPDATE receivables SET status = 'CANCELLED' WHERE receivable_id = ?",
                            current.getReceivableId()
                    );
                    return new VerifiedBlockchainTransaction(
                            1337L,
                            12345L,
                            "0x" + "1".repeat(64),
                            21000L,
                            new BigDecimal("1000000000"),
                            current.getOnchainReceivableId(),
                            "TOKENIZE_RECEIVABLE".equals(
                                    transaction.getTransactionType()
                            ) ? TOKEN_ID : null
                    );
                })
                .when(transactionVerifier)
                .verify(any(), any(), any());

        assertApiError(
                () -> receivableService.markTokenized(
                        SELLER_EMAIL,
                        verified.getReceivableId(),
                        new ReceivableTokenizedRequest(TOKENIZE_TX)
                ),
                HttpStatus.CONFLICT,
                "RECEIVABLE_STATE_CONFLICT"
        );
    }

    private ReceivableResponse createFixture() {
        authService.signup(new SignupRequest(
                SELLER_EMAIL,
                "password123",
                "Seller User",
                "Onchain Seller",
                "4444444444"
        ));
        authService.signup(new SignupRequest(
                BUYER_EMAIL,
                "password123",
                "Buyer User",
                "Onchain Buyer",
                "5555555555"
        ));
        walletService.connect(SELLER_EMAIL, new WalletConnectRequest(SELLER_WALLET, 1337L));
        walletService.connect(BUYER_EMAIL, new WalletConnectRequest(BUYER_WALLET, 1337L));

        return receivableService.create(SELLER_EMAIL, new ReceivableCreateRequest(
                "5555555555",
                new BigDecimal("1000000"),
                new BigDecimal("900000"),
                LocalDate.of(2026, 7, 30),
                LocalDate.of(2026, 8, 30),
                null,
                "Onchain synchronization test"
        ));
    }

    private ReceivableChainCreatedRequest chainCreatedRequest(String txHash) {
        return new ReceivableChainCreatedRequest("1", txHash, CONTRACT);
    }

    private ReceivableResponse createVerifiedFixture() {
        ReceivableResponse created = createFixture();
        confirmJournal(
                SELLER_EMAIL,
                created.getReceivableId(),
                "CREATE_RECEIVABLE",
                CREATE_TX
        );
        ReceivableResponse chainCreated = receivableService.markChainCreated(
                SELLER_EMAIL,
                created.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );
        confirmJournal(
                BUYER_EMAIL,
                chainCreated.getReceivableId(),
                "VERIFY_RECEIVABLE",
                VERIFY_TX
        );
        return receivableService.markVerified(
                BUYER_EMAIL,
                chainCreated.getReceivableId(),
                new ReceivableVerifiedRequest(VERIFY_TX)
        );
    }

    private void confirmJournal(
            String email,
            Long receivableId,
            String transactionType,
            String txHash
    ) {
        transactionService.create(
                email,
                journalRequest(receivableId, transactionType, txHash)
        );
        transactionService.markConfirmed(email, txHash, confirmedRequest());
    }

    private BlockchainTransactionCreateRequest journalRequest(
            Long receivableId,
            String transactionType,
            String txHash
    ) {
        return new BlockchainTransactionCreateRequest(
                receivableId,
                transactionType,
                CONTRACT,
                txHash
        );
    }

    private BlockchainTransactionConfirmedRequest confirmedRequest() {
        return new BlockchainTransactionConfirmedRequest(
                "12345",
                "21000",
                "1000000000"
        );
    }

    private ReceivableResponse createAdditionalReceivable() {
        return receivableService.create(SELLER_EMAIL, new ReceivableCreateRequest(
                "5555555555",
                new BigDecimal("2000000"),
                new BigDecimal("1800000"),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
                null,
                "Second onchain synchronization test"
        ));
    }

    private void assertApiError(Runnable invocation, HttpStatus status, String code) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus()).isEqualTo(status);
                    assertThat(apiException.getCode()).isEqualTo(code);
                });
    }
}
