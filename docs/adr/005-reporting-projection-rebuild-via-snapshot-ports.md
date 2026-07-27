# ADR-005: reporting 投影的增量靠事件订阅、重建靠源模块快照拉取端口

## 状态

已批准

## 背景

ADR-004 决定 `reporting` 维护自有扁平投影表，靠订阅 `inventory` / `catalog` / `location` 的公开事件保持增量更新。但阶段六上线时投影为空，而报表（如「指定时间范围库存变化」）必须展示上线前的历史流水；事件丢失或投影 schema 变更后也需要重建。系统虽有 Spring Modulith 的 `event_publication` 登记表，但它用于可靠投递而非长期事件回放，完成事件可能被清理、且不含 `reporting` listener 的历史绑定，不能作为重建来源。

## 决策

事件与拉取职责分明：

1. **增量**：`reporting` 订阅源模块公开事件（`StockChangedEvent`、`catalog`/`location` 新增的变更事件），提交后写入自有投影表。事件是增量的唯一来源。
2. **重建**：`inventory` / `catalog` / `location` 在公共 API 上新增只读快照拉取端口（如 `InventoryApi.dumpMovements(householdId, cursor)`、`CatalogApi.dumpItems`、`LocationApi.dumpTree`），仅供 `reporting` 全量重建。
3. **重建触发**：投影为空时启动自动全量重建；提供管理员触发的「重建报表读模型」端点，供投影 schema 变更、事件丢失补齐或补偿。
4. 不从 `event_publication` 表回放历史事件做重建。

## 考虑过的备选

- **回放 event_publication**：脆弱、依赖清理策略、历史事件无 reporting listener 绑定，与 Modulith 设计意图不符。
- **不补历史**：报表无法满足「指定时间范围库存变化」，违反 spec §6.8，不可取。

## 后果

- 各源模块公共 API 新增稳定的只读拉取端口，成为跨模块契约。
- 重建期间投影可能短暂不完整，重建任务需支持游标分批、可恢复、不阻塞写路径；重建完成后回切事件增量。
- 事件用于增量、拉取用于重建的边界必须文档化，避免后续把重建耦合进事件机制。