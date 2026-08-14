package com.zija.household.internal;

import com.zija.shared.ZijaAuditOutcome;
import com.zija.shared.ZijaMemberRole;
import com.zija.shared.ZijaMemberStatus;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import com.zija.household.internal.exception.InvalidCredentialsException;
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

/**
 * 家庭管理服务，处理家庭的初始化和基本信息查询。
 * <p>
 * 实现 {@link HouseholdApi} 接口，提供系统初始化引导（bootstrap）、
 * 家庭信息查询、成员列表查询及角色权限校验等功能。
 * 系统采用单家庭模式，家庭记录通过单例键（singletonKey=1）保证唯一性。
 */
@Service
class HouseholdService implements HouseholdApi {

    private final HouseholdMapper householdMapper;
    private final MemberMapper memberMapper;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

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

    /**
     * 检查系统是否已完成初始化（即家庭记录是否存在）。
     *
     * @return 已初始化返回 true，否则返回 false
     */
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

    /**
     * 执行系统初始化引导，创建家庭和首个 OWNER 账户。
     * <p>
     * 此操作在同一个事务中完成：创建家庭记录、注册账户、创建 OWNER 成员、记录审计日志。
     * 如果家庭已初始化则抛出异常。
     *
     * @param command 初始化命令（家庭名、用户名、密码、显示名、邮箱）
     * @return 创建的家庭信息
     * @throws HouseholdAlreadyInitializedException 如果家庭已存在
     */
    @Transactional
    public HouseholdInfo bootstrap(BootstrapCommand command) {
        var household = new HouseholdEntity();
        household.setSingletonKey((short) 1);
        household.setId(UUID.randomUUID());
        household.setName(command.householdName());
        household.setTimezone(DEFAULT_TIMEZONE);
        household.setVersion(0);

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
        member.setRole(ZijaMemberRole.OWNER);
        member.setStatus(ZijaMemberStatus.ACTIVE);
        member.setVersion(0);
        memberMapper.insert(member);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.HOUSEHOLD_INITIALIZED, ZijaAuditOutcome.SUCCESS,
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

    /**
     * 要求指定账户为活跃成员，否则抛出异常。
     *
     * @param accountId 账户 ID
     * @return 成员信息
     * @throws InvalidCredentialsException 如果账户不是成员或非活跃状态
     */
    @Override
    @Transactional(readOnly = true)
    public MemberInfo requireActiveMember(UUID accountId) {
        var member = memberMapper.selectByAccount(accountId)
                .orElseThrow(() -> new InvalidCredentialsException());
        if (!ZijaMemberStatus.ACTIVE.equals(member.getStatus())) {
            throw new InvalidCredentialsException();
        }
        return toInfo(member);
    }

    /**
     * 检查指定账户是否具有至少指定级别的角色。
     *
     * @param accountId    账户 ID
     * @param requiredRole 最低要求角色
     * @return 满足角色要求返回 true，否则返回 false
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasAtLeastRole(UUID accountId, MemberRole requiredRole) {
        var member = memberMapper.selectByAccount(accountId).orElse(null);
        if (member == null || !ZijaMemberStatus.ACTIVE.equals(member.getStatus())) {
            return false;
        }
        return MemberRole.valueOf(member.getRole()).isAtLeast(requiredRole);
    }

    private MemberInfo toInfo(MemberEntity m) {
        return new MemberInfo(m.getId(), m.getHouseholdId(), m.getAccountId(),
                null, null, MemberRole.valueOf(m.getRole()), m.getStatus());
    }
}
