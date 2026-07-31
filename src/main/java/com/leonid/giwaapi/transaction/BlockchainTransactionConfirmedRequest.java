package com.leonid.giwaapi.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BlockchainTransactionConfirmedRequest(
        @NotBlank
        @Pattern(
                regexp = "^[1-9][0-9]{0,18}$",
                message = "블록 번호는 양의 정수 문자열이어야 합니다."
        )
        String blockNumber,

        @NotBlank
        @Pattern(
                regexp = "^[1-9][0-9]{0,18}$",
                message = "사용된 가스는 양의 정수 문자열이어야 합니다."
        )
        String gasUsed,

        @NotBlank
        @Pattern(
                regexp = "^(0|[1-9][0-9]{0,64})$",
                message = "유효 가스 가격은 0 이상의 정수 문자열이어야 합니다."
        )
        String effectiveGasPrice
) {
}
