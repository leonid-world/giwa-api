package com.leonid.giwaapi.transaction;

import jakarta.validation.constraints.Size;

public record BlockchainTransactionFailedRequest(
        @Size(max = 100)
        String errorCode,

        @Size(max = 2000)
        String errorMessage
) {
}
