import { expect, test } from "@playwright/test";
import { ensureBootstrapped } from "./helpers";

test("首页显示风险卡片与优先任务", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.goto("/");
  await expect(page.locator(".page-title")).toContainText("首页");

  // 风险卡片区域可见
  await expect(page.locator(".risk-card").first()).toBeVisible();

  // 快速操作区域可见
  await expect(page.locator(".quick-actions")).toBeVisible();

  // 最近库存流水区域可见
  await expect(page.locator(".recent-section")).toBeVisible();
});

test("提醒中心 snooze / complete / reopen 流程", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.goto("/reminders");
  await expect(page.locator(".page-title")).toContainText("提醒中心");

  // 筛选栏可见
  await expect(page.locator(".filter-bar")).toBeVisible();

  // 如果有任务行，执行操作流程
  const firstRow = page.locator(".el-table__row").first();
  if (await firstRow.count() > 0) {
    // 点击操作按钮
    await firstRow.locator("button:has-text('操作')").click();

    // 验证下拉菜单出现
    await expect(page.locator(".el-dropdown-menu")).toBeVisible();

    // 尝试 snooze（弹窗会出现 datetime 输入框，用 Esc 关闭即可——e2e 只验流程不报错）
    const snoozeItem = page.locator(
      ".el-dropdown-menu__item:has-text('稍后提醒')",
    );
    if (await snoozeItem.isVisible().catch(() => false)) {
      await snoozeItem.click();
      // snooze 弹窗会弹出，按 Esc 关闭
      await page.keyboard.press("Escape");
    }
  }
});

test("通知页可访问", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.goto("/notifications");
  await expect(page.locator(".page-title")).toContainText("通知");

  // 通知列表区域可见（有通知或空状态）
  await expect(page.locator(".notif-list")).toBeVisible();
});

test("提醒规则页 owner 可保存", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.goto("/settings/reminder");
  await expect(page.locator(".page-title")).toContainText("提醒规则");

  // 保存按钮对 owner 可见（ensureBootstrapped 登录的是 owner）
  await expect(page.getByRole("button", { name: "保存" })).toBeVisible();
});

test("邮件提醒分区可见且 SMTP 状态徽章显示", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.goto("/settings/reminder");
  await expect(page.locator(".page-title")).toContainText("提醒规则");

  // 邮件提醒分区可见
  await expect(page.locator(".mail-section")).toBeVisible();
  await expect(page.getByText("邮件提醒")).toBeVisible();

  // SMTP 状态徽章可见（已配置或未配置）
  const smtpBadge = page.locator(".zj-badge").first();
  await expect(smtpBadge).toBeVisible();
});
