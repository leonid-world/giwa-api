package com.leonid.giwaapi.receivable;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceivableCreateRequest(
        @NotBlank @Size(max = 30) String buyerBusinessNumber,
        @NotNull @Positive @Digits(integer = 36, fraction = 0) BigDecimal faceValue,
        @NotNull @Positive @Digits(integer = 36, fraction = 0) BigDecimal fundingAmount,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate maturityDate,
        @Pattern(regexp = "^(0x)?[a-fA-F0-9]{64}$") String documentHash,
        @Size(max = 1000) String description
) {
}
