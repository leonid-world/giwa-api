package com.leonid.giwaapi.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class BlockchainTransactionFailureRecorder {

    private final BlockchainTransactionMapper transactionMapper;

    BlockchainTransactionFailureRecorder(
            BlockchainTransactionMapper transactionMapper
    ) {
        this.transactionMapper = transactionMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int record(
            BlockchainTransactionResponse transaction,
            BlockchainVerificationException exception
    ) {
        return transactionMapper.markVerificationFailed(
                transaction.getTxHash(),
                transaction.getCompanyId(),
                exception.code(),
                exception.getMessage(),
                transaction.getVerificationVersion()
        );
    }
}
