package com.leonid.giwaapi.receivable;

import com.leonid.giwaapi.auth.AuthService;
import com.leonid.giwaapi.auth.AuthResponse;
import com.leonid.giwaapi.auth.SignupRequest;
import com.leonid.giwaapi.wallet.WalletConnectRequest;
import com.leonid.giwaapi.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReceivableServiceIntegrationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAndReadsReceivableForSellerAndBuyer() {
        String sellerEmail = "receivable-seller@example.com";
        String buyerEmail = "receivable-buyer@example.com";
        authService.signup(new SignupRequest(
                sellerEmail, "password123", "Seller User", "Seller Company", "1111111111"
        ));
        authService.signup(new SignupRequest(
                buyerEmail, "password123", "Buyer User", "Buyer Company", "2222222222"
        ));
        walletService.connect(sellerEmail, new WalletConnectRequest(
                "0x1111111111111111111111111111111111111111", 1337L
        ));
        walletService.connect(buyerEmail, new WalletConnectRequest(
                "0x2222222222222222222222222222222222222222", 1337L
        ));

        ReceivableResponse created = receivableService.create(sellerEmail, new ReceivableCreateRequest(
                "2222222222",
                new BigDecimal("1000000000000000000"),
                new BigDecimal("900000000000000000"),
                LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 8, 29),
                null,
                "MVP integration test"
        ));

        assertThat(created.getReceivableId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo("CREATED");
        assertThat(created.getSellerCompanyName()).isEqualTo("Seller Company");
        assertThat(created.getBuyerCompanyName()).isEqualTo("Buyer Company");
        assertThat(receivableService.getAll(sellerEmail)).hasSize(1);
        assertThat(receivableService.getAll(buyerEmail)).hasSize(1);
        assertThat(receivableService.getById(buyerEmail, created.getReceivableId()).getFaceValue())
                .isEqualByComparingTo("1000000000000000000");
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM receivable_status_history WHERE receivable_id = ? AND current_status = 'CREATED'",
                Integer.class,
                created.getReceivableId()
        );
        assertThat(historyCount).isEqualTo(1);

        String outsiderEmail = "receivable-outsider@example.com";
        authService.signup(new SignupRequest(
                outsiderEmail, "password123", "Outsider User", "Outsider Company", "3333333333"
        ));
        assertThat(receivableService.getAll(outsiderEmail)).isEmpty();
        assertThatThrownBy(() -> receivableService.getById(outsiderEmail, created.getReceivableId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void exposesTokenizedFundingOpportunitiesOnlyToUnrelatedCompanies() {
        String sellerEmail = "opportunity-seller@example.com";
        String buyerEmail = "opportunity-buyer@example.com";
        String funderEmail = "opportunity-funder@example.com";
        authService.signup(new SignupRequest(
                sellerEmail,
                "password123",
                "Seller User",
                "Opportunity Seller",
                "7111111111"
        ));
        authService.signup(new SignupRequest(
                buyerEmail,
                "password123",
                "Buyer User",
                "Opportunity Buyer",
                "7222222222"
        ));
        AuthResponse funderAuth = authService.signup(new SignupRequest(
                funderEmail,
                "password123",
                "Funder User",
                "Opportunity Funder",
                "7333333333"
        ));
        walletService.connect(sellerEmail, new WalletConnectRequest(
                "0x7111111111111111111111111111111111111111",
                1337L
        ));
        walletService.connect(buyerEmail, new WalletConnectRequest(
                "0x7222222222222222222222222222222222222222",
                1337L
        ));

        ReceivableResponse created = receivableService.create(
                sellerEmail,
                new ReceivableCreateRequest(
                        "7222222222",
                        new BigDecimal("1000000"),
                        new BigDecimal("900000"),
                        LocalDate.of(2026, 7, 30),
                        LocalDate.of(2026, 8, 30),
                        null,
                        "Funding opportunity test"
                )
        );

        assertThat(receivableService.getFundingOpportunities(funderEmail))
                .isEmpty();
        assertThatThrownBy(
                () -> receivableService.getById(
                        funderEmail,
                        created.getReceivableId()
                )
        ).isInstanceOf(ResponseStatusException.class);

        jdbcTemplate.update(
                """
                UPDATE receivables
                   SET status = 'TOKENIZED',
                       onchain_receivable_id = 1,
                       contract_address = ?,
                       create_tx_hash = ?,
                       verify_tx_hash = ?,
                       token_id = 9,
                       tokenize_tx_hash = ?
                 WHERE receivable_id = ?
                """,
                "0x7333333333333333333333333333333333333333",
                "0x" + "a".repeat(64),
                "0x" + "b".repeat(64),
                "0x" + "c".repeat(64),
                created.getReceivableId()
        );

        assertThat(receivableService.getFundingOpportunities(sellerEmail))
                .isEmpty();
        assertThat(receivableService.getFundingOpportunities(buyerEmail))
                .isEmpty();
        assertThat(receivableService.getFundingOpportunities(funderEmail))
                .extracting(ReceivableResponse::getReceivableId)
                .containsExactly(created.getReceivableId());
        assertThat(
                receivableService.getById(
                        funderEmail,
                        created.getReceivableId()
                ).getStatus()
        ).isEqualTo("TOKENIZED");

        jdbcTemplate.update(
                """
                UPDATE receivables
                   SET status = 'FUNDED',
                       funder_company_id = ?,
                       funder_wallet_address = ?,
                       mock_token_address = ?,
                       funding_tx_hash = ?
                 WHERE receivable_id = ?
                """,
                funderAuth.user().companyId(),
                "0x7333333333333333333333333333333333333333",
                "0x7444444444444444444444444444444444444444",
                "0x" + "d".repeat(64),
                created.getReceivableId()
        );

        assertThat(receivableService.getFundingOpportunities(funderEmail))
                .isEmpty();
        ReceivableResponse funded = receivableService.getById(
                funderEmail,
                created.getReceivableId()
        );
        assertThat(funded.getFunderCompanyName())
                .isEqualTo("Opportunity Funder");
    }
}
