import { expect, test } from "@playwright/test";
import { ensureBootstrapped } from "./helpers";

test("shows the live backend and PostgreSQL status after bootstrap", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.goto("/system");
  await expect(page.getByRole("heading", { name: "系统状态" })).toBeVisible();
  await expect(page.getByText("系统运行正常")).toBeVisible();
  await expect(page.getByText("PostgreSQL 已连接")).toBeVisible();
});
