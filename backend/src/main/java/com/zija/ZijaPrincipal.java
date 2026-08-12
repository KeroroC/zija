package com.zija;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * 认证主体（已登录用户）。
 * <p>
 * 实现 Spring Security 的 {@link UserDetails} 接口，封装账户 ID、用户名、显示名称、
 * 密码哈希和启用状态等信息。作为 {@link Authentication#getPrincipal()} 的返回值
 * 在整个请求生命周期中使用。
 */
public final class ZijaPrincipal implements UserDetails {

    private final UUID accountId;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final boolean active;

    public ZijaPrincipal(
            UUID accountId,
            String username,
            String displayName,
            String passwordHash,
            boolean active
    ) {
        this.accountId = accountId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    /** 返回账户的 UUID。 */
    public UUID getAccountId() { return accountId; }

    /** 返回账户 UUID 的字符串形式，用作 Spring Session 的主索引名。 */
    public String getName() { return accountId.toString(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

    /** 返回用户显示名称（昵称）。 */
    public String getDisplayName() { return displayName; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }
}
