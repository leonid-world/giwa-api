package com.leonid.giwaapi.company;

import com.leonid.giwaapi.auth.SignupRequest;
import com.leonid.giwaapi.receivable.ReceivableCreateRequest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BusinessNumberValidationTests {

    @Autowired
    private Validator validator;

    @Test
    void acceptsExactlyTenDigits() {
        SignupRequest request = new SignupRequest(
                "business-number@example.com",
                "password123",
                "Test User",
                "Test Company",
                "0123456789"
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsFormattedOrWrongLengthBusinessNumbers() {
        SignupRequest formatted = new SignupRequest(
                "formatted@example.com",
                "password123",
                "Test User",
                "Test Company",
                "123-45-67890"
        );
        ReceivableCreateRequest shortNumber = new ReceivableCreateRequest(
                "123456789",
                BigDecimal.TEN,
                BigDecimal.ONE,
                LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 8, 29),
                null,
                null
        );

        assertThat(validator.validate(formatted)).isNotEmpty();
        assertThat(validator.validate(shortNumber)).isNotEmpty();
    }
}
