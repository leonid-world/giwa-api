package com.leonid.giwaapi.transaction;

import java.math.BigDecimal;

public record VerifiedBlockchainTransaction(
        Long chainId,
        Long blockNumber,
        String blockHash,
        Long gasUsed,
        BigDecimal effectiveGasPrice,
        Long eventReceivableId,
        Long eventTokenId
) {
}
