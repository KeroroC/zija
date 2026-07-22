import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { e2eBaseURL, ensureBootstrapped, loginViaUi, owner } from "./helpers";

test("owner recovery resets password and invalidates old credentials and sessions", async ({ browser, page }) => {
  test.setTimeout(90_000);
  await ensureBootstrapped(page);

  const viaDocker = process.env.ZIJA_RECOVERY_VIA_DOCKER === "1";
  test.skip(!viaDocker, "owner recovery e2e requires the Compose recovery command");

  const oldSessionContext = await browser.newContext({ baseURL: e2eBaseURL });
  const oldSessionPage = await oldSessionContext.newPage();
  await loginViaUi(oldSessionPage, owner.username, owner.password);

  let output = "";
  try {
    const project = process.env.ZIJA_COMPOSE_PROJECT ?? "zija-e2e";
    output = execFileSync("docker", [
      "compose", "-p", project, "exec", "-T", "app",
      "timeout", "30s", "java", "-jar", "/app/zija.jar",
      "--spring.main.web-application-type=none", "--zija.command=recover-owner"
    ], { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 });
  } catch (error) {
    const err = error as { stdout?: string; stderr?: string };
    output = `${err.stdout ?? ""}${err.stderr ?? ""}`;
  }

  const match = output.match(/#token=([A-Za-z0-9_-]+)/);
  expect(match, `recovery token not found in output: ${output}`).toBeTruthy();
  const token = match![1];

  // Use a fresh context so recovery page is not mixed with an authenticated shell state.
  await page.context().clearCookies();
  await page.goto(`/owner-recovery#token=${token}`);
  await expect(page.getByRole("heading", { name: "重置所有者密码" })).toBeVisible();
  await expect(page.getByText("请为所有者账户设置新密码。")).toBeVisible({ timeout: 15_000 });

  const inputs = page.locator(".recovery-page input");
  await inputs.nth(0).fill("N3wPassw0rd!");
  await inputs.nth(1).fill("N3wPassw0rd!");
  await page.getByRole("button", { name: "重置密码" }).click();
  await expect(page).toHaveURL(/\/login/, { timeout: 15_000 });

  const oldSessionResponse = await oldSessionContext.request.get("/api/v1/household/me");
  expect(oldSessionResponse.status()).toBe(401);

  const reusedTokenPage = await browser.newPage({ baseURL: e2eBaseURL });
  await reusedTokenPage.goto(`/owner-recovery#token=${token}`);
  await expect(reusedTokenPage.getByText("恢复链接无效或已过期。")).toBeVisible();
  await reusedTokenPage.close();

  await page.locator("input").nth(0).fill(owner.username);
  await page.locator("input").nth(1).fill(owner.password);
  await page.locator(".login-btn").click();
  await expect(page.getByText("用户名或密码错误")).toBeVisible();

  const recoveredPassword = "N3wPassw0rd!";
  await loginViaUi(page, owner.username, recoveredPassword);
  await expect(page.getByText("所有者")).toBeVisible();

  await page.goto("/profile");
  const profileInputs = page.locator("input");
  await profileInputs.nth(0).fill(recoveredPassword);
  await profileInputs.nth(1).fill(owner.password);
  await page.getByRole("button", { name: "修改密码" }).click();
  await expect(page).toHaveURL(/\/login/);
  await loginViaUi(page, owner.username, owner.password);
  await oldSessionContext.close();
});
