package com.leonid.giwaapi.receivable;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/receivables")
public class ReceivableController {

    private final ReceivableService receivableService;

    public ReceivableController(ReceivableService receivableService) {
        this.receivableService = receivableService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReceivableResponse create(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody ReceivableCreateRequest request
    ) {
        return receivableService.create(email, request);
    }

    @GetMapping
    public List<ReceivableResponse> getAll(@AuthenticationPrincipal String email) {
        return receivableService.getAll(email);
    }

    @GetMapping("/{receivableId}")
    public ReceivableResponse getById(
            @AuthenticationPrincipal String email,
            @PathVariable Long receivableId
    ) {
        return receivableService.getById(email, receivableId);
    }

    @PostMapping("/{receivableId}/chain-created")
    public ReceivableResponse markChainCreated(
            @AuthenticationPrincipal String email,
            @PathVariable Long receivableId,
            @Valid @RequestBody ReceivableChainCreatedRequest request
    ) {
        return receivableService.markChainCreated(email, receivableId, request);
    }

    @PostMapping("/{receivableId}/verified")
    public ReceivableResponse markVerified(
            @AuthenticationPrincipal String email,
            @PathVariable Long receivableId,
            @Valid @RequestBody ReceivableVerifiedRequest request
    ) {
        return receivableService.markVerified(email, receivableId, request);
    }

    @PostMapping("/{receivableId}/tokenized")
    public ReceivableResponse markTokenized(
            @AuthenticationPrincipal String email,
            @PathVariable Long receivableId,
            @Valid @RequestBody ReceivableTokenizedRequest request
    ) {
        return receivableService.markTokenized(email, receivableId, request);
    }
}
