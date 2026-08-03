import { expect, test } from "@playwright/test";
import { ensureBootstrapped } from "./helpers";

test("报表完整链路：入库→投影→搜索→库存分布→导出→重建", async ({ page }) => {
  await ensureBootstrapped(page);

  const ts = Date.now();
  const catName = `e2e-rpt-cat-${ts}`;
  const unitName = `e2e-rpt-unit-${ts}`;
  const locName = `e2e-rpt-loc-${ts}`;
  const itemName = `e2e-rpt-item-${ts}`;

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

  // ─── 2. Create unit ───
  await page.getByRole("tab", { name: "单位" }).click();
  await page.getByPlaceholder("新单位名称").fill(unitName);
  const decimalInput = page.getByPlaceholder("小数位");
  await decimalInput.click();
  await decimalInput.press("Control+a");
  await decimalInput.press("Backspace");
  await decimalInput.type("0");
  await page.getByRole("button", { name: "添加" }).click();
  await expect(page.locator("tbody tr", { hasText: unitName })).toBeVisible();

  // ─── 3. Create location ───
  await page.goto("/locations");
  await expect(page.getByRole("heading", { name: "位置管理" })).toBeVisible();
  await page.getByRole("button", { name: "新增根位置" }).click();
  const locDialog = page.locator(".el-dialog", { hasText: "位置" });
  await locDialog.locator("input").fill(locName);
  await locDialog.getByRole("button", { name: "确定" }).click();
  await expect(page.locator(".tree-node", { hasText: locName })).toBeVisible();

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

  // Select category
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

  // ─── 5. Inbound stock ───
  await page.goto("/inventory");
  await expect(
    page.getByRole("heading", { name: "库存管理" }),
  ).toBeVisible();

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

  // Set quantity
  const inboundQty = inboundDialog.locator(".el-input-number input");
  await inboundQty.fill("10");

  // Select location
  await inboundDialog
    .locator(".el-form-item", { hasText: "入库位置" })
    .locator(".el-select")
    .click();
  await page
    .locator(".el-tree-select__popper:visible")
    .locator(".el-select-dropdown__item", { hasText: locName })
    .click();

  await inboundDialog.getByRole("button", { name: "下一步" }).click();
  await inboundDialog.getByRole("button", { name: "确认入库" }).click();
  await expect(inboundDialog).not.toBeVisible();
  await expect(page.getByText("入库成功")).toBeVisible();

  // ─── 6. Wait for projection and verify stock-by-location report ───
  // Navigate to the report page — the projection should be populated
  await page.goto("/reports/stock-by-location");
  await expect(
    page.getByRole("heading", { name: "库存分布" }),
  ).toBeVisible();

  // Wait for the table to load with our item
  const reportRow = page.locator("tbody tr", { hasText: itemName });
  await expect(reportRow).toBeVisible({ timeout: 10_000 });
  await expect(reportRow.getByText(locName)).toBeVisible();
  // exact: true —— 名称里嵌了 Date.now()，其数字串可能包含 "10" 子串，
  // 子串匹配会命中位置/物品/单位单元格，触发 strict mode violation。
  await expect(reportRow.getByText("10", { exact: true })).toBeVisible();

  // ─── 7. Search for the item ───
  await page.goto("/reports/search");
  await expect(
    page.getByRole("heading", { name: "全局搜索" }),
  ).toBeVisible();

  const searchInput = page.locator(".search-bar input");
  await searchInput.fill(itemName);
  await searchInput.press("Enter");

  // Wait for search results
  await expect(
    page.locator(".search-results").getByText(itemName),
  ).toBeVisible({ timeout: 10_000 });

  // ─── 8. Export CSV ───
  await page.goto("/reports/stock-by-location");
  await expect(
    page.getByRole("heading", { name: "库存分布" }),
  ).toBeVisible();
  await expect(reportRow).toBeVisible({ timeout: 10_000 });

  // Intercept the export download
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出 CSV" }).click();
  const download = await downloadPromise;

  // Verify download filename
  expect(download.suggestedFilename()).toMatch(/\.csv$/);

  // Read the downloaded content
  const content = await download.path();
  expect(content).toBeTruthy();

  // ─── 9. Projection rebuild via settings ───
  await page.goto("/reports/settings");
  await expect(
    page.getByRole("heading", { name: "报表设置" }),
  ).toBeVisible();

  // Click the rebuild button
  await page.getByRole("button", { name: "重建报表读模型" }).click();

  // Confirm the popconfirm
  const popconfirm = page.locator(".el-popconfirm");
  await expect(popconfirm).toBeVisible();
  await popconfirm.getByRole("button", { name: "确认重建" }).click();

  // Wait for rebuild to complete
  await expect(page.getByText("重建完成", { exact: true })).toBeVisible({ timeout: 30_000 });

  // ─── 10. Verify data still exists after rebuild ───
  await page.goto("/reports/stock-by-location");
  await expect(
    page.getByRole("heading", { name: "库存分布" }),
  ).toBeVisible();

  const rebuiltRow = page.locator("tbody tr", { hasText: itemName });
  await expect(rebuiltRow).toBeVisible({ timeout: 15_000 });
  await expect(rebuiltRow.getByText("10", { exact: true })).toBeVisible();
});
