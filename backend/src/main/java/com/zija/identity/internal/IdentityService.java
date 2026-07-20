package com.zija.identity.internal;

import com.zija.identity.IdentityApi;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import com.zija.system.SystemApi;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
class IdentityService implements IdentityApi {

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final SystemApi systemApi;

    IdentityService(
            AccountMapper accountMapper,
            PasswordEncoder passwordEncoder,
            SystemApi systemApi
    ) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.systemApi = systemApi;
    }

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

    @Override
    @Transactional
    public void changePassword(UUID accountId, ChangePasswordCommand command) {
        var account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new InvalidCredentialsException();
        }
        if (command.currentPassword() != null
                && !passwordEncoder.matches(command.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        var newHash = passwordEncoder.encode(command.newPassword());
        if (accountMapper.updatePasswordHash(accountId, newHash, account.getVersion()) != 1) {
            throw new InvalidCredentialsException();
        }
    }

    @Override
    @Transactional
    public void disableAccount(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account != null) {
            accountMapper.updateStatus(accountId, "DISABLED", account.getVersion());
        }
    }

    @Override
    @Transactional
    public void activateAccount(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account != null) {
            accountMapper.updateStatus(accountId, "ACTIVE", account.getVersion());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActive(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new InvalidCredentialsException();
        }
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
