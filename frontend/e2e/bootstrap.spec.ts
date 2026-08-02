import { expect, test } from "@playwright/test";
import { bootstrapViaUi, owner, readCsrf } from "./helpers";

test("bootstrap creates household and owner then lands on home", async ({ page }) => {
  // Fresh compose stack is uninitialized; if already initialized this test is skipped
  // by checking status first.
  const status = await page.request.get("/api/v1/household/status");
  const body = await status.json();
  test.skip(body.initialized === true, "household already initialized");

  const csrfBeforeBootstrap = await readCsrf(page.request);
  await bootstrapViaUi(page, owner);
  const csrfAfterBootstrap = await readCsrf(page.request);
  expect(csrfAfterBootstrap).not.toBe(csrfBeforeBootstrap);
  await expect(page.getByRole("heading", { name: "首页" })).toBeVisible();
  await expect(page.locator(".user-trigger")).toContainText("E2E所有者");
});
