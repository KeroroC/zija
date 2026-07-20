package com.zija;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

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

    public UUID getAccountId() { return accountId; }

    public String getName() { return accountId.toString(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

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
