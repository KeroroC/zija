package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZijaSessionInvalidatorTest {

    @SuppressWarnings("unchecked")
    @Test
    void deletesAllSessionsByPrincipalName() {
        var repository = mock(FindByIndexNameSessionRepository.class);
        var sessionId = "session-1";
        var session = mock(Session.class);
        when(repository.findByPrincipalName("00000000-0000-0000-0000-000000000001"))
                .thenReturn(Map.of(sessionId, session));

        var invalidator = new ZijaSessionInvalidator(repository);
        invalidator.invalidateAllForAccount(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        verify(repository).deleteById(sessionId);
    }
}
