package com.leonid.giwaapi.transaction;

import com.leonid.giwaapi.common.error.ApiException;
import org.springframework.http.HttpStatus;

class BlockchainVerificationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean terminal;

    BlockchainVerificationException(
            HttpStatus status,
            String code,
            String message,
            boolean terminal
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.terminal = terminal;
    }

    String code() {
        return code;
    }

    boolean terminal() {
        return terminal;
    }

    ApiException toApiException() {
        return new ApiException(status, code, getMessage());
    }
}
