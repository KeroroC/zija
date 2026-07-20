package com.zija;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class ZijaSessionInvalidator {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public ZijaSessionInvalidator(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository
    ) {
        this.sessionRepository = sessionRepository;
    }

    public void invalidateAllForAccount(UUID accountId) {
        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(accountId.toString());
        for (String sessionId : sessions.keySet()) {
            sessionRepository.deleteById(sessionId);
        }
    }
}
