package com.zija.reminder.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.reminder.internal.persistence.NotificationEntity;
import com.zija.reminder.internal.persistence.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class NotificationIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired NotificationService notificationService;
    @Autowired NotificationMapper notificationMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbc;

    private UUID householdId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE reminder_notification, reminder_task, reminder_household_rule, reminder_processed_event, reminder_event_dead_letter, household, account RESTART IDENTITY CASCADE");
        var hh = new HouseholdEntity(); hh.setSingletonKey((short) 1); hh.setId(UUID.randomUUID()); hh.setName("T"); hh.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(hh); householdId = hh.getId();
    }

    private UUID seedNotif(boolean read) {
        var n = new NotificationEntity();
        n.setId(UUID.randomUUID()); n.setHouseholdId(householdId);
        n.setScope("TASK_CREATED"); n.setTitle("测试通知");
        n.setRead(read); n.setCreatedAt(OffsetDateTime.now());
        notificationMapper.insert(n); return n.getId();
    }

    @Test
    void pageReturnsAllAndUnreadFilter() {
        seedNotif(false); seedNotif(false); seedNotif(true);
        var all = notificationService.page(householdId, 1, 20, false);
        assertThat(all.items()).hasSize(3);
        var unread = notificationService.page(householdId, 1, 20, true);
        assertThat(unread.items()).hasSize(2);
    }

    @Test
    void unreadCount() {
        seedNotif(false); seedNotif(false); seedNotif(true);
        assertThat(notificationService.unreadCount(householdId)).isEqualTo(2);
    }

    @Test
    void markOneRead() {
        var id = seedNotif(false);
        notificationService.markOneRead(householdId, id);
        assertThat(notificationMapper.selectById(id).getRead()).isTrue();
    }

    @Test
    void markAllRead() {
        seedNotif(false); seedNotif(false);
        notificationService.markAllRead(householdId);
        assertThat(notificationService.unreadCount(householdId)).isEqualTo(0);
    }
}
