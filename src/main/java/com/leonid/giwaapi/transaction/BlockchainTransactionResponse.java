package com.leonid.giwaapi.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BlockchainTransactionResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long blockchainTransactionId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long receivableId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long companyId;
    private String walletAddress;
    private String transactionType;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long chainId;
    private String contractAddress;
    private String functionName;
    private String txHash;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long blockNumber;
    private String blockHash;
    private String txStatus;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long gasUsed;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal effectiveGasPrice;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long eventReceivableId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long eventTokenId;
    private LocalDateTime rpcVerifiedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long verificationVersion;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BlockchainTransactionResponse() {
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

    public Long getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(Long blockNumber) {
        this.blockNumber = blockNumber;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public String getTxStatus() {
        return txStatus;
    }

    public void setTxStatus(String txStatus) {
        this.txStatus = txStatus;
    }

    public Long getGasUsed() {
        return gasUsed;
    }

    public void setGasUsed(Long gasUsed) {
        this.gasUsed = gasUsed;
    }

    public BigDecimal getEffectiveGasPrice() {
        return effectiveGasPrice;
    }

    public void setEffectiveGasPrice(BigDecimal effectiveGasPrice) {
        this.effectiveGasPrice = effectiveGasPrice;
    }

    public Long getEventReceivableId() {
        return eventReceivableId;
    }

    public void setEventReceivableId(Long eventReceivableId) {
        this.eventReceivableId = eventReceivableId;
    }

    public Long getEventTokenId() {
        return eventTokenId;
    }

    public void setEventTokenId(Long eventTokenId) {
        this.eventTokenId = eventTokenId;
    }

    public LocalDateTime getRpcVerifiedAt() {
        return rpcVerifiedAt;
    }

    public void setRpcVerifiedAt(LocalDateTime rpcVerifiedAt) {
        this.rpcVerifiedAt = rpcVerifiedAt;
    }

    public Long getVerificationVersion() {
        return verificationVersion;
    }

    public void setVerificationVersion(Long verificationVersion) {
        this.verificationVersion = verificationVersion;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
