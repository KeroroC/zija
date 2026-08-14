package com.zija.identity.internal;

import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZijaUserDetailsServiceTest {

    @Test
    void loadsActiveAccountByNormalizedUsername() {
        var mapper = mock(AccountMapper.class);
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("Owner");
        account.setUsernameNormalized("owner");
        account.setPasswordHash("{bcrypt}$2a$10$hash");
        account.setDisplayName("所有者");
        account.setStatus(AccountStatus.ACTIVE);
        when(mapper.selectByNormalizedUsername("owner")).thenReturn(Optional.of(account));

        var service = new ZijaUserDetailsService(mapper);
        var details = service.loadUserByUsername("Owner");

        var principal = (com.zija.ZijaPrincipal) details;
        assertThat(principal.getAccountId()).isEqualTo(account.getId());
        assertThat(principal.getUsername()).isEqualTo("Owner");
        assertThat(principal.getName()).isEqualTo(account.getId().toString());
        assertThat(principal.getPassword()).isEqualTo("{bcrypt}$2a$10$hash");
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    void rejectsDisabledAccount() {
        var mapper = mock(AccountMapper.class);
        var account = new AccountEntity();
        account.setStatus(AccountStatus.DISABLED);
        when(mapper.selectByNormalizedUsername("x")).thenReturn(Optional.of(account));

        var service = new ZijaUserDetailsService(mapper);
        assertThatThrownBy(() -> service.loadUserByUsername("x"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
