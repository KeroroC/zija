package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class HouseholdService implements HouseholdApi {

    private final HouseholdMapper householdMapper;
    private final MemberMapper memberMapper;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;

    HouseholdService(
            HouseholdMapper householdMapper,
            MemberMapper memberMapper,
            IdentityApi identityApi,
            SystemApi systemApi
    ) {
        this.householdMapper = householdMapper;
        this.memberMapper = memberMapper;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
    }

    public record BootstrapCommand(
            String householdName,
            String username,
            String password,
            String displayName,
            String email
    ) {
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInitialized() {
        return householdMapper.selectById((short) 1) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HouseholdInfo> findHousehold() {
        return Optional.ofNullable(householdMapper.selectById((short) 1))
                .map(h -> new HouseholdInfo(h.getId(), h.getName(), h.getTimezone()));
    }

    @Transactional
    public HouseholdInfo bootstrap(BootstrapCommand command) {
        var household = new HouseholdEntity();
        household.setSingletonKey((short) 1);
        household.setId(UUID.randomUUID());
        household.setName(command.householdName());
        household.setTimezone("Asia/Shanghai");

        try {
            householdMapper.insertSingleton(household);
        } catch (DuplicateKeyException ex) {
            throw new HouseholdAlreadyInitializedException();
        }

        var account = identityApi.registerAccount(new IdentityApi.RegisterAccountCommand(
                command.username(), command.password(),
                command.displayName(), command.email()));

        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(household.getId());
        member.setAccountId(account.id());
        member.setRole("OWNER");
        member.setStatus("ACTIVE");
        memberMapper.insert(member);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "HOUSEHOLD_INITIALIZED", "SUCCESS",
                household.getId(), account.id(), account.id(),
                null, null, null));

        return new HouseholdInfo(household.getId(), household.getName(), household.getTimezone());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberInfo> findMembers(UUID householdId) {
        return memberMapper.selectByHousehold(householdId).stream()
                .map(m -> new MemberInfo(m.getId(), m.getHouseholdId(), m.getAccountId(),
                        null, null, MemberRole.valueOf(m.getRole()), m.getStatus()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberInfo> findMember(UUID householdId, UUID accountId) {
        return memberMapper.selectByAccount(accountId)
                .filter(m -> m.getHouseholdId().equals(householdId))
                .map(m -> new MemberInfo(m.getId(), m.getHouseholdId(), m.getAccountId(),
                        null, null, MemberRole.valueOf(m.getRole()), m.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public MemberInfo requireActiveMember(UUID accountId) {
        var member = memberMapper.selectByAccount(accountId)
                .orElseThrow(() -> new InvalidCredentialsException());
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new InvalidCredentialsException();
        }
        return toInfo(member);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAtLeastRole(UUID accountId, MemberRole requiredRole) {
        var member = memberMapper.selectByAccount(accountId).orElse(null);
        if (member == null || !"ACTIVE".equals(member.getStatus())) {
            return false;
        }
        return MemberRole.valueOf(member.getRole()).isAtLeast(requiredRole);
    }

    private MemberInfo toInfo(MemberEntity m) {
        return new MemberInfo(m.getId(), m.getHouseholdId(), m.getAccountId(),
                null, null, MemberRole.valueOf(m.getRole()), m.getStatus());
    }
}
