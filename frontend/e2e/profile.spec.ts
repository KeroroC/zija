import { expect, test } from "@playwright/test";
import { ensureBootstrapped, owner } from "./helpers";

test("owner edits display name from profile and header reflects it", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.locator(".user-trigger").click();
  await page.locator(".el-dropdown-menu__item").filter({ hasText: "个人资料" }).click();
  await expect(page.getByRole("heading", { name: "个人资料" })).toBeVisible();

  const nameInput = page.locator(".name-edit input");
  await expect(nameInput).toHaveValue(owner.displayName);

  const renamed = `${owner.displayName}2`;
  try {
    await nameInput.fill(renamed);
    await page.getByRole("button", { name: "保存" }).click();
    await expect(page.locator(".user-trigger")).toContainText(renamed);
  } finally {
    // Always restore the canonical name so later specs (and long-lived dev
    // stacks) never see a poisoned display name, even if the rename failed.
    await page.goto("/profile");
    const restoreInput = page.locator(".name-edit input");
    await restoreInput.fill(owner.displayName);
    await page.getByRole("button", { name: "保存" }).click();
    await expect(page.locator(".user-trigger")).toContainText(owner.displayName);
  }
});
