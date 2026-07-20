package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemberServiceTest {

    @Test
    void adminCannotDeactivateOwner() {
        var mapper = mock(MemberMapper.class);
        var owner = member("OWNER", "ACTIVE");
        when(mapper.selectById(owner.getId())).thenReturn(owner);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(),
                owner.getId(), "DEACTIVATED"))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void cannotDeactivateSelf() {
        var mapper = mock(MemberMapper.class);
        var self = member("ADMIN", "ACTIVE");
        when(mapper.selectById(self.getId())).thenReturn(self);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        assertThatThrownBy(() -> service.updateStatus(self.getAccountId(),
                self.getId(), "DEACTIVATED"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ownerCanPromoteMemberToAdmin() {
        var mapper = mock(MemberMapper.class);
        var target = member("MEMBER", "ACTIVE");
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateRole(any(), any(), any())).thenReturn(1);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        service.updateRole(UUID.randomUUID(), target.getId(), "ADMIN");
        verify(mapper).updateRole(target.getId(), "ADMIN", target.getVersion());
    }

    @Test
    void cannotSetRoleToOwner() {
        var mapper = mock(MemberMapper.class);
        var target = member("MEMBER", "ACTIVE");
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        assertThatThrownBy(() -> service.updateRole(UUID.randomUUID(),
                target.getId(), "OWNER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MemberEntity member(String role, String status) {
        var m = new MemberEntity();
        m.setId(UUID.randomUUID());
        m.setHouseholdId(UUID.randomUUID());
        m.setAccountId(UUID.randomUUID());
        m.setRole(role);
        m.setStatus(status);
        m.setVersion(0);
        return m;
    }
}
