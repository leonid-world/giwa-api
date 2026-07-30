package com.leonid.giwaapi.receivable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReceivableChainCreatedRequest(
        @NotBlank
        @Pattern(
                regexp = "^[1-9][0-9]{0,18}$",
                message = "온체인 채권 ID는 양의 정수여야 합니다."
        )
        String onchainReceivableId,

        @NotBlank
        @Pattern(
                regexp = "^0x[a-fA-F0-9]{64}$",
                message = "트랜잭션 해시 형식을 확인해 주세요."
        )
        String txHash,

        @NotBlank
        @Pattern(
                regexp = "^0x[a-fA-F0-9]{40}$",
                message = "컨트랙트 주소 형식을 확인해 주세요."
        )
        String contractAddress
) {
}
