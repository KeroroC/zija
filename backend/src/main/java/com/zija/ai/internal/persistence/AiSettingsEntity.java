package com.zija.ai.internal.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;

@TableName("ai_provider_setting")
public class AiSettingsEntity {

    @TableId(value = "singleton_key", type = IdType.INPUT)
    private Short singletonKey;
    private Boolean enabled;
    private String providerId;
    private String providerCredential;
    private Boolean outboundEnabled;
    private Integer requestsPerMinute;
    private Integer maxContextTokens;
    private Integer maxConcurrentRequests;
    private Integer requestTimeoutSeconds;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Integer version;

    public Short getSingletonKey() { return singletonKey; }
    public void setSingletonKey(Short singletonKey) { this.singletonKey = singletonKey; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getProviderCredential() { return providerCredential; }
    public void setProviderCredential(String providerCredential) { this.providerCredential = providerCredential; }
    public Boolean getOutboundEnabled() { return outboundEnabled; }
    public void setOutboundEnabled(Boolean outboundEnabled) { this.outboundEnabled = outboundEnabled; }
    public Integer getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(Integer requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    public Integer getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(Integer maxContextTokens) { this.maxContextTokens = maxContextTokens; }
    public Integer getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(Integer maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
    public Integer getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(Integer requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
