package com.zija.identity;

import java.util.Optional;
import java.util.UUID;

public interface IdentityApi {

    AccountInfo registerAccount(RegisterAccountCommand command);

    Optional<AccountInfo> findById(UUID id);

    Optional<AccountInfo> findByNormalizedUsername(String normalizedUsername);

    void changePassword(UUID accountId, ChangePasswordCommand command);

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
