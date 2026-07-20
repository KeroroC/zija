package com.zija.identity.internal;

import com.zija.ZijaPrincipal;
import com.zija.identity.internal.persistence.AccountMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
class ZijaUserDetailsService implements UserDetailsService {

    private final AccountMapper accountMapper;

    ZijaUserDetailsService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var normalized = username.trim().toLowerCase(Locale.ROOT);
        var account = accountMapper.selectByNormalizedUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new UsernameNotFoundException("not active");
        }
        return new ZijaPrincipal(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getPasswordHash(),
                true
        );
    }
}
