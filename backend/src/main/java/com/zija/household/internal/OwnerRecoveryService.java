package com.zija.household.internal;

import com.zija.household.internal.persistence.OwnerRecoveryTokenEntity;
import com.zija.household.internal.persistence.OwnerRecoveryTokenMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
class OwnerRecoveryService {

    private final OwnerRecoveryTokenMapper tokenMapper;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    OwnerRecoveryService(OwnerRecoveryTokenMapper tokenMapper, IdentityApi identityApi,
                        SystemApi systemApi) {
        this.tokenMapper = tokenMapper;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
    }

    public record GenerateResult(UUID id, String rawToken, OffsetDateTime expiresAt) {
    }

    @Transactional
    public GenerateResult generate(UUID householdId, UUID ownerAccountId) {
        tokenMapper.invalidatePending(ownerAccountId);

        var rawBytes = new byte[32];
        random.nextBytes(rawBytes);
        var rawToken = base64Url.encodeToString(rawBytes);
        var expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15);

        var entity = new OwnerRecoveryTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setAccountId(ownerAccountId);
        entity.setTokenDigest(InvitationService.sha256Hex(rawToken));
        entity.setExpiresAt(expiresAt);
        tokenMapper.insert(entity);

        return new GenerateResult(entity.getId(), rawToken, expiresAt);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var digest = InvitationService.sha256Hex(rawToken);
        var token = tokenMapper.selectByDigestForUpdate(digest)
                .orElseThrow(InvalidInvitationException::new);
        if (token.getConsumedAt() != null
                || token.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidInvitationException();
        }
        tokenMapper.markConsumed(token.getId());
        identityApi.changePassword(token.getAccountId(),
                new IdentityApi.ChangePasswordCommand(null, newPassword));
        identityApi.disableAccount(token.getAccountId());
        identityApi.activateAccount(token.getAccountId());
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "OWNER_RECOVERY", "SUCCESS", token.getHouseholdId(),
                token.getAccountId(), token.getAccountId(), null, null, null));
    }

    public Optional<OwnerRecoveryTokenEntity> inspect(String rawToken) {
        return tokenMapper.selectByDigestForUpdate(InvitationService.sha256Hex(rawToken))
                .filter(t -> t.getConsumedAt() == null
                        && t.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
