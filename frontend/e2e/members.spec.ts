import { expect, test } from "@playwright/test";
import { e2eBaseURL, ensureBootstrapped, loginViaUi, owner } from "./helpers";

test("owner manages a member and transfers ownership with role boundaries", async ({ browser, page }) => {
  await ensureBootstrapped(page);
  const username = `e2e-managed-${Date.now()}`;
  const password = "Passw0rd!";

  await page.goto("/members");
  await expect(page.getByRole("main").getByText("成员管理")).toBeVisible();
  await page.getByTestId("create-invite").click();
  await page.getByTestId("confirm-invite").click();
  const href = await page.getByTestId("invite-link").innerText();

  const memberContext = await browser.newContext({ baseURL: e2eBaseURL });
  const memberPage = await memberContext.newPage();
  await memberPage.goto(href);
  await expect(memberPage.getByRole("heading", { name: "加入家庭" })).toBeVisible();
  const inputs = memberPage.locator("input");
  await inputs.nth(0).fill(username);
  await inputs.nth(1).fill(password);
  await inputs.nth(2).fill("E2E受管成员");
  await memberPage.getByRole("button", { name: "加入" }).click();
  await expect(memberPage).toHaveURL(/\/$/);
  await memberContext.close();

  await page.goto("/members");
  const memberRow = page.locator("tbody tr", { hasText: username });
  await expect(memberRow).toBeVisible();

  const peerUsername = `e2e-peer-${Date.now()}`;
  await page.getByTestId("create-invite").click();
  await page.getByTestId("confirm-invite").click();
  const peerHref = await page.getByTestId("invite-link").innerText();
  const peerContext = await browser.newContext({ baseURL: e2eBaseURL });
  const peerPage = await peerContext.newPage();
  await peerPage.goto(peerHref);
  const peerInputs = peerPage.locator("input");
  await peerInputs.nth(0).fill(peerUsername);
  await peerInputs.nth(1).fill(password);
  await peerInputs.nth(2).fill("E2E第三方成员");
  await peerPage.getByRole("button", { name: "加入" }).click();
  await expect(peerPage).toHaveURL(/\/$/);
  await peerContext.close();
  await page.goto("/members");

  await memberRow.getByRole("button", { name: "设为管理员" }).click();
  await expect(memberRow.getByText("管理员", { exact: true })).toBeVisible();

  const adminContext = await browser.newContext({ baseURL: e2eBaseURL });
  const adminPage = await adminContext.newPage();
  await loginViaUi(adminPage, username, password);
  await adminPage.goto("/members");
  const ownerRowForAdmin = adminPage.locator("tbody tr", { hasText: owner.username });
  const selfRowForAdmin = adminPage.locator("tbody tr", { hasText: username });
  const peerRowForAdmin = adminPage.locator("tbody tr", { hasText: peerUsername });
  await expect(ownerRowForAdmin.getByRole("button")).toHaveCount(0);
  await expect(selfRowForAdmin.getByRole("button")).toHaveCount(0);
  await expect(peerRowForAdmin.getByRole("button", { name: "停用" })).toBeVisible();
  await expect(peerRowForAdmin.getByRole("button", { name: "设为管理员" })).toHaveCount(0);
  await adminContext.close();

  await memberRow.getByRole("button", { name: "取消管理员" }).click();
  await expect(memberRow.getByText("成员", { exact: true })).toBeVisible();
  await memberRow.getByRole("button", { name: "停用" }).click();
  await expect(memberRow.getByText("已停用", { exact: true })).toBeVisible();
  await expect(memberRow.getByRole("button", { name: "转移所有权" })).toHaveCount(0);
  await memberRow.getByRole("button", { name: "启用" }).click();
  await expect(memberRow.getByText("活跃", { exact: true })).toBeVisible();

  await memberRow.getByRole("button", { name: "转移所有权" }).click();
  await page.getByTestId("confirm-transfer").click();
  await expect(page).toHaveURL(/\/login/);

  await loginViaUi(page, username, password);
  await page.goto("/members");
  const formerOwnerRow = page.locator("tbody tr", { hasText: owner.username });
  await expect(formerOwnerRow.getByText("管理员", { exact: true })).toBeVisible();
  await formerOwnerRow.getByRole("button", { name: "转移所有权" }).click();
  await page.getByTestId("confirm-transfer").click();
  await expect(page).toHaveURL(/\/login/);

  await loginViaUi(page, owner.username, owner.password);
  await expect(page.getByText("所有者")).toBeVisible();
});
