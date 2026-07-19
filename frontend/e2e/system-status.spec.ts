import { expect, test } from "@playwright/test";

test("shows the live backend and PostgreSQL status", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "系统状态" }))
    .toBeVisible();
  await expect(page.getByText("系统运行正常")).toBeVisible();
  await expect(page.getByText("PostgreSQL 已连接")).toBeVisible();
  await expect(page.getByText("管理员")).toBeVisible();
});
