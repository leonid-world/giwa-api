package com.leonid.giwaapi.wallet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record WalletConnectRequest(
        @NotBlank @Pattern(regexp = "^0x[a-fA-F0-9]{40}$") String walletAddress,
        @NotNull @Positive Long chainId
) {
}
