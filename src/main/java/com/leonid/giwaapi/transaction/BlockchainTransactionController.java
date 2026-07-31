package com.leonid.giwaapi.transaction;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BlockchainTransactionController {

    private final BlockchainTransactionService transactionService;

    public BlockchainTransactionController(BlockchainTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/blockchain-transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public BlockchainTransactionResponse create(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody BlockchainTransactionCreateRequest request
    ) {
        return transactionService.create(email, request);
    }

    @PatchMapping("/blockchain-transactions/{txHash}/confirmed")
    public BlockchainTransactionResponse markConfirmed(
            @AuthenticationPrincipal String email,
            @PathVariable String txHash,
            @Valid @RequestBody BlockchainTransactionConfirmedRequest request
    ) {
        return transactionService.markConfirmed(email, txHash, request);
    }

    @PatchMapping("/blockchain-transactions/{txHash}/failed")
    public BlockchainTransactionResponse markFailed(
            @AuthenticationPrincipal String email,
            @PathVariable String txHash,
            @Valid @RequestBody BlockchainTransactionFailedRequest request
    ) {
        return transactionService.markFailed(email, txHash, request);
    }

    @GetMapping("/receivables/{receivableId}/transactions")
    public List<BlockchainTransactionResponse> getAllByReceivable(
            @AuthenticationPrincipal String email,
            @PathVariable Long receivableId
    ) {
        return transactionService.getAllByReceivable(email, receivableId);
    }
}
