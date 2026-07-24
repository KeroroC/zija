import { expect, test } from "@playwright/test";
import { ensureBootstrapped } from "./helpers";

test("location management lifecycle", async ({ page }) => {
  await ensureBootstrapped(page);

  const ts = Date.now();
  const rootName = `e2e-loc-root-${ts}`;
  const childName = `e2e-loc-child-${ts}`;
  const grandchildName = `e2e-loc-gc-${ts}`;
  const renamedRoot = `e2e-loc-root-renamed-${ts}`;

  // Helper: fill the name dialog input and submit
  async function fillNameDialogAndSubmit(name: string) {
    const dialog = page.locator(".el-dialog", { hasText: "位置" });
    await dialog.locator("input").fill(name);
    await dialog.getByRole("button", { name: "确定" }).click();
  }

  // --- Step 1: Navigate to locations page ---
  await page.goto("/locations");
  await expect(page.getByRole("heading", { name: "位置管理" })).toBeVisible();

  // --- Step 2: Create a root location ---
  await page.getByRole("button", { name: "新增根位置" }).click();
  await fillNameDialogAndSubmit(rootName);
  await expect(page.locator(".tree-node", { hasText: rootName })).toBeVisible();

  // --- Step 3: Create a child location under root ---
  const rootNode = page.locator(".tree-node", { hasText: rootName }).first();
  await rootNode.locator('[data-testid="loc-add"]').click();
  await fillNameDialogAndSubmit(childName);
  await expect(page.locator(".tree-node", { hasText: childName })).toBeVisible();

  // --- Step 4: Create a grandchild location under child ---
  const childNode = page.locator(".tree-node", { hasText: childName }).first();
  await childNode.locator('[data-testid="loc-add"]').click();
  await fillNameDialogAndSubmit(grandchildName);
  await expect(page.locator(".tree-node", { hasText: grandchildName })).toBeVisible();

  // --- Step 5: Rename the root location ---
  const nodeToRename = page.locator(".tree-node", { hasText: rootName }).first();
  await nodeToRename.locator('[data-testid="loc-rename"]').click();
  const renameDialog = page.locator(".el-dialog", { hasText: "重命名" });
  await expect(renameDialog).toBeVisible();
  await renameDialog.locator("input").clear();
  await renameDialog.locator("input").fill(renamedRoot);
  await renameDialog.getByRole("button", { name: "确定" }).click();
  await expect(page.locator(".tree-node", { hasText: renamedRoot })).toBeVisible();
  // Old name should no longer appear in the tree
  await expect(page.locator(".tree-node", { hasText: rootName })).toHaveCount(0);

  // --- Step 6: Move grandchild from under child to under root directly ---
  // Tree: root > child > grandchild  →  root > {child, grandchild}
  const gcNode = page.locator(".tree-node", { hasText: grandchildName }).first();
  await gcNode.locator('[data-testid="loc-move"]').click();
  const moveDialog = page.locator(".el-dialog", { hasText: "移动位置" });
  await expect(moveDialog).toBeVisible();
  // Select the root node in the move dialog tree as the target parent
  await moveDialog
    .locator(".el-tree-node__content", { hasText: renamedRoot })
    .click();
  await moveDialog.getByRole("button", { name: "确定" }).click();
  await expect(page.getByText("已移动")).toBeVisible();

  // --- Step 7: Verify circular reference is rejected ---
  // Current tree: root > {child, grandchild}
  // Try moving root under child → circular (child is a descendant of root)
  const rootNodeForMove = page.locator(".tree-node", { hasText: renamedRoot }).first();
  await rootNodeForMove.locator('[data-testid="loc-move"]').click();
  const circDialog = page.locator(".el-dialog", { hasText: "移动位置" });
  await expect(circDialog).toBeVisible();
  await circDialog
    .locator(".el-tree-node__content", { hasText: childName })
    .click();
  await circDialog.getByRole("button", { name: "确定" }).click();
  // Expect an error about circular reference or descendant
  await expect(page.getByText(/循环|子位置/)).toBeVisible();
  // Cancel the move dialog
  await circDialog.getByRole("button", { name: "取消" }).click();

  // --- Step 8: Delete an empty leaf node ---
  // child has no children (grandchild was moved out) → can be deleted
  const childForDelete = page.locator(".tree-node", { hasText: childName }).first();
  await childForDelete.locator('[data-testid="loc-delete"]').click();
  // Element MessageBox confirmation dialog appears
  await page.locator(".el-message-box").getByRole("button", { name: "确定" }).click();
  await expect(page.getByText("已删除")).toBeVisible();
  await expect(page.locator(".tree-node", { hasText: childName })).toHaveCount(0);

  // --- Step 9: Verify a node with children cannot be deleted ---
  // Root now has grandchild as a child
  const rootForDelete = page.locator(".tree-node", { hasText: renamedRoot }).first();
  await rootForDelete.locator('[data-testid="loc-delete"]').click();
  // Client-side check: warning about child locations, no confirmation dialog
  await expect(page.getByText(/子位置.*请先删除/)).toBeVisible();

  // --- Cleanup: delete grandchild then root ---
  const gcForDelete = page.locator(".tree-node", { hasText: grandchildName }).first();
  await gcForDelete.locator('[data-testid="loc-delete"]').click();
  await page.locator(".el-message-box").getByRole("button", { name: "确定" }).click();
  await expect(page.getByText("已删除")).toBeVisible();

  const rootFinal = page.locator(".tree-node", { hasText: renamedRoot }).first();
  await rootFinal.locator('[data-testid="loc-delete"]').click();
  await page.locator(".el-message-box").getByRole("button", { name: "确定" }).click();
  await expect(page.getByText("已删除")).toBeVisible();
});
