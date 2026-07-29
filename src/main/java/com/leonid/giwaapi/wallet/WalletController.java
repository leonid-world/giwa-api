package com.leonid.giwaapi.wallet;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/connect")
    public WalletResponse connect(@AuthenticationPrincipal String email, @Valid @RequestBody WalletConnectRequest request) {
        return walletService.connect(email, request);
    }

    @GetMapping("/me")
    public WalletResponse me(@AuthenticationPrincipal String email) {
        return walletService.me(email);
    }
}
