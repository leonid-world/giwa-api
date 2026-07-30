package com.leonid.giwaapi.receivable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReceivableVerifiedRequest(
        @NotBlank
        @Pattern(
                regexp = "^0x[a-fA-F0-9]{64}$",
                message = "트랜잭션 해시 형식을 확인해 주세요."
        )
        String txHash
) {
}
