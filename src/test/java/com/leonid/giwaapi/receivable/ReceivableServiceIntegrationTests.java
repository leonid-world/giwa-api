package com.leonid.giwaapi.receivable;

import com.leonid.giwaapi.auth.AuthService;
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
                sellerEmail, "password123", "Seller User", "Seller Company", "111-11-11111"
        ));
        authService.signup(new SignupRequest(
                buyerEmail, "password123", "Buyer User", "Buyer Company", "222-22-22222"
        ));
        walletService.connect(sellerEmail, new WalletConnectRequest(
                "0x1111111111111111111111111111111111111111", 1337L
        ));
        walletService.connect(buyerEmail, new WalletConnectRequest(
                "0x2222222222222222222222222222222222222222", 1337L
        ));

        ReceivableResponse created = receivableService.create(sellerEmail, new ReceivableCreateRequest(
                "222-22-22222",
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
                outsiderEmail, "password123", "Outsider User", "Outsider Company", "333-33-33333"
        ));
        assertThat(receivableService.getAll(outsiderEmail)).isEmpty();
        assertThatThrownBy(() -> receivableService.getById(outsiderEmail, created.getReceivableId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
