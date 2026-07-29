package com.leonid.giwaapi.wallet;

public record WalletResponse(String walletAddress) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.walletAddress());
    }
}
