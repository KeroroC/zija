import { expect, test } from "@playwright/test";
import { ensureBootstrapped, owner } from "./helpers";

test("owner edits display name from profile and header reflects it", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.locator(".user-trigger").click();
  await page.locator(".el-dropdown-menu__item").filter({ hasText: "个人资料" }).click();
  await expect(page.getByRole("heading", { name: "个人资料" })).toBeVisible();

  const nameInput = page.locator(".name-edit input");
  await expect(nameInput).toHaveValue(owner.displayName);

  await nameInput.fill("E2E所有者2");
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.locator(".user-trigger")).toContainText("E2E所有者2");

  // Revert so later specs see the canonical owner display name.
  await nameInput.fill(owner.displayName);
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.locator(".user-trigger")).toContainText(owner.displayName);
});
