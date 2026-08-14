package com.zija.reminder.internal.mail;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.shared.ZijaMemberRole;
import com.zija.reminder.internal.LazyInit;
import com.zija.reminder.internal.exception.MailSettingVersionConflictException;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MailSettingService {

    private final MailSettingMapper mailSettingMapper;
    private final SystemApi systemApi;
    private final MailService mailService;

    MailSettingService(MailSettingMapper mailSettingMapper, SystemApi systemApi, MailService mailService) {
        this.mailSettingMapper = mailSettingMapper;
        this.systemApi = systemApi;
        this.mailService = mailService;
    }

    public record MailSettingView(
            UUID id,
            UUID householdId,
            boolean digestEnabled,
            String digestFrequency,
            boolean urgentEnabled,
            List<String> recipientRoles,
            OffsetDateTime lastDigestSentAt,
            int version,
            boolean smtpConfigured
    ) {}

    public record MailSettingUpdate(
            boolean digestEnabled,
            String digestFrequency,
            boolean urgentEnabled,
            List<String> recipientRoles,
            int version
    ) {}

    /** Lazy-initialize household default mail settings if not exists. */
    @Transactional
    public MailSettingView getOrCreate(UUID householdId) {
        var wrapper = new LambdaQueryWrapper<MailSettingEntity>()
                .eq(MailSettingEntity::getHouseholdId, householdId);
        MailSettingEntity e = LazyInit.getOrCreate(
                () -> mailSettingMapper.selectOne(wrapper),
                () -> createDefaultSetting(householdId),
                mailSettingMapper::insert);
        return toView(e);
    }

    private MailSettingEntity createDefaultSetting(UUID householdId) {
        var e = new MailSettingEntity();
        e.setId(UUID.randomUUID());
        e.setHouseholdId(householdId);
        e.setDigestEnabled(false);
        e.setDigestFrequency("DAILY");
        e.setUrgentEnabled(true);
        e.setRecipientRoles(List.of(ZijaMemberRole.OWNER));
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setVersion(0);
        return e;
    }

    /** Update mail settings with optimistic lock. */
    @Transactional
    public MailSettingView update(UUID householdId, MailSettingUpdate update) {
        validateUpdate(update);
        var current = mailSettingMapper.selectOne(new LambdaQueryWrapper<MailSettingEntity>()
                .eq(MailSettingEntity::getHouseholdId, householdId));
        if (current == null) {
            // lazy init first, then re-read
            getOrCreate(householdId);
            current = mailSettingMapper.selectOne(new LambdaQueryWrapper<MailSettingEntity>()
                    .eq(MailSettingEntity::getHouseholdId, householdId));
        }
        if (current.getVersion() != update.version()) {
            throw new MailSettingVersionConflictException();
        }
        current.setDigestEnabled(update.digestEnabled());
        current.setDigestFrequency(update.digestFrequency());
        current.setUrgentEnabled(update.urgentEnabled());
        current.setRecipientRoles(update.recipientRoles());
        current.setUpdatedAt(OffsetDateTime.now());
        int rows = mailSettingMapper.updateById(current); // optimistic lock
        if (rows == 0) throw new MailSettingVersionConflictException();
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "MAIL_SETTING_UPDATE", ZijaAuditOutcome.SUCCESS, householdId, null, null, null, null,
                Map.of("version", String.valueOf(update.version()))));
        return toView(current);
    }

    private void validateUpdate(MailSettingUpdate u) {
        if (u.digestFrequency() == null ||
            (!u.digestFrequency().equals("DAILY") && !u.digestFrequency().equals("WEEKLY"))) {
            throw new IllegalArgumentException("digestFrequency must be DAILY or WEEKLY");
        }
        if (u.recipientRoles() == null || u.recipientRoles().isEmpty()) {
            throw new IllegalArgumentException("recipientRoles must not be empty");
        }
    }

    private MailSettingView toView(MailSettingEntity e) {
        return new MailSettingView(
                e.getId(),
                e.getHouseholdId(),
                Boolean.TRUE.equals(e.getDigestEnabled()),
                e.getDigestFrequency(),
                Boolean.TRUE.equals(e.getUrgentEnabled()),
                e.getRecipientRoles(),
                e.getLastDigestSentAt(),
                e.getVersion() == null ? 0 : e.getVersion(),
                mailService.isConfigured()
        );
    }
}
