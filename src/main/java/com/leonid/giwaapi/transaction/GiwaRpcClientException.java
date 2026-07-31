package com.leonid.giwaapi.transaction;

class GiwaRpcClientException extends RuntimeException {

    GiwaRpcClientException(String message) {
        super(message);
    }

    GiwaRpcClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
