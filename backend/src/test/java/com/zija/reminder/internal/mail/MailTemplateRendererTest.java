package com.zija.reminder.internal.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 邮件模板渲染器单元测试。
 */
class MailTemplateRendererTest {

    private final MailTemplateRenderer renderer = new MailTemplateRenderer();

    @Test
    void digestRendersHouseholdNameAndLink() {
        var html = renderer.renderDigest(Map.of(
                "householdName", "我家",
                "expiryTasks", List.of(),
                "lowStockTasks", List.of(),
                "link", "http://localhost:3000/"
        ));

        assertThat(html)
                .contains("我家")
                .contains("http://localhost:3000/")
                .contains("每日提醒摘要");
    }

    @Test
    void digestRendersExpiryTasks() {
        var html = renderer.renderDigest(Map.of(
                "householdName", "测试家庭",
                "expiryTasks", List.of(
                        Map.of("title", "牛奶将到期", "dueAt", "明天"),
                        Map.of("title", "面包将到期", "dueAt", "后天")
                ),
                "lowStockTasks", List.of(),
                "link", "http://example.com/"
        ));

        assertThat(html)
                .contains("牛奶将到期")
                .contains("明天")
                .contains("面包将到期")
                .contains("后天")
                .contains("即将到期");
    }

    @Test
    void digestRendersLowStockTasks() {
        var html = renderer.renderDigest(Map.of(
                "householdName", "测试家庭",
                "expiryTasks", List.of(),
                "lowStockTasks", List.of(
                        Map.of("title", "洗衣液不足", "dueAt", "剩余 1 瓶")
                ),
                "link", "http://example.com/"
        ));

        assertThat(html)
                .contains("洗衣液不足")
                .contains("剩余 1 瓶")
                .contains("库存不足");
    }

    @Test
    void digestRendersNoSectionsWhenEmpty() {
        var html = renderer.renderDigest(Map.of(
                "householdName", "空家庭",
                "expiryTasks", List.of(),
                "lowStockTasks", List.of(),
                "link", "http://example.com/"
        ));

        assertThat(html)
                .doesNotContain("即将到期")
                .doesNotContain("库存不足");
    }

    @Test
    void urgentRendersTitleAndSeverityAndLink() {
        var html = renderer.renderUrgent(Map.of(
                "title", "紧急事项",
                "severity", "URGENT",
                "link", "http://example.com/urgent"
        ));

        assertThat(html)
                .contains("紧急事项")
                .contains("URGENT")
                .contains("http://example.com/urgent")
                .contains("紧急提醒");
    }

    @Test
    void urgentRendersWithChineseSeverity() {
        var html = renderer.renderUrgent(Map.of(
                "title", "过期提醒",
                "severity", "紧急",
                "link", "http://example.com/"
        ));

        assertThat(html)
                .contains("过期提醒")
                .contains("紧急");
    }
}
