package com.zija;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 会话失效器。
 * <p>
 * 通过 Spring Session 的 {@link FindByIndexNameSessionRepository} 按账户 ID
 * 查询并删除该用户的所有活跃会话，适用于密码修改、账户禁用等需要强制登出的场景。
 */
@Component
public class ZijaSessionInvalidator {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public ZijaSessionInvalidator(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository
    ) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 使指定账户的所有活跃会话失效。
     *
     * @param accountId 要失效会话的账户 UUID
     */
    public void invalidateAllForAccount(UUID accountId) {
        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(accountId.toString());
        for (String sessionId : sessions.keySet()) {
            sessionRepository.deleteById(sessionId);
        }
    }
}
