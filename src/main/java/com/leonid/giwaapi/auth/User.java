package com.leonid.giwaapi.auth;

import java.time.LocalDateTime;

public class User {
    private Long userId;
    private Long companyId;
    private String email;
    private String passwordHash;
    private String userName;
    private LocalDateTime createdAt;

    public User() {
    }

    public User(Long userId, Long companyId, String email, String passwordHash, String userName, LocalDateTime createdAt) {
        this.userId = userId;
        this.companyId = companyId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.userName = userName;
        this.createdAt = createdAt;
    }

    public Long userId() { return userId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long companyId() { return companyId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String email() { return email; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String passwordHash() { return passwordHash; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String userName() { return userName; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
