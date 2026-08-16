package com.zija.ai.internal;

import com.zija.ai.internal.exception.AiConfigurationVersionConflictException;
import com.zija.ai.internal.persistence.AiSettingsEntity;
import com.zija.ai.internal.persistence.AiSettingsMapper;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
class AiSettingsService {

    static final short SINGLETON_KEY = 1;
    private final AiSettingsMapper mapper;
    private final SystemApi systemApi;

    AiSettingsService(AiSettingsMapper mapper, SystemApi systemApi) {
        this.mapper = mapper;
        this.systemApi = systemApi;
    }

    record UpdateCommand(
            boolean enabled,
            String providerId,
            String credential,
            boolean clearCredential,
            boolean outboundEnabled,
            int requestsPerMinute,
            int maxContextTokens,
            int maxConcurrentRequests,
            int requestTimeoutSeconds,
            int version
    ) {
    }

    record SettingsView(
            boolean enabled,
            String providerId,
            boolean credentialConfigured,
            boolean outboundEnabled,
            int requestsPerMinute,
            int maxContextTokens,
            int maxConcurrentRequests,
            int requestTimeoutSeconds,
            int version
    ) {
    }

    record ProviderConfiguration(
            boolean enabled,
            String providerId,
            String credential,
            boolean outboundEnabled,
            int requestsPerMinute,
            int maxContextTokens,
            int maxConcurrentRequests,
            int requestTimeoutSeconds
    ) {
    }

    @Transactional
    SettingsView currentView() {
        return toView(currentEntity());
    }

    @Transactional
    ProviderConfiguration currentConfiguration() {
        AiSettingsEntity entity = currentEntity();
        return new ProviderConfiguration(
                Boolean.TRUE.equals(entity.getEnabled()), entity.getProviderId(), entity.getProviderCredential(),
                Boolean.TRUE.equals(entity.getOutboundEnabled()), entity.getRequestsPerMinute(),
                entity.getMaxContextTokens(), entity.getMaxConcurrentRequests(), entity.getRequestTimeoutSeconds());
    }

    @Transactional
    SettingsView update(UUID householdId, UUID actorAccountId, UpdateCommand command) {
        validate(command);
        AiSettingsEntity current = currentEntity();
        if (!Integer.valueOf(command.version()).equals(current.getVersion())) {
            throw new AiConfigurationVersionConflictException();
        }
        current.setEnabled(command.enabled());
        current.setProviderId(command.providerId().trim());
        if (command.clearCredential()) {
            current.setProviderCredential(null);
        } else if (command.credential() != null) {
            current.setProviderCredential(command.credential());
        }
        current.setOutboundEnabled(command.outboundEnabled());
        current.setRequestsPerMinute(command.requestsPerMinute());
        current.setMaxContextTokens(command.maxContextTokens());
        current.setMaxConcurrentRequests(command.maxConcurrentRequests());
        current.setRequestTimeoutSeconds(command.requestTimeoutSeconds());
        current.setUpdatedAt(OffsetDateTime.now());
        if (mapper.updateById(current) == 0) {
            throw new AiConfigurationVersionConflictException();
        }
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.AI_SETTING_UPDATED, ZijaAuditOutcome.SUCCESS,
                householdId, actorAccountId, null, null, null,
                Map.of("providerId", current.getProviderId(),
                        "enabled", String.valueOf(current.getEnabled()),
                        "outboundEnabled", String.valueOf(current.getOutboundEnabled()),
                        "version", String.valueOf(command.version()))));
        return toView(current);
    }

    private AiSettingsEntity currentEntity() {
        AiSettingsEntity entity = mapper.selectById(SINGLETON_KEY);
        if (entity != null) {
            return entity;
        }
        mapper.insertDefaultIfMissing();
        return mapper.selectById(SINGLETON_KEY);
    }

    private SettingsView toView(AiSettingsEntity entity) {
        return new SettingsView(
                Boolean.TRUE.equals(entity.getEnabled()), entity.getProviderId(),
                entity.getProviderCredential() != null && !entity.getProviderCredential().isBlank(),
                Boolean.TRUE.equals(entity.getOutboundEnabled()), entity.getRequestsPerMinute(),
                entity.getMaxContextTokens(), entity.getMaxConcurrentRequests(),
                entity.getRequestTimeoutSeconds(), entity.getVersion());
    }

    private void validate(UpdateCommand command) {
        if (command.providerId() == null || command.providerId().isBlank() || command.providerId().length() > 50) {
            throw new IllegalArgumentException("providerId must not be blank and must be at most 50 characters");
        }
        if (command.credential() != null && command.credential().length() > 4096) {
            throw new IllegalArgumentException("credential must be at most 4096 characters");
        }
        if (command.credential() != null && command.clearCredential()) {
            throw new IllegalArgumentException("credential and clearCredential cannot be used together");
        }
        if (command.requestsPerMinute() < 1 || command.requestsPerMinute() > 600) {
            throw new IllegalArgumentException("requestsPerMinute must be between 1 and 600");
        }
        if (command.maxContextTokens() < 256 || command.maxContextTokens() > 131072) {
            throw new IllegalArgumentException("maxContextTokens must be between 256 and 131072");
        }
        if (command.maxConcurrentRequests() < 1 || command.maxConcurrentRequests() > 32) {
            throw new IllegalArgumentException("maxConcurrentRequests must be between 1 and 32");
        }
        if (command.requestTimeoutSeconds() < 1 || command.requestTimeoutSeconds() > 300) {
            throw new IllegalArgumentException("requestTimeoutSeconds must be between 1 and 300");
        }
        if (command.version() < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
