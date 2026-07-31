package com.leonid.giwaapi.transaction;

import java.util.List;

record GiwaRpcProof(
        String chainId,
        String latestBlockNumber,
        Transaction transaction,
        Receipt receipt,
        Block canonicalBlock
) {
    record Transaction(
            String hash,
            String from,
            String to,
            String input,
            String value,
            String chainId,
            String blockNumber,
            String blockHash,
            String gasPrice
    ) {
    }

    record Receipt(
            String transactionHash,
            String from,
            String to,
            String blockNumber,
            String blockHash,
            String status,
            String gasUsed,
            String effectiveGasPrice,
            List<Log> logs
    ) {
    }

    record Log(
            String address,
            List<String> topics,
            String data,
            boolean removed
    ) {
    }

    record Block(
            String number,
            String hash
    ) {
    }
}
