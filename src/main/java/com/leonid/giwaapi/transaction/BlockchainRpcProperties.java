package com.leonid.giwaapi.transaction;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.blockchain")
public class BlockchainRpcProperties {

    private String rpcUrl = "";
    private Long chainId = 0L;
    private String receivableFinanceAddress = "";
    private String mockKrwAddress = "";
    private Long rpcTimeoutMs = 10000L;
    private Integer minConfirmations = 1;

    public String getRpcUrl() {
        return rpcUrl;
    }

    public void setRpcUrl(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }

    public Long getChainId() {
        return chainId;
    }

    public void setChainId(Long chainId) {
        this.chainId = chainId;
    }

    public String getReceivableFinanceAddress() {
        return receivableFinanceAddress;
    }

    public void setReceivableFinanceAddress(String receivableFinanceAddress) {
        this.receivableFinanceAddress = receivableFinanceAddress;
    }

    public String getMockKrwAddress() {
        return mockKrwAddress;
    }

    public void setMockKrwAddress(String mockKrwAddress) {
        this.mockKrwAddress = mockKrwAddress;
    }

    public Long getRpcTimeoutMs() {
        return rpcTimeoutMs;
    }

    public void setRpcTimeoutMs(Long rpcTimeoutMs) {
        this.rpcTimeoutMs = rpcTimeoutMs;
    }

    public Integer getMinConfirmations() {
        return minConfirmations;
    }

    public void setMinConfirmations(Integer minConfirmations) {
        this.minConfirmations = minConfirmations;
    }
}
