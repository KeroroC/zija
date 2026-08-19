package com.zija.ai.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KnowledgeQaServiceExecutionUnavailableTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void executionUnavailableAcceptsNullScopeWithoutThrowing() {
        var service = new KnowledgeQaService(null, null, null, null, null, null, null);

        assertThatCode(() -> service.executionUnavailable(HOUSEHOLD_ID, "怎么清洁？", null))
                .doesNotThrowAnyException();
    }

    @Test
    void executionUnavailableWithNullScopeReturnsModelUnavailable() {
        var service = new KnowledgeQaService(null, null, null, null, null, null, null);

        var answer = service.executionUnavailable(HOUSEHOLD_ID, "怎么清洁？", null);

        assertThat(answer.reasonCode()).isEqualTo(KnowledgeQaService.REASON_MODEL_UNAVAILABLE);
        assertThat(answer.modelAvailable()).isFalse();
    }
}
