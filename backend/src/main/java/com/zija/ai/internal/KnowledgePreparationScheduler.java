package com.zija.ai.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 知识来源异步准备调度：默认每 10 秒认领一次到期来源（选择、手动重试、自动重试退避到期）。
 * 测试环境 cron 置 {@code "-"} 禁用（见 NoBackgroundSchedulingInTestsTest），
 * 直接调用 {@link KnowledgePreparationService#prepareDue} 覆盖。
 */
@Service
class KnowledgePreparationScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePreparationScheduler.class);

    private final KnowledgePreparationService preparationService;

    KnowledgePreparationScheduler(KnowledgePreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @Scheduled(cron = "${zija.schedule.knowledge-prepare:*/10 * * * * *}",
            zone = "${zija.schedule.zone:Asia/Shanghai}")
    public void scanDue() {
        int processed = preparationService.prepareDue(OffsetDateTime.now());
        if (processed > 0) {
            log.info("Knowledge preparation scan processed {} sources", processed);
        }
    }
}
