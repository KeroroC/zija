package com.zija.identity.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.identity.IdentityApi;
import com.zija.identity.internal.exception.AccountVersionConflictException;
import com.zija.identity.internal.exception.InvalidCredentialsException;
import com.zija.identity.internal.exception.UsernameAlreadyExistsException;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 身份认证服务，管理用户账户的完整生命周期。
 * <p>
 * 实现 {@link IdentityApi} 接口，提供账户注册、查询、密码修改/重置、
 * 账户启用/禁用等功能。用户名在存储前会进行归一化处理（去空格、转小写）。
 * 密码修改和重置操作会同时失效该账户的所有活跃会话。
 */
@Service
class IdentityService implements IdentityApi {

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final ZijaSessionInvalidator sessionInvalidator;

    IdentityService(
            AccountMapper accountMapper,
            PasswordEncoder passwordEncoder,
            ZijaSessionInvalidator sessionInvalidator
    ) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionInvalidator = sessionInvalidator;
    }

    /**
     * 注册新账户，用户名归一化后全局唯一。
     *
     * @param command 注册命令（用户名、密码、显示名、邮箱）
     * @return 注册成功的账户信息
     * @throws UsernameAlreadyExistsException 如果用户名已存在
     */
    @Override
    @Transactional
    public AccountInfo registerAccount(RegisterAccountCommand command) {
        var trimmed = command.username().trim();
        var normalized = normalize(trimmed);

        if (accountMapper.selectByNormalizedUsername(normalized).isPresent()) {
            throw new UsernameAlreadyExistsException(normalized);
        }

        var entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(trimmed);
        entity.setUsernameNormalized(normalized);
        entity.setPasswordHash(passwordEncoder.encode(command.password()));
        entity.setDisplayName(command.displayName().trim());
        entity.setEmail(command.email());
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        accountMapper.insert(entity);

        return toInfo(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountInfo> findById(UUID id) {
        return Optional.ofNullable(accountMapper.selectById(id)).map(this::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountInfo> findByNormalizedUsername(String normalizedUsername) {
        return accountMapper.selectByNormalizedUsername(normalizedUsername).map(this::toInfo);
    }

    /**
     * 修改密码，需验证当前密码。修改成功后失效该账户所有会话。
     *
     * @param accountId 账户 ID
     * @param command   修改密码命令（当前密码、新密码）
     * @throws InvalidCredentialsException 如果账户不存在或当前密码不正确
     * @throws AccountVersionConflictException 如果乐观锁失败
     */
    @Override
    @Transactional
    public void changePassword(UUID accountId, ChangePasswordCommand command) {
        var account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new InvalidCredentialsException();
        }
        if (command.currentPassword() == null
                || !passwordEncoder.matches(command.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        var newHash = passwordEncoder.encode(command.newPassword());
        if (accountMapper.updatePasswordHash(accountId, newHash, account.getVersion()) != 1) {
            throw new AccountVersionConflictException();
        }
        sessionInvalidator.invalidateAllForAccount(accountId);
    }

    /**
     * 重置密码（无需验证当前密码），用于管理员操作或密码恢复流程。
     * 重置成功后失效该账户所有会话。
     *
     * @param accountId 账户 ID
     * @param newPassword 新密码
     * @throws InvalidCredentialsException 如果账户不存在
     * @throws AccountVersionConflictException 如果乐观锁失败
     */
    @Override
    @Transactional
    public void resetPassword(UUID accountId, String newPassword) {
        var account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new InvalidCredentialsException();
        }
        var newHash = passwordEncoder.encode(newPassword);
        if (accountMapper.updatePasswordHash(accountId, newHash, account.getVersion()) != 1) {
            throw new AccountVersionConflictException();
        }
        sessionInvalidator.invalidateAllForAccount(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AccountInfo> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var entities = accountMapper.selectByIds(ids);
        return entities.stream()
                .collect(Collectors.toMap(AccountEntity::getId, this::toInfo));
    }

    /**
     * 禁用指定账户。
     *
     * @param accountId 账户 ID
     */
    @Override
    @Transactional
    public void disableAccount(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account != null) {
            accountMapper.updateStatus(accountId, "DISABLED", account.getVersion());
        }
    }

    /**
     * 激活指定账户。
     *
     * @param accountId 账户 ID
     */
    @Override
    @Transactional
    public void activateAccount(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account != null) {
            accountMapper.updateStatus(accountId, "ACTIVE", account.getVersion());
        }
    }

    /**
     * 校验指定账户是否存在且状态为 ACTIVE，不满足则抛出异常。
     *
     * @param accountId 账户 ID
     * @throws InvalidCredentialsException 如果账户不存在或非活跃状态
     */
    @Override
    @Transactional(readOnly = true)
    public void requireActive(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new InvalidCredentialsException();
        }
    }

    /**
     * 修改指定账户的显示名称，返回更新后的账户信息。
     * 此操作不改动会话：调用方负责刷新当前会话的认证主体。
     *
     * @throws InvalidCredentialsException 如果账户不存在
     * @throws AccountVersionConflictException 如果乐观锁失败
     */
    @Transactional
    public AccountInfo updateDisplayName(UUID accountId, String displayName) {
        var account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new InvalidCredentialsException();
        }
        var trimmed = displayName.trim();
        if (accountMapper.updateDisplayName(accountId, trimmed, account.getVersion()) != 1) {
            throw new AccountVersionConflictException();
        }
        account.setDisplayName(trimmed);
        return toInfo(account);
    }

    static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private AccountInfo toInfo(AccountEntity entity) {
        return new AccountInfo(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getStatus()
        );
    }
}
