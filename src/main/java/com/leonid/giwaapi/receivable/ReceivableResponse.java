package com.leonid.giwaapi.receivable;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReceivableResponse {
    private Long receivableId;
    private Long sellerCompanyId;
    private String sellerCompanyName;
    private Long buyerCompanyId;
    private String buyerCompanyName;
    private Long funderCompanyId;
    private String funderCompanyName;
    private String sellerWalletAddress;
    private String buyerWalletAddress;
    private String funderWalletAddress;
    private String currencyCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal faceValue;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal fundingAmount;
    private LocalDate issueDate;
    private LocalDate maturityDate;
    private String status;
    private String documentHash;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long onchainReceivableId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long tokenId;
    private String contractAddress;
    private String mockTokenAddress;
    private String createTxHash;
    private String verifyTxHash;
    private String tokenizeTxHash;
    private String fundingTxHash;
    private String repayTxHash;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ReceivableResponse() {
    }

    public Long getReceivableId() { return receivableId; }
    public void setReceivableId(Long receivableId) { this.receivableId = receivableId; }
    public Long getSellerCompanyId() { return sellerCompanyId; }
    public void setSellerCompanyId(Long sellerCompanyId) { this.sellerCompanyId = sellerCompanyId; }
    public String getSellerCompanyName() { return sellerCompanyName; }
    public void setSellerCompanyName(String sellerCompanyName) { this.sellerCompanyName = sellerCompanyName; }
    public Long getBuyerCompanyId() { return buyerCompanyId; }
    public void setBuyerCompanyId(Long buyerCompanyId) { this.buyerCompanyId = buyerCompanyId; }
    public String getBuyerCompanyName() { return buyerCompanyName; }
    public void setBuyerCompanyName(String buyerCompanyName) { this.buyerCompanyName = buyerCompanyName; }
    public Long getFunderCompanyId() { return funderCompanyId; }
    public void setFunderCompanyId(Long funderCompanyId) { this.funderCompanyId = funderCompanyId; }
    public String getFunderCompanyName() { return funderCompanyName; }
    public void setFunderCompanyName(String funderCompanyName) { this.funderCompanyName = funderCompanyName; }
    public String getSellerWalletAddress() { return sellerWalletAddress; }
    public void setSellerWalletAddress(String sellerWalletAddress) { this.sellerWalletAddress = sellerWalletAddress; }
    public String getBuyerWalletAddress() { return buyerWalletAddress; }
    public void setBuyerWalletAddress(String buyerWalletAddress) { this.buyerWalletAddress = buyerWalletAddress; }
    public String getFunderWalletAddress() { return funderWalletAddress; }
    public void setFunderWalletAddress(String funderWalletAddress) { this.funderWalletAddress = funderWalletAddress; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getFaceValue() { return faceValue; }
    public void setFaceValue(BigDecimal faceValue) { this.faceValue = faceValue; }
    public BigDecimal getFundingAmount() { return fundingAmount; }
    public void setFundingAmount(BigDecimal fundingAmount) { this.fundingAmount = fundingAmount; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDocumentHash() { return documentHash; }
    public void setDocumentHash(String documentHash) { this.documentHash = documentHash; }
    public Long getOnchainReceivableId() { return onchainReceivableId; }
    public void setOnchainReceivableId(Long onchainReceivableId) { this.onchainReceivableId = onchainReceivableId; }
    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long tokenId) { this.tokenId = tokenId; }
    public String getContractAddress() { return contractAddress; }
    public void setContractAddress(String contractAddress) { this.contractAddress = contractAddress; }
    public String getMockTokenAddress() { return mockTokenAddress; }
    public void setMockTokenAddress(String mockTokenAddress) { this.mockTokenAddress = mockTokenAddress; }
    public String getCreateTxHash() { return createTxHash; }
    public void setCreateTxHash(String createTxHash) { this.createTxHash = createTxHash; }
    public String getVerifyTxHash() { return verifyTxHash; }
    public void setVerifyTxHash(String verifyTxHash) { this.verifyTxHash = verifyTxHash; }
    public String getTokenizeTxHash() { return tokenizeTxHash; }
    public void setTokenizeTxHash(String tokenizeTxHash) { this.tokenizeTxHash = tokenizeTxHash; }
    public String getFundingTxHash() { return fundingTxHash; }
    public void setFundingTxHash(String fundingTxHash) { this.fundingTxHash = fundingTxHash; }
    public String getRepayTxHash() { return repayTxHash; }
    public void setRepayTxHash(String repayTxHash) { this.repayTxHash = repayTxHash; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
