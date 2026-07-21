package com.zija.identity.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.identity.IdentityApi;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityServiceTest {

    private AccountMapper accountMapper;
    private PasswordEncoder passwordEncoder;
    private SystemApi systemApi;
    private ZijaSessionInvalidator sessionInvalidator;
    private IdentityService service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        systemApi = mock(SystemApi.class);
        sessionInvalidator = mock(ZijaSessionInvalidator.class);
        service = new IdentityService(accountMapper, passwordEncoder, systemApi, sessionInvalidator);
    }

    @Test
    void normalizesUsernameToLowercase() {
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("{bcrypt}hash");
        when(accountMapper.selectByNormalizedUsername("owner")).thenReturn(Optional.empty());

        service.registerAccount(new IdentityApi.RegisterAccountCommand(
                " Owner ", "Passw0rd!", "所有者", "owner@example.com"));

        var captor = org.mockito.ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountMapper).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("Owner");
        assertThat(captor.getValue().getUsernameNormalized()).isEqualTo("owner");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateNormalizedUsername() {
        var existing = new AccountEntity();
        existing.setUsernameNormalized("owner");
        when(accountMapper.selectByNormalizedUsername("owner")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.registerAccount(new IdentityApi.RegisterAccountCommand(
                "Owner", "Passw0rd!", "所有者", null)))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void changePasswordUpgradesEncoding() {
        var account = new AccountEntity();
        account.setId(java.util.UUID.randomUUID());
        account.setVersion(3);
        account.setPasswordHash("{sha256}legacy");
        when(passwordEncoder.matches("OldPass1", "{sha256}legacy")).thenReturn(true);
        when(passwordEncoder.encode("NewPass2")).thenReturn("{bcrypt}newhash");
        when(accountMapper.selectById(account.getId())).thenReturn(account);
        when(accountMapper.updatePasswordHash(any(), any(), any())).thenReturn(1);

        service.changePassword(account.getId(),
                new IdentityApi.ChangePasswordCommand("OldPass1", "NewPass2"));

        verify(accountMapper).updatePasswordHash(account.getId(), "{bcrypt}newhash", 3);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        var account = new AccountEntity();
        account.setId(java.util.UUID.randomUUID());
        account.setVersion(0);
        account.setPasswordHash("{bcrypt}hash");
        when(passwordEncoder.matches("wrong", "{bcrypt}hash")).thenReturn(false);
        when(accountMapper.selectById(account.getId())).thenReturn(account);

        assertThatThrownBy(() -> service.changePassword(account.getId(),
                new IdentityApi.ChangePasswordCommand("wrong", "newpass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void changePasswordRejectsMissingCurrentPassword() {
        var account = new AccountEntity();
        account.setId(java.util.UUID.randomUUID());
        account.setVersion(0);
        account.setPasswordHash("{bcrypt}hash");
        when(accountMapper.selectById(account.getId())).thenReturn(account);
        when(passwordEncoder.encode("NewPass2")).thenReturn("{bcrypt}newhash");
        when(accountMapper.updatePasswordHash(
                account.getId(), "{bcrypt}newhash", 0)).thenReturn(1);

        assertThatThrownBy(() -> service.changePassword(account.getId(),
                new IdentityApi.ChangePasswordCommand(null, "NewPass2")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(passwordEncoder);
        verify(accountMapper, never()).updatePasswordHash(any(), any(), any());
    }
}
