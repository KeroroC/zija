package com.zija.file.internal;

import com.zija.file.FileApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 附件回收站清除任务：保留期满后物理删除（删元数据 + 卷上对象）。
 *
 * <p>保留期由 {@code zija.file.retention-days} 配置（默认 30 天）；
 * 调度时区与其它定时任务一致（{@code zija.schedule.zone}，默认 Asia/Shanghai）。
 * 测试环境中 cron 必须为 {@code "-"}（见 NoBackgroundSchedulingInTestsTest），
 * 测试直接调用 {@link FileApi#purgeExpired}。</p>
 */
@Component
class FilePurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(FilePurgeScheduler.class);

    private final FileApi fileApi;
    private final int retentionDays;
    private final ZoneId zone;

    FilePurgeScheduler(
            FileApi fileApi,
            @Value("${zija.file.retention-days:30}") int retentionDays,
            @Value("${zija.schedule.zone:Asia/Shanghai}") String zone
    ) {
        this.fileApi = fileApi;
        this.retentionDays = retentionDays;
        this.zone = ZoneId.of(zone);
    }

    @Scheduled(cron = "${zija.schedule.file-purge:0 20 3 * * *}",
               zone = "${zija.schedule.zone:Asia/Shanghai}")
    public void purgeExpiredAttachments() {
        OffsetDateTime cutoff = OffsetDateTime.now(zone).minusDays(retentionDays);
        int purged = fileApi.purgeExpired(cutoff);
        if (purged > 0) {
            log.info("附件保留期清除完成: purged={} cutoff={}", purged, cutoff);
        }
    }
}
