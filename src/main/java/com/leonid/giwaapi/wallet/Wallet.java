package com.leonid.giwaapi.wallet;

public class Wallet {
    private Long companyWalletId;
    private Long companyId;
    private String walletAddress;
    private Long chainId;

    public Wallet() {
    }

    public Wallet(Long companyWalletId, Long companyId, String walletAddress, Long chainId) {
        this.companyWalletId = companyWalletId;
        this.companyId = companyId;
        this.walletAddress = walletAddress;
        this.chainId = chainId;
    }

    public Long companyWalletId() { return companyWalletId; }
    public Long getCompanyWalletId() { return companyWalletId; }
    public void setCompanyWalletId(Long companyWalletId) { this.companyWalletId = companyWalletId; }
    public Long companyId() { return companyId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String walletAddress() { return walletAddress; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public Long chainId() { return chainId; }
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
}
