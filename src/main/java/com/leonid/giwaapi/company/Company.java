package com.leonid.giwaapi.company;

public class Company {
    private Long companyId;
    private String companyName;
    private String businessNumber;

    public Company() {
    }

    public Company(Long companyId, String companyName, String businessNumber) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.businessNumber = businessNumber;
    }

    public Long companyId() { return companyId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String companyName() { return companyName; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String businessNumber() { return businessNumber; }
    public String getBusinessNumber() { return businessNumber; }
    public void setBusinessNumber(String businessNumber) { this.businessNumber = businessNumber; }
}
