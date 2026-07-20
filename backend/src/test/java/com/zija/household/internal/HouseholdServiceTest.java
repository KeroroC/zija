package com.zija.household.internal;

import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HouseholdServiceTest {

    @Test
    void bootstrapCreatesHouseholdOwnerAndAccountInOrder() {
        var householdMapper = mock(HouseholdMapper.class);
        var memberMapper = mock(MemberMapper.class);
        var identityApi = mock(IdentityApi.class);
        var systemApi = mock(SystemApi.class);
        when(identityApi.registerAccount(any())).thenReturn(new IdentityApi.AccountInfo(
                UUID.randomUUID(), "owner", "所有者", null, "ACTIVE"));

        var service = new HouseholdService(householdMapper, memberMapper, identityApi, systemApi);
        service.bootstrap(new HouseholdService.BootstrapCommand(
                "我的家", "owner", "Passw0rd!", "所有者", null));

        verify(householdMapper).insertSingleton(any());
        verify(identityApi).registerAccount(any());
        verify(memberMapper).insert(any(MemberEntity.class));
        verify(systemApi).recordAudit(any());
    }

    @Test
    void bootstrapConflictMapsToAlreadyInitialized() {
        var householdMapper = mock(HouseholdMapper.class);
        doThrow(new DuplicateKeyException("dup"))
                .when(householdMapper).insertSingleton(any());

        var service = new HouseholdService(householdMapper,
                mock(MemberMapper.class), mock(IdentityApi.class), mock(SystemApi.class));

        assertThatThrownBy(() -> service.bootstrap(new HouseholdService.BootstrapCommand(
                "我的家", "owner", "Passw0rd!", "所有者", null)))
                .isInstanceOf(HouseholdAlreadyInitializedException.class);
    }
}
