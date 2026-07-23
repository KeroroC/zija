package com.zija.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 身份模块公共 API，提供账户注册、查询、密码管理及账户启用/禁用能力。
 */
public interface IdentityApi {

    /** 注册新账户。 */
    AccountInfo registerAccount(RegisterAccountCommand command);

    /** 按 ID 查询账户。 */
    Optional<AccountInfo> findById(UUID id);

    /** 按规范化用户名查询账户。 */
    Optional<AccountInfo> findByNormalizedUsername(String normalizedUsername);

    /** 批量按 ID 查询账户，返回 ID 到账户信息的映射。 */
    Map<UUID, AccountInfo> findByIds(Collection<UUID> ids);

    /** 修改当前密码（需验证旧密码）。 */
    void changePassword(UUID accountId, ChangePasswordCommand command);

    /** 重置指定账户的密码（管理员操作，无需旧密码）。 */
    void resetPassword(UUID accountId, String newPassword);

    /** 禁用指定账户，禁止其登录。 */
    void disableAccount(UUID accountId);

    /** 激活指定账户，恢复其登录能力。 */
    void activateAccount(UUID accountId);

    /** 校验指定账户处于激活状态，否则抛出异常。 */
    void requireActive(UUID accountId);

    /** 注册账户的命令参数。 */
    record RegisterAccountCommand(
            String username,
            String password,
            String displayName,
            String email
    ) {
    }

    /** 修改密码的命令参数。 */
    record ChangePasswordCommand(
            String currentPassword,
            String newPassword
    ) {
    }

    /** 账户基本信息。 */
    record AccountInfo(
            UUID id,
            String username,
            String displayName,
            String email,
            String status
    ) {
    }
}
