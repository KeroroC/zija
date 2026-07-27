# ADR-004: reporting 模块采用事件投影 + 查询端口的混合读模型

## 状态

已批准

## 背景

阶段六新增 `reporting` 模块，负责全局搜索、报表、CSV 导入导出。库存、品目、位置的事实表分别归 `inventory` / `catalog` / `location` 模块，且 Spring Modulith 的 `ModularityTests` 禁止外部模块引用他模块 `internal` 包、实体或 Mapper。`reporting` 需要跨多表聚合与搜索，又不能直连他模块事务表。spec §8.4 要求 reporting「订阅公开事件并调用稳定查询端口」，§8.5 提到「报表投影」可在事务提交后短时间最终一致。

## 决策

`reporting` 模块采用**事件投影 + 查询端口**的混合读模型：

1. `reporting` 订阅 `inventory` / `catalog` / `location` 公开的领域事件（StockChanged、LotCreated、ItemCreated/Updated/Archived、CategoryChanged、LocationCreated/Updated/Moved/Deleted 等），在源事务提交后通过 Spring Modulith 事件登记机制把变更写进 `reporting` 自有的扁平投影表（`reporting_search_index`、`reporting_stock_flat`、`reporting_movement_flat`、`reporting_location_flat` 等）。
2. 所有报表查询、全局搜索都只读 `reporting` 自有投影表，不回查源模块事务表。
3. 源模块在公共 API 上仅暴露少量**只读拉取/快照端口**（如 `InventoryApi.dumpMovements(householdId, cursor)`、`CatalogApi.listActiveItems`、`LocationApi.dumpTree`），用于投影首次构建、事件丢失补齐和投影 schema 变更后的重建。
4. 复杂报表 SQL 仍写自定义 Mapper XML（spec §8.3），但作用在 `reporting` 自有投影表上，不跨模块读他表。
5. 允许报表/搜索在源事务提交后短时间最终一致；前端库存操作成功后立即显示 `inventory` 返回的最新库存，不等待投影。

## 考虑过的备选

- **纯查询端口（不建投影）**：在 `InventoryApi` 等上加搜索与按时间范围/成员/类型筛流水的查询方法，复杂 SQL 留在各模块 internal Mapper。简单、强一致，但公共 API 膨胀、跨表搜索 JOIN 无处安放、性能依赖原表索引；与 spec §8.5「报表投影」字面不符。
- **直连他模块表**：`reporting` 直接读 `inventory.stock_position` 等。简单但直接破坏 `ModularityTests`，不可取。

## 后果

- `inventory` / `catalog` / `location` 必须新增并稳定公开一批领域事件（事件契约即跨模块契约），新增投影重建端口。
- `reporting` 引入投影表与 Flyway 迁移，需处理重放、去重（processed_event 登记表，复用 5a 的 `EventRetryService` 模式）、重建窗口。
- 报表/搜索查询性能可控、解耦于写模型；代价是首次构建投影与重建机制带来的实现复杂度。
- 投影只读、源数据仍归源模块；导入写路径仍走源模块命令（见后续 ADR），reporting 不直接写他模块事实表。