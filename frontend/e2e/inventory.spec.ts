import { expect, test } from "@playwright/test";
import { e2eBaseURL, ensureBootstrapped, readCsrf } from "./helpers";

test("库存主链路：入库→领用→报损→移位→盘点→冲正", async ({ browser, page }) => {
  await ensureBootstrapped(page);

  const ts = Date.now();
  const catName = `e2e-inv-cat-${ts}`;
  const unitName = `e2e-inv-unit-${ts}`;
  const locAName = `e2e-locA-${ts}`;
  const locBName = `e2e-locB-${ts}`;
  const itemName = `e2e-inv-item-${ts}`;

  // ─── 1. Create category ───
  await page.goto("/settings/catalog");
  await expect(page.getByRole("heading", { name: "目录设置" })).toBeVisible();
  await page.getByRole("button", { name: "添加根分类" }).click();
  const categoryDialog = page.locator(".el-dialog", { hasText: "添加根分类" });
  await categoryDialog
    .locator(".el-form-item", { hasText: "名称" })
    .locator("input")
    .fill(catName);
  await categoryDialog.getByRole("button", { name: "确定" }).click();
  await expect(categoryDialog).not.toBeVisible();
  await expect(page.locator(".tree-node", { hasText: catName })).toBeVisible();

  // ─── 2. Create unit (decimal scale 0) ───
  await page.getByRole("tab", { name: "单位" }).click();
  await page.getByPlaceholder("新单位名称").fill(unitName);
  const decimalInput = page.getByPlaceholder("小数位");
  await decimalInput.click();
  await decimalInput.press("Control+a");
  await decimalInput.press("Backspace");
  await decimalInput.type("0");
  await page.getByRole("button", { name: "添加" }).click();
  await expect(page.locator("tbody tr", { hasText: unitName })).toBeVisible();

  // ─── 3. Create two root locations (A and B) ───
  await page.goto("/locations");
  await expect(page.getByRole("heading", { name: "位置管理" })).toBeVisible();

  async function createRootLocation(name: string) {
    await page.getByRole("button", { name: "新增根位置" }).click();
    const dialog = page.locator(".el-dialog", { hasText: "位置" });
    await dialog.locator("input").fill(name);
    await dialog.getByRole("button", { name: "确定" }).click();
    await expect(page.locator(".tree-node", { hasText: name })).toBeVisible();
  }

  await createRootLocation(locAName);
  await createRootLocation(locBName);

  // ─── 4. Create item ───
  await page.goto("/items");
  await expect(page.getByRole("heading", { name: "物品资料" })).toBeVisible();
  await page.getByRole("button", { name: "新建物品" }).click();
  await page.getByPlaceholder("请输入物品名称").fill(itemName);

  // Select management type (耐用品)
  await page
    .locator(".el-form-item", { hasText: "管理类型" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: "耐用品" })
    .click();

  // Select unit
  await page
    .locator(".el-form-item", { hasText: "单位" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: unitName })
    .click();

  // Select category via tree-select
  await page
    .locator(".el-form-item", { hasText: "分类" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-tree-select__popper:visible")
    .locator(".el-select-dropdown__item", { hasText: catName })
    .click();

  await page.getByRole("button", { name: "创建" }).click();
  await expect(page.locator(".el-drawer:visible")).toHaveCount(0);
  await expect(page.locator("tbody tr", { hasText: itemName })).toBeVisible();

  // ─── 5. Navigate to inventory ───
  await page.goto("/inventory");
  await expect(
    page.getByRole("heading", { name: "库存管理" }),
  ).toBeVisible();

  // ─── 6. Inbound: new lot, quantity=5, to location A ───
  await page.locator('[data-testid="btn-inbound"]').click();
  const inboundDialog = page.locator(".el-dialog", { hasText: "入库" });
  await expect(inboundDialog).toBeVisible();

  // Select item
  await inboundDialog
    .locator(".el-form-item", { hasText: "物品" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: itemName })
    .click();

  // Set quantity to 5
  const inboundQty = inboundDialog.locator(".el-input-number input");
  await inboundQty.click();
  await inboundQty.press("Control+a");
  await inboundQty.press("Backspace");
  await inboundQty.type("5");

  // Select location A
  await inboundDialog
    .locator(".el-form-item", { hasText: "入库位置" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-tree-select__popper:visible")
    .locator(".el-select-dropdown__item", { hasText: locAName })
    .click();

  // Step 0 → Step 1
  await inboundDialog.getByRole("button", { name: "下一步" }).click();

  // Confirm inbound
  await inboundDialog.getByRole("button", { name: "确认入库" }).click();
  await expect(inboundDialog).not.toBeVisible();
  await expect(page.getByText("入库成功")).toBeVisible();

  // ─── 7. Verify: stock=5, lots=1, movement INBOUND ───
  await page.getByRole("tab", { name: "当前库存" }).click();
  const stockRow = page.locator('.el-tab-pane').nth(0).locator("tbody tr", { hasText: itemName });
  await expect(stockRow).toBeVisible();
  await expect(stockRow.getByText(/^5\s/)).toBeVisible();

  await page.getByRole("tab", { name: "批次" }).click();
  const lotsPane = page.locator('.el-tab-pane').nth(1);
  await expect(lotsPane.locator("tbody tr").first()).toBeVisible();
  const lotCount = await lotsPane.locator("tbody tr").count();
  expect(lotCount).toBeGreaterThanOrEqual(1);

  await page.getByRole("tab", { name: "流水" }).click();
  const movementsPane = page.locator('.el-tab-pane').nth(2);
  await expect(
    movementsPane.locator(".el-tag", { hasText: "入库" }).first(),
  ).toBeVisible();

  // ─── 8. Consume 2 from location A ───
  await page.locator('[data-testid="btn-consume"]').click();
  const consumeDialog = page.locator(".el-dialog", { hasText: "领用" });
  await expect(consumeDialog).toBeVisible();

  // Step 0: select item
  await consumeDialog
    .locator(".el-form-item", { hasText: "物品" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: itemName })
    .click();
  await consumeDialog.getByRole("button", { name: "下一步" }).click();

  // Step 1: select the first stock position row
  await expect(consumeDialog.locator("tbody tr").first()).toBeVisible();
  await consumeDialog.locator("tbody tr").first().click();
  await consumeDialog.getByRole("button", { name: "下一步" }).click();

  // Step 2: set quantity to 2
  const consumeQty = consumeDialog.locator(".el-input-number input");
  await consumeQty.click();
  await consumeQty.press("Control+a");
  await consumeQty.press("Backspace");
  await consumeQty.type("2");
  await consumeDialog.getByRole("button", { name: "确认领用" }).click();
  await expect(consumeDialog).not.toBeVisible();
  await expect(page.getByText("领用成功")).toBeVisible();

  // ─── 9. Verify: stock=3 ───
  await page.getByRole("tab", { name: "当前库存" }).click();
  await expect(
    page.locator('.el-tab-pane').nth(0).locator("tbody tr", { hasText: itemName }).getByText(/^3\s/),
  ).toBeVisible();

  // ─── 10. Loss 1, reason=过期 ───
  await page.locator('[data-testid="btn-loss"]').click();
  const lossDialog = page.locator(".el-dialog", { hasText: "报损" });
  await expect(lossDialog).toBeVisible();

  // Step 0: select the first stock position row
  await expect(lossDialog.locator("tbody tr").first()).toBeVisible();
  await lossDialog.locator("tbody tr").first().click();
  await lossDialog.getByRole("button", { name: "下一步" }).click();

  // Step 1: quantity=1, reason=过期
  const lossQty = lossDialog.locator(".el-input-number input");
  await lossQty.click();
  await lossQty.press("Control+a");
  await lossQty.press("Backspace");
  await lossQty.type("1");
  await lossDialog.getByPlaceholder("请输入报损原因").fill("过期");
  await lossDialog.getByRole("button", { name: "确认报损" }).click();
  await expect(lossDialog).not.toBeVisible();
  await expect(page.getByText("报损成功")).toBeVisible();

  // ─── 11. Verify: stock=2 ───
  await page.getByRole("tab", { name: "当前库存" }).click();
  await expect(
    page.locator('.el-tab-pane').nth(0).locator("tbody tr", { hasText: itemName }).getByText(/^2\s/),
  ).toBeVisible();

  // ─── 12. Transfer 1 from A to B ───
  await page.locator('[data-testid="btn-transfer"]').click();
  const transferDialog = page.locator(".el-dialog", { hasText: "移位" });
  await expect(transferDialog).toBeVisible();

  // Step 0: select the first stock position row
  await expect(transferDialog.locator("tbody tr").first()).toBeVisible();
  await transferDialog.locator("tbody tr").first().click();
  await transferDialog.getByRole("button", { name: "下一步" }).click();

  // Step 1: select target location B, quantity=1
  await transferDialog
    .locator(".el-form-item", { hasText: "目标位置" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-tree-select__popper:visible")
    .locator(".el-select-dropdown__item", { hasText: locBName })
    .click();

  const transferQty = transferDialog.locator(".el-input-number input");
  await transferQty.click();
  await transferQty.press("Control+a");
  await transferQty.press("Backspace");
  await transferQty.type("1");

  await transferDialog.getByRole("button", { name: "确认移位" }).click();
  await expect(transferDialog).not.toBeVisible();
  await expect(page.getByText("移位成功")).toBeVisible();

  // ─── 13. Verify: A=1, B=1 ───
  await page.getByRole("tab", { name: "当前库存" }).click();
  await expect(
    page.locator('.el-tab-pane').nth(0).locator("tbody tr", { hasText: locAName }).getByText(/^1\s/),
  ).toBeVisible();
  await expect(
    page.locator('.el-tab-pane').nth(0).locator("tbody tr", { hasText: locBName }).getByText(/^1\s/),
  ).toBeVisible();

  // ─── 14. Stocktake on location A: actual=0, reason=遗失 ───
  await page.locator('[data-testid="btn-stocktake"]').click();
  const stocktakeDialog = page.locator(".el-dialog", { hasText: "盘点" });
  await expect(stocktakeDialog).toBeVisible();

  // Step 0: select location A, create stocktake
  await stocktakeDialog
    .locator(".el-form-item", { hasText: "盘点位置" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-tree-select__popper:visible")
    .locator(".el-select-dropdown__item", { hasText: locAName })
    .click();
  await stocktakeDialog
    .getByRole("button", { name: "创建盘点" })
    .click();

  // Step 1: set actual=0 for the first row, fill reason
  await expect(stocktakeDialog.locator("tbody tr").first()).toBeVisible();
  const actualInput = stocktakeDialog
    .locator("tbody .el-input-number input")
    .first();
  await actualInput.click();
  await actualInput.press("Control+a");
  await actualInput.press("Backspace");
  await actualInput.type("0");

  // Fill reason for the discrepancy
  await stocktakeDialog
    .locator("tbody .el-input__inner[placeholder='差异必填']")
    .first()
    .fill("遗失");

  // Save draft → goes to step 2
  await stocktakeDialog.getByRole("button", { name: "保存" }).click();
  await expect(page.getByText("盘点草稿已保存")).toBeVisible();

  // Step 2: confirm stocktake
  await stocktakeDialog.getByRole("button", { name: "确认盘点" }).click();
  const confirmBox = page.locator(".el-message-box");
  await expect(confirmBox).toBeVisible();
  await confirmBox.getByRole("button", { name: "确认" }).click();
  await expect(stocktakeDialog).not.toBeVisible();

  // ─── 15. Verify: ADJUSTMENT movement, A=0 ───
  await page.getByRole("tab", { name: "流水" }).click();
  const movementsPane2 = page.locator('.el-tab-pane').nth(2);
  await expect(
    movementsPane2.locator(".el-tag", { hasText: "调整" }).first(),
  ).toBeVisible();

  await page.getByRole("tab", { name: "当前库存" }).click();
  // After stocktake adjusted to 0, location A should show quantity 0
  await expect(
    page.locator('.el-tab-pane').nth(0).locator("tbody tr", { hasText: locAName }).getByText(/^0\s/),
  ).toBeVisible();

  // ─── 16. Owner: reverse a CONSUME movement ───
  await page.getByRole("tab", { name: "流水" }).click();
  const movementsPane3 = page.locator('.el-tab-pane').nth(2);
  // Find a CONSUME row that has not been reversed
  const consumeRow = movementsPane3.locator("tbody tr", { hasText: "领用" }).first();
  await consumeRow.click();

  const movementDrawer = page.locator(".el-drawer:visible");
  await expect(movementDrawer).toBeVisible();
  await expect(movementDrawer.getByText("冲正此流水")).toBeVisible();
  await movementDrawer.getByRole("button", { name: "冲正此流水" }).click();

  // Confirm reversal
  await page
    .locator(".el-message-box")
    .getByRole("button", { name: "确定冲正" })
    .click();
  await expect(page.getByText("冲正成功")).toBeVisible();

  // ─── 17. Verify: REVERSAL movement ───
  await expect(
    movementsPane3.locator(".el-tag", { hasText: "冲销" }).first(),
  ).toBeVisible();

  // ─── 18. Create a member for permission check ───
  const memberUsername = `e2e-inv-member-${ts}`;
  const memberPassword = "Passw0rd!";

  await page.goto("/members");
  await expect(page.getByText("成员管理")).toBeVisible();
  await page.getByTestId("create-invite").click();
  await page.getByTestId("confirm-invite").click();
  const inviteHref = await page.getByTestId("invite-link").innerText();

  const memberContext = await browser.newContext({ baseURL: e2eBaseURL });
  const memberPage = await memberContext.newPage();
  await memberPage.goto(inviteHref);
  await expect(
    memberPage.getByRole("heading", { name: "加入家庭" }),
  ).toBeVisible();
  const memberInputs = memberPage.locator("input");
  await memberInputs.nth(0).fill(memberUsername);
  await memberInputs.nth(1).fill(memberPassword);
  await memberInputs.nth(2).fill("E2E库存成员");
  await memberPage.getByRole("button", { name: "加入" }).click();
  await expect(memberPage).toHaveURL(/\/$/);

  // ─── 19. Member: reverse button NOT visible ───
  await memberPage.goto("/inventory");
  await expect(
    memberPage.getByRole("heading", { name: "库存管理" }),
  ).toBeVisible();
  await memberPage.getByRole("tab", { name: "流水" }).click();
  const memberMovementsPane = memberPage.locator('.el-tab-pane').nth(2);
  await expect(memberMovementsPane.locator("tbody tr").first()).toBeVisible();
  await memberMovementsPane.locator("tbody tr").first().click();

  const memberDrawer = memberPage.locator(".el-drawer:visible");
  await expect(memberDrawer).toBeVisible();
  // Member should NOT see the reverse button
  await expect(memberDrawer.getByText("冲正此流水")).not.toBeVisible();

  // ─── 20. Member: direct API call returns 403 ───
  const csrfToken = await readCsrf(memberPage.request);

  // Get a non-reversed movement ID
  const movementsResp = await memberPage.request.get(
    "/api/v1/inventory/movements?page=1&pageSize=1",
  );
  expect(movementsResp.ok()).toBeTruthy();
  const movementsData = await movementsResp.json();
  expect(movementsData.items.length).toBeGreaterThan(0);
  const movementId = movementsData.items[0].id as string;

  const reverseResp = await memberPage.request.post(
    `/api/v1/inventory/movements/${movementId}/reverse`,
    {
      headers: { "X-CSRF-TOKEN": csrfToken },
      data: { reason: null, memo: null },
    },
  );
  expect(reverseResp.status()).toBe(403);

  await memberContext.close();

  // ─── 21. Consistency check (Owner) ───
  // Switch back to owner's page (still logged in)
  await page.goto("/inventory");
  await expect(
    page.getByRole("heading", { name: "库存管理" }),
  ).toBeVisible();

  // Fetch consistency report via API using owner's session
  const consistencyResp = await page.request.get(
    "/api/v1/inventory/consistency-report",
  );
  expect(consistencyResp.ok()).toBeTruthy();
  const consistencyData = await consistencyResp.json();
  expect(consistencyData.discrepancies).toBeDefined();
  expect(Array.isArray(consistencyData.discrepancies)).toBe(true);
});
