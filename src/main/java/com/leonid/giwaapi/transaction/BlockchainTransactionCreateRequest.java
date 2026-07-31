package com.leonid.giwaapi.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record BlockchainTransactionCreateRequest(
        @NotNull
        @Positive
        Long receivableId,

        @NotBlank
        String transactionType,

        @NotBlank
        @Pattern(
                regexp = "^0x[a-fA-F0-9]{40}$",
                message = "컨트랙트 주소 형식을 확인해 주세요."
        )
        String contractAddress,

        @NotBlank
        @Pattern(
                regexp = "^0x[a-fA-F0-9]{64}$",
                message = "트랜잭션 해시 형식을 확인해 주세요."
        )
        String txHash
) {
}
