package com.leonid.giwaapi.receivable;

import com.leonid.giwaapi.auth.AuthService;
import com.leonid.giwaapi.auth.SignupRequest;
import com.leonid.giwaapi.common.error.ApiException;
import com.leonid.giwaapi.wallet.WalletConnectRequest;
import com.leonid.giwaapi.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void synchronizesChainCreationAndBuyerVerificationIdempotently() {
        ReceivableResponse created = createFixture();

        ReceivableResponse chainCreated = receivableService.markChainCreated(
                SELLER_EMAIL,
                created.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );
        assertThat(chainCreated.getStatus()).isEqualTo("CREATED");
        assertThat(chainCreated.getOnchainReceivableId()).isEqualTo(1L);
        assertThat(chainCreated.getContractAddress()).isEqualTo(CONTRACT);
        assertThat(chainCreated.getCreateTxHash()).isEqualTo(CREATE_TX);

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
                "BLOCKCHAIN_METADATA_CONFLICT"
        );
    }

    @Test
    void rejectsVerificationTransactionReusedByAnotherReceivable() {
        ReceivableResponse first = createFixture();
        ReceivableResponse second = createAdditionalReceivable();
        receivableService.markChainCreated(
                SELLER_EMAIL,
                first.getReceivableId(),
                chainCreatedRequest(CREATE_TX)
        );
        receivableService.markChainCreated(
                SELLER_EMAIL,
                second.getReceivableId(),
                new ReceivableChainCreatedRequest(
                        "2",
                        "0x" + "c".repeat(64),
                        CONTRACT
                )
        );
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
                "BLOCKCHAIN_METADATA_CONFLICT"
        );
    }

    @Test
    void rejectsCreateTransactionReusedAsVerificationTransaction() {
        ReceivableResponse created = createFixture();
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
                "BLOCKCHAIN_METADATA_CONFLICT"
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
