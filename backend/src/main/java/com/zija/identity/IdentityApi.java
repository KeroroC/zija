package com.zija.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IdentityApi {

    AccountInfo registerAccount(RegisterAccountCommand command);

    Optional<AccountInfo> findById(UUID id);

    Optional<AccountInfo> findByNormalizedUsername(String normalizedUsername);

    Map<UUID, AccountInfo> findByIds(Collection<UUID> ids);

    void changePassword(UUID accountId, ChangePasswordCommand command);

    void resetPassword(UUID accountId, String newPassword);

    void disableAccount(UUID accountId);

    void activateAccount(UUID accountId);

    void requireActive(UUID accountId);

    record RegisterAccountCommand(
            String username,
            String password,
            String displayName,
            String email
    ) {
    }

    record ChangePasswordCommand(
            String currentPassword,
            String newPassword
    ) {
    }

    record AccountInfo(
            UUID id,
            String username,
            String displayName,
            String email,
            String status
    ) {
    }
}
