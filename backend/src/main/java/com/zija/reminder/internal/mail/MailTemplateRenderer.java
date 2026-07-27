package com.zija.reminder.internal.mail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 纯字符串模板渲染器，将邮件 HTML 模板与数据模型合并。
 * <p>
 * 不依赖 Thymeleaf 等模板引擎，使用简单的 {@code {var}} 占位符替换。
 */
@Component
public class MailTemplateRenderer {

    /**
     * 渲染每日提醒摘要邮件。
     *
     * @param model 数据模型，包含以下键：
     *              <ul>
     *                <li>{@code householdName} — 家庭名称</li>
     *                <li>{@code expiryTasks} — 到期任务列表，每项含 {@code title} 和 {@code dueAt}</li>
     *                <li>{@code lowStockTasks} — 低库存任务列表，每项含 {@code title} 和 {@code dueAt}</li>
     *                <li>{@code link} — 详情页链接</li>
     *              </ul>
     * @return 渲染后的 HTML 字符串
     */
    @SuppressWarnings("unchecked")
    public String renderDigest(Map<String, Object> model) {
        String template = readTemplate("mail/digest.html");
        String householdName = (String) model.getOrDefault("householdName", "");
        String link = (String) model.getOrDefault("link", "");
        List<Map<String, String>> expiryTasks = (List<Map<String, String>>) model.getOrDefault("expiryTasks", List.of());
        List<Map<String, String>> lowStockTasks = (List<Map<String, String>>) model.getOrDefault("lowStockTasks", List.of());

        String expirySection = buildTaskSection("即将到期", expiryTasks);
        String lowStockSection = buildTaskSection("库存不足", lowStockTasks);

        return template
                .replace("{householdName}", householdName)
                .replace("{expirySection}", expirySection)
                .replace("{lowStockSection}", lowStockSection)
                .replace("{link}", link);
    }

    /**
     * 渲染紧急提醒邮件。
     *
     * @param model 数据模型，包含以下键：
     *              <ul>
     *                <li>{@code title} — 提醒标题</li>
     *                <li>{@code severity} — 紧急度</li>
     *                <li>{@code link} — 详情页链接</li>
     *              </ul>
     * @return 渲染后的 HTML 字符串
     */
    public String renderUrgent(Map<String, Object> model) {
        String template = readTemplate("mail/urgent.html");
        String title = (String) model.getOrDefault("title", "");
        String severity = (String) model.getOrDefault("severity", "");
        String link = (String) model.getOrDefault("link", "");

        return template
                .replace("{title}", title)
                .replace("{severity}", severity)
                .replace("{link}", link);
    }

    private String readTemplate(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read mail template: " + path, e);
        }
    }

    private String buildTaskSection(String heading, List<Map<String, String>> tasks) {
        if (tasks.isEmpty()) {
            return "";
        }
        String items = tasks.stream()
                .map(task -> "<li>" + task.getOrDefault("title", "") + "（" + task.getOrDefault("dueAt", "") + "）</li>")
                .collect(Collectors.joining("\n    "));
        return """
                <h3 style="color: #2E5D4B;">%s</h3>
                  <ul style="padding-left: 20px;">
                    %s
                  </ul>
                """.formatted(heading, items);
    }
}
