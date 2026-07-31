package com.leonid.giwaapi.transaction;

interface GiwaRpcClient {

    GiwaRpcProof getTransactionProof(String txHash);
}
