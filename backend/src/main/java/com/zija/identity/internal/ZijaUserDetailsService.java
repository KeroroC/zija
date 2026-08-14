package com.zija.identity.internal;

import com.zija.shared.ZijaMemberStatus;
import com.zija.ZijaPrincipal;
import com.zija.identity.internal.persistence.AccountMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Spring Security 用户详情加载服务。
 * <p>
 * 根据归一化的用户名查询账户，构造 {@link ZijaPrincipal} 供 Spring Security
 * 认证流程使用。仅加载状态为 ACTIVE 的账户，非活跃账户视为不存在。
 */
@Service
class ZijaUserDetailsService implements UserDetailsService {

    private final AccountMapper accountMapper;

    ZijaUserDetailsService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * 根据用户名加载用户详情。用户名会先归一化（去空格、转小写）再查询。
     *
     * @param username 用户名
     * @return 用户详情
     * @throws UsernameNotFoundException 如果用户不存在或非活跃状态
     */
    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String username) {
        var normalized = username.trim().toLowerCase(Locale.ROOT);
        var account = accountMapper.selectByNormalizedUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        if (!ZijaMemberStatus.ACTIVE.equals(account.getStatus())) {
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
