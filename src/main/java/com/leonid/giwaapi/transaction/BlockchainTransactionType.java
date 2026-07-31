package com.leonid.giwaapi.transaction;

enum BlockchainTransactionType {
    CREATE_RECEIVABLE("createReceivable", RequiredActor.SELLER, "CREATED"),
    VERIFY_RECEIVABLE("verifyReceivable", RequiredActor.BUYER, "CREATED"),
    TOKENIZE_RECEIVABLE("tokenizeReceivable", RequiredActor.SELLER, "VERIFIED");

    private final String functionName;
    private final RequiredActor requiredActor;
    private final String requiredReceivableStatus;

    BlockchainTransactionType(
            String functionName,
            RequiredActor requiredActor,
            String requiredReceivableStatus
    ) {
        this.functionName = functionName;
        this.requiredActor = requiredActor;
        this.requiredReceivableStatus = requiredReceivableStatus;
    }

    String functionName() {
        return functionName;
    }

    RequiredActor requiredActor() {
        return requiredActor;
    }

    String requiredReceivableStatus() {
        return requiredReceivableStatus;
    }

    enum RequiredActor {
        SELLER,
        BUYER
    }
}
