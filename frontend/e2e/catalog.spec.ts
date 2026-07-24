import { expect, test } from "@playwright/test";
import { ensureBootstrapped } from "./helpers";

const ts = Date.now();
const catName = `e2e-cat-${ts}`;
const brandName = `e2e-brand-${ts}`;
const unitName = `e2e-unit-${ts}`;
const tagName = `e2e-tag-${ts}`;
const itemName = `e2e-item-${ts}`;

test("full catalog lifecycle: categories, brands, units, tags, and items", async ({ page }) => {
  await ensureBootstrapped(page);

  // ─── 1. Navigate to catalog settings ───
  await page.goto("/settings/catalog");
  await expect(page.getByRole("heading", { name: "目录设置" })).toBeVisible();

  // ─── 2. Create a category ───
  await page.getByRole("button", { name: "添加根分类" }).click();
  const categoryDialog = page.locator(".el-dialog", { hasText: "添加根分类" });
  await categoryDialog.locator(".el-form-item", { hasText: "名称" }).locator("input").fill(catName);
  await categoryDialog.getByRole("button", { name: "确定" }).click();
  await expect(categoryDialog).not.toBeVisible();
  await expect(page.locator(".tree-node", { hasText: catName })).toBeVisible();

  // ─── 3. Create a brand ───
  await page.getByRole("tab", { name: "品牌" }).click();
  await page.getByPlaceholder("新品牌名称").fill(brandName);
  await page.getByRole("button", { name: "添加" }).click();
  await expect(page.locator("tbody tr", { hasText: brandName })).toBeVisible();

  // ─── 4. Create a unit ───
  await page.getByRole("tab", { name: "单位" }).click();
  await page.getByPlaceholder("新单位名称").fill(unitName);
  // el-input-number: clear and type the decimal scale value
  const decimalInput = page.getByPlaceholder("小数位");
  await decimalInput.click();
  await decimalInput.press("Control+a");
  await decimalInput.press("Backspace");
  await decimalInput.type("2");
  await page.getByRole("button", { name: "添加" }).click();
  await expect(page.locator("tbody tr", { hasText: unitName })).toBeVisible();

  // ─── 5. Create a tag ───
  await page.getByRole("tab", { name: "标签" }).click();
  await page.getByPlaceholder("新标签名称").fill(tagName);
  await page.getByRole("button", { name: "添加" }).click();
  await expect(page.locator("tbody tr", { hasText: tagName })).toBeVisible();

  // ─── 6. Navigate to items page ───
  await page.goto("/items");
  await expect(page.getByRole("heading", { name: "物品资料" })).toBeVisible();

  // ─── 7. Create a new item ───
  await page.getByRole("button", { name: "新建物品" }).click();
  const drawer = page.locator(".el-drawer");

  // Fill name
  await page.getByPlaceholder("请输入物品名称").fill(itemName);

  // Select management type (耐用品)
  await page.locator(".el-form-item", { hasText: "管理类型" }).locator(".el-select").click();
  await page.locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: "耐用品" }).click();

  // Select unit
  await page.locator(".el-form-item", { hasText: "单位" }).locator(".el-select").click();
  await page.locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: unitName }).click();

  // Select category via tree-select
  await page.locator(".el-form-item", { hasText: "分类" }).locator(".el-tree-select").click();
  await page.locator(".el-tree-select-dropdown:visible")
    .locator(".el-tree-node__label", { hasText: catName }).click();

  // Select brand (filterable + allow-create: type and pick existing)
  await page.locator(".el-form-item", { hasText: "品牌" }).locator(".el-select").click();
  await page.locator(".el-form-item", { hasText: "品牌" }).locator("input").fill(brandName);
  await page.locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: brandName }).first().click();

  // Select tag (multiple + filterable + allow-create: type and pick existing)
  await page.locator(".el-form-item", { hasText: "标签" }).locator(".el-select").click();
  await page.locator(".el-form-item", { hasText: "标签" }).locator("input").fill(tagName);
  await page.locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: tagName }).first().click();

  // Submit the form
  await page.getByRole("button", { name: "创建" }).click();
  await expect(drawer).not.toBeVisible();
  const itemRow = page.locator("tbody tr", { hasText: itemName });
  await expect(itemRow).toBeVisible();

  // ─── 8. Upload cover image (skipped - requires a real image file) ───

  // ─── 9. Archive the item via detail drawer ───
  await itemRow.click();
  await expect(page.getByRole("heading", { name: "物品详情" })).toBeVisible();
  await expect(page.getByText(itemName)).toBeVisible();
  await page.getByRole("button", { name: "归档" }).click();
  // Confirm the archive dialog
  await page.locator(".el-message-box").getByRole("button", { name: "确定" }).click();
  // Close the detail drawer (selectedItem is not refreshed after archive)
  await page.locator(".el-drawer__close-btn").click();
  await expect(drawer).not.toBeVisible();

  // Verify the item is no longer in the ACTIVE table
  await expect(itemRow).not.toBeVisible();

  // Change status filter to show archived items
  const statusFilter = page.locator(".items-filters .el-select", { hasText: "活跃" });
  await statusFilter.click();
  await page.locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: "归档" }).click();

  // Verify the item shows as archived
  await expect(itemRow).toBeVisible();
  await expect(itemRow.getByText("归档")).toBeVisible();

  // ─── 10. Restore the item via detail drawer ───
  await itemRow.click();
  await expect(page.getByRole("heading", { name: "物品详情" })).toBeVisible();
  await page.getByRole("button", { name: "恢复" }).click();
  // Close the drawer
  await page.locator(".el-drawer__close-btn").click();
  await expect(drawer).not.toBeVisible();

  // Change status filter back to active
  const archivedFilter = page.locator(".items-filters .el-select", { hasText: "归档" });
  await archivedFilter.click();
  await page.locator(".el-select-dropdown:visible")
    .locator(".el-select-dropdown__item", { hasText: "活跃" }).click();

  // Verify the item shows as active
  await expect(itemRow).toBeVisible();
  await expect(itemRow.getByText("活跃")).toBeVisible();

  // ─── 11. Navigate back to catalog settings ───
  await page.goto("/settings/catalog");
  await expect(page.getByRole("heading", { name: "目录设置" })).toBeVisible();

  // ─── 12. Archive and restore the category ───
  // Toggle "显示已归档" so archived categories remain visible after archiving
  await page.locator(".el-tab-pane").nth(0).locator(".el-switch").click();

  const catNode = page.locator(".tree-node", { hasText: catName });
  await catNode.getByRole("button", { name: "归档" }).click();
  await page.locator(".el-message-box").getByRole("button", { name: "确定" }).click();
  // Verify category shows as archived (tag in tree node)
  await expect(catNode.getByText("已归档")).toBeVisible();

  // Restore the category
  await catNode.getByRole("button", { name: "恢复" }).click();
  await expect(catNode.getByText("已归档")).not.toBeVisible();

  // ─── 13. Archive and restore the brand ───
  await page.getByRole("tab", { name: "品牌" }).click();
  // Toggle "显示已归档" for brands
  await page.locator(".el-tab-pane").nth(1).locator(".el-switch").click();

  const brandRow = page.locator("tbody tr", { hasText: brandName });
  await brandRow.getByRole("button", { name: "归档" }).click();
  await page.locator(".el-message-box").getByRole("button", { name: "确定" }).click();
  await expect(brandRow.getByText("已归档")).toBeVisible();

  // Restore the brand
  await brandRow.getByRole("button", { name: "恢复" }).click();
  await expect(brandRow.getByText("活跃")).toBeVisible();

  // ─── 14. Archive and restore the unit ───
  await page.getByRole("tab", { name: "单位" }).click();
  // Toggle "显示已归档" for units
  await page.locator(".el-tab-pane").nth(2).locator(".el-switch").click();

  const unitRow = page.locator("tbody tr", { hasText: unitName });
  await unitRow.getByRole("button", { name: "归档" }).click();
  await page.locator(".el-message-box").getByRole("button", { name: "确定" }).click();
  await expect(unitRow.getByText("已归档")).toBeVisible();

  // Restore the unit
  await unitRow.getByRole("button", { name: "恢复" }).click();
  await expect(unitRow.getByText("活跃")).toBeVisible();

  // ─── 15. Archive and restore the tag ───
  await page.getByRole("tab", { name: "标签" }).click();
  // Toggle "显示已归档" for tags
  await page.locator(".el-tab-pane").nth(3).locator(".el-switch").click();

  const tagRow = page.locator("tbody tr", { hasText: tagName });
  await tagRow.getByRole("button", { name: "归档" }).click();
  await page.locator(".el-message-box").getByRole("button", { name: "确定" }).click();
  await expect(tagRow.getByText("已归档")).toBeVisible();

  // Restore the tag
  await tagRow.getByRole("button", { name: "恢复" }).click();
  await expect(tagRow.getByText("活跃")).toBeVisible();
});
