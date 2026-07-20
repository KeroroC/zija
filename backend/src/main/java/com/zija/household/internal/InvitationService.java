package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.InvitationEntity;
import com.zija.household.internal.persistence.InvitationMapper;
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
class InvitationService {

    private final InvitationMapper invitationMapper;
    private final SystemApi systemApi;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    InvitationService(InvitationMapper invitationMapper, SystemApi systemApi) {
        this.invitationMapper = invitationMapper;
        this.systemApi = systemApi;
    }

    public record CreateResult(UUID id, String rawToken, String digest,
                                HouseholdApi.MemberRole role, OffsetDateTime expiresAt) {
    }

    public record RedeemCommand(String username, String password,
                                String displayName, String email) {
    }

    @Transactional
    public CreateResult create(UUID householdId, UUID createdBy,
                                HouseholdApi.MemberRole role, int expiresInHours) {
        var rawBytes = new byte[32];
        random.nextBytes(rawBytes);
        var rawToken = base64Url.encodeToString(rawBytes);
        var digest = sha256Hex(rawToken);

        var entity = new InvitationEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setTokenDigest(digest);
        entity.setRole(role.name());
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(expiresInHours));
        entity.setCreatedBy(createdBy);
        invitationMapper.insert(entity);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "INVITATION_CREATED", "SUCCESS", householdId, createdBy, null,
                null, null, null));
        return new CreateResult(entity.getId(), rawToken, digest, role, entity.getExpiresAt());
    }

    @Transactional
    public void redeem(String rawToken, RedeemCommand command,
                       IdentityApi identityApi, MemberService memberService) {
        var digest = sha256Hex(rawToken);
        var invitation = invitationMapper.selectByDigestForUpdate(digest)
                .orElseThrow(InvalidInvitationException::new);

        if (invitation.getConsumedAt() != null
                || invitation.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidInvitationException();
        }

        var account = identityApi.registerAccount(new IdentityApi.RegisterAccountCommand(
                command.username(), command.password(), command.displayName(), command.email()));
        memberService.addMember(invitation.getHouseholdId(), account.id(),
                HouseholdApi.MemberRole.valueOf(invitation.getRole()));
        invitationMapper.markConsumed(invitation.getId(), account.id());

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "INVITATION_REDEEMED", "SUCCESS", invitation.getHouseholdId(),
                account.id(), account.id(), null, null, null));
    }

    public Optional<InvitationEntity> inspect(String rawToken) {
        return invitationMapper.selectByDigestForUpdate(sha256Hex(rawToken))
                .filter(i -> i.getConsumedAt() == null
                        && i.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    static String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
