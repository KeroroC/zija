import { expect, test } from "@playwright/test";
import { e2eBaseURL, ensureBootstrapped, owner } from "./helpers";

test("owner creates invitation and new member redeems once", async ({ browser, page }) => {
  await ensureBootstrapped(page);
  const username = `e2e-invite-${Date.now()}`;

  await page.goto("/members");
  await expect(page.getByRole("main").getByText("成员管理")).toBeVisible();
  await page.getByTestId("create-invite").click();
  await page.getByTestId("confirm-invite").click();

  const inviteLink = page.getByTestId("invite-link");
  await expect(inviteLink).toBeVisible();
  const href = await inviteLink.innerText();
  expect(href).toContain("/invitation/redeem#token=");

  const memberContext = await browser.newContext({ baseURL: e2eBaseURL });
  const memberPage = await memberContext.newPage();
  await memberPage.goto(href);
  await expect(memberPage).toHaveURL(/\/invitation\/redeem$/);
  await expect(memberPage.getByRole("heading", { name: "加入家庭" })).toBeVisible();

  const inputs = memberPage.locator("input");
  await inputs.nth(0).fill(username);
  await inputs.nth(1).fill("Passw0rd!");
  await inputs.nth(2).fill("E2E成员");
  await memberPage.getByRole("button", { name: "加入" }).click();
  await expect(memberPage).toHaveURL(/\/$/);
  await expect(memberPage.getByText("成员", { exact: true })).toBeVisible();

  const secondContext = await browser.newContext({ baseURL: e2eBaseURL });
  const secondPage = await secondContext.newPage();
  await secondPage.goto(href);
  await expect(secondPage.getByText("邀请链接无效或已过期。")).toBeVisible();

  await memberContext.close();
  await secondContext.close();

  await page.goto("/members");
  await expect(page.getByRole("main").getByText(owner.username)).toBeVisible();
  await expect(page.getByRole("main").getByText(username)).toBeVisible();
});
