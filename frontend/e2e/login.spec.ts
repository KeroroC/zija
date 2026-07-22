import { expect, test } from "@playwright/test";
import { ensureBootstrapped, loginViaUi, owner } from "./helpers";

test("login succeeds with owner credentials and rejects bad password uniformly", async ({ page }) => {
  await ensureBootstrapped(page);
  const originalSession = (await page.context().cookies())
    .find((cookie) => cookie.name === "ZIJA_SESSION")?.value;
  expect(originalSession).toBeTruthy();

  await page.getByRole("button", { name: "登出" }).click();
  await expect(page).toHaveURL(/login/);

  await page.goto("/login");
  await page.locator("input").nth(0).fill(owner.username);
  await page.locator("input").nth(1).fill("wrong-password");
  await page.locator(".login-btn").click();
  await expect(page.getByText("用户名或密码错误")).toBeVisible();

  const rateLimitedUsername = `missing-${Date.now()}`;
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    await page.locator("input").nth(0).fill(rateLimitedUsername);
    await page.locator("input").nth(1).fill("wrong-password");
    await page.locator(".login-btn").click();
    const expectedMessage = attempt < 5
      ? "用户名或密码错误"
      : "尝试过多，请稍后再试";
    await expect(page.locator(".el-message__content")
      .filter({ hasText: expectedMessage })
      .last()).toBeVisible();
  }

  await loginViaUi(page, owner.username, owner.password);
  await expect(page.getByText("所有者")).toBeVisible();
  const newSession = (await page.context().cookies())
    .find((cookie) => cookie.name === "ZIJA_SESSION")?.value;
  expect(newSession).toBeTruthy();
  expect(newSession).not.toBe(originalSession);
});
