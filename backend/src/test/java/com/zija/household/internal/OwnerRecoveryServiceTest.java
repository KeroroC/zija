package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.OwnerRecoveryTokenEntity;
import com.zija.household.internal.persistence.OwnerRecoveryTokenMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OwnerRecoveryServiceTest {

    @Test
    void generateInvalidatesPreviousTokens() {
        var mapper = mock(OwnerRecoveryTokenMapper.class);
        var identityApi = mock(IdentityApi.class);
        var service = new OwnerRecoveryService(mapper, identityApi, mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        var result = service.generate(UUID.randomUUID(), UUID.randomUUID());

        verify(mapper).invalidatePending(any());
        verify(mapper).insert(any(OwnerRecoveryTokenEntity.class));
        assertThat(result.rawToken()).isNotBlank();
    }

    @Test
    void resetPasswordConsumesTokenOnce() {
        var mapper = mock(OwnerRecoveryTokenMapper.class);
        var identityApi = mock(IdentityApi.class);
        var token = token(false, future());
        when(mapper.selectByDigestForUpdate(any())).thenReturn(Optional.of(token));
        when(mapper.markConsumed(any())).thenReturn(1);
        var service = new OwnerRecoveryService(mapper, identityApi, mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        service.resetPassword("raw", "NewPass1");

        verify(identityApi).resetPassword(eq(token.getAccountId()), eq("NewPass1"));
    }

    @Test
    void expiredTokenRejected() {
        var mapper = mock(OwnerRecoveryTokenMapper.class);
        var token = token(true, past());
        when(mapper.selectByDigestForUpdate(any())).thenReturn(Optional.of(token));
        var service = new OwnerRecoveryService(mapper, mock(IdentityApi.class), mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        assertThatThrownBy(() -> service.resetPassword("raw", "NewPass1"))
                .isInstanceOf(InvalidInvitationException.class);
    }

    private OwnerRecoveryTokenEntity token(boolean consumed, OffsetDateTime expires) {
        var t = new OwnerRecoveryTokenEntity();
        t.setId(UUID.randomUUID());
        t.setHouseholdId(UUID.randomUUID());
        t.setAccountId(UUID.randomUUID());
        t.setTokenDigest("digest");
        t.setExpiresAt(expires);
        t.setConsumedAt(consumed ? OffsetDateTime.now(ZoneOffset.UTC) : null);
        return t;
    }

    private OffsetDateTime future() { return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10); }
    private OffsetDateTime past() { return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10); }
}
