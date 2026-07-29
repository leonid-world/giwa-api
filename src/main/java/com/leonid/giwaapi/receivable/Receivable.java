package com.leonid.giwaapi.receivable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Receivable {
    private Long receivableId;
    private Long sellerCompanyId;
    private Long buyerCompanyId;
    private Long funderCompanyId;
    private String sellerWalletAddress;
    private String buyerWalletAddress;
    private String funderWalletAddress;
    private String currencyCode;
    private BigDecimal faceValue;
    private BigDecimal fundingAmount;
    private LocalDate issueDate;
    private LocalDate maturityDate;
    private String status;
    private String documentHash;
    private String description;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Receivable() {
    }

    public Long getReceivableId() { return receivableId; }
    public void setReceivableId(Long receivableId) { this.receivableId = receivableId; }
    public Long getSellerCompanyId() { return sellerCompanyId; }
    public void setSellerCompanyId(Long sellerCompanyId) { this.sellerCompanyId = sellerCompanyId; }
    public Long getBuyerCompanyId() { return buyerCompanyId; }
    public void setBuyerCompanyId(Long buyerCompanyId) { this.buyerCompanyId = buyerCompanyId; }
    public Long getFunderCompanyId() { return funderCompanyId; }
    public void setFunderCompanyId(Long funderCompanyId) { this.funderCompanyId = funderCompanyId; }
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
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
