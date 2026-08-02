import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const owner = {
  householdName: "E2E 家庭",
  username: "e2e-owner",
  password: "Passw0rd!",
  displayName: "E2E所有者"
};

export const e2eBaseURL = process.env.ZIJA_WEB_URL ?? "http://127.0.0.1:8088";

async function waitForAppReady(page: Page): Promise<void> {
  await page.waitForFunction(() => {
    const path = window.location.pathname;
    return path === "/" || path === "/bootstrap" || path === "/login"
      || path.startsWith("/members") || path.startsWith("/profile")
      || path.startsWith("/invitation") || path.startsWith("/owner-recovery");
  });
  // Wait until shell or auth form is rendered after Vue router guards settle.
  await Promise.race([
    page.getByRole("heading", { name: "系统状态" }).waitFor({ state: "visible", timeout: 15_000 }),
    page.getByRole("heading", { name: "初始化你的家庭" }).waitFor({ state: "visible", timeout: 15_000 }),
    page.locator(".auth-brand-cn").waitFor({ state: "visible", timeout: 15_000 }),
    page.locator(".user-trigger").waitFor({ state: "visible", timeout: 15_000 })
  ]).catch(() => undefined);
}

export async function bootstrapViaUi(page: Page, user = owner): Promise<void> {
  await page.goto("/");
  await waitForAppReady(page);
  if (!page.url().includes("/bootstrap")) {
    await page.goto("/bootstrap");
  }
  await expect(page.getByRole("heading", { name: "初始化你的家庭" })).toBeVisible();
  const inputs = page.locator("input");
  await inputs.nth(0).fill(user.householdName);
  await inputs.nth(1).fill(user.username);
  await inputs.nth(2).fill(user.password);
  await inputs.nth(3).fill(user.displayName);
  await page.getByRole("button", { name: "创建家庭" }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("heading", { name: "首页" })).toBeVisible();
  await expect(page.getByText("E2E所有者")).toBeVisible();
}

export async function loginViaUi(
  page: Page,
  username: string,
  password: string
): Promise<void> {
  await page.goto("/login");
  await expect(page.locator(".auth-brand-cn")).toBeVisible();
  const inputs = page.locator("input");
  await inputs.nth(0).fill(username);
  await inputs.nth(1).fill(password);
  await page.locator(".login-btn").click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.locator(".user-trigger")).toBeVisible();
}

export async function ensureBootstrapped(page: Page): Promise<void> {
  await page.goto("/");
  await waitForAppReady(page);

  if (page.url().includes("/bootstrap")
    || await page.getByRole("heading", { name: "初始化你的家庭" }).isVisible().catch(() => false)) {
    await bootstrapViaUi(page);
    return;
  }

  if (await page.locator(".user-trigger").isVisible().catch(() => false)) {
    return;
  }

  await loginViaUi(page, owner.username, owner.password);
}

export async function readCsrf(request: APIRequestContext): Promise<string> {
  const response = await request.get("/api/v1/auth/csrf");
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(typeof body.token).toBe("string");
  return body.token as string;
}
