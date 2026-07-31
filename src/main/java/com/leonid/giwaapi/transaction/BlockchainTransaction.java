package com.leonid.giwaapi.transaction;

public class BlockchainTransaction {
    private Long blockchainTransactionId;
    private Long receivableId;
    private Long companyId;
    private String walletAddress;
    private String transactionType;
    private Long chainId;
    private String contractAddress;
    private String functionName;
    private String txHash;
    private String txStatus;

    public BlockchainTransaction() {
    }

    public Long getBlockchainTransactionId() {
        return blockchainTransactionId;
    }

    public void setBlockchainTransactionId(Long blockchainTransactionId) {
        this.blockchainTransactionId = blockchainTransactionId;
    }

    public Long getReceivableId() {
        return receivableId;
    }

    public void setReceivableId(Long receivableId) {
        this.receivableId = receivableId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public Long getChainId() {
        return chainId;
    }

    public void setChainId(Long chainId) {
        this.chainId = chainId;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public String getTxStatus() {
        return txStatus;
    }

    public void setTxStatus(String txStatus) {
        this.txStatus = txStatus;
    }
}
