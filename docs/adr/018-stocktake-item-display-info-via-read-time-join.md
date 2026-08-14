# 盘点行项展示信息：读时 join 补齐物品名/批次号/单位，而非冗余落库

盘点「录入数据」与「确认盘点」两个步骤的行项此前只展示批次号和账面数量，批次列甚至回退到批次 UUID 的前 8 位（如 `lotId.substring(0,8)`），用户无法从行的样子辨认出是哪个物品，盘点核对困难。

关键分歧是这些展示字段（物品名 `itemName`、批次号 `lotNumber`、单位 `unitName`）应该**随盘点草稿落库冗余一份**，还是**读时 join 补齐**。

决定：

- **读时 join 补齐**：盘点行项详情查询由纯 `inventory_stocktake_item` 单表，改为 `JOIN inventory_lot → catalog_item → catalog_unit`，返回带 `itemName`、`lotNumber`、`unitName` 的 DTO `StocktakeItemWithDetails`。盘点详情接口借此一并返回这三个字段，前端只消费，不做本地拼装。
- **不冗余落库**：`inventory_stocktake_item` 仍只存 `lot_id`/`location_id` 等标识与数量，不新增展示列。物品改名、批次号变更后盘点草稿始终显示最新名称，无同步滞后、无历史脏快照。
- **空值前端兜底**：批次号、单位为 `null` 时后端直接返回 `null`，由前端渲染为「—」占位，避免出现「null」字样。
- **数量与单位分离展示**：账面/实际数量为纯数字，单位以独立「单位」列展示（录入与确认两处一致），避免把单位塞进输入框干扰录入。join 涉及的 `item_name`、`lot_number`、`unit_name` 三列均 `NOT NULL` 且具引用完整性，故「—」兜底仅是防御层，常规数据不会触发。

权衡与拒绝项：

- 拒绝「回收盘点行项时冗余 `item_name`/`unit_name` 快照」：盘点行项是草稿态的活跃视图，不是历史读模型；物品改名后应即时反映在盘点界面上。若落库快照，改名后需事件同步或等刷新，与入口常见「显示旧名」问题同源。
- 拒绝「前端拉全量批次自行建 Map 拼装」：盘点对话框虽已用 `fetchLots` 拉过零库存批次，但以 `pageSize=1000` 一次全拉做展示拼装既不严谨也与 `toMovementWithDetailsResponse`、`toStockPositionResponse` 已有的「后端 join 补展示字段」模式不一致。
- 沿用既有模式：与移动流水 `MovementWithDetails`、库存位 `StockPositionWithDetails` 一致，均由持久层 join DTO + Controller 映射剥离实体字段，保持「展示字段由后端一次性算好」的一致性。
