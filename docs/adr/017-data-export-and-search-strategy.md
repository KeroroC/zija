# ADR-017: 数据导出与全局搜索策略

## 状态

已批准

## 背景

阶段六需要提供报表、数据导出和全局搜索能力。spec §6.7–§6.8 要求全局搜索覆盖物品名称、品牌、标签、批次号、序列号和位置名称，导出支持当前筛选结果和完整数据集。如何在模块边界内（不跨模块直连他表）实现这些能力？

## 决策

### 1. CSV 导出（不交付导入）

- 阶段六仅交付 CSV 导出，不交付 CSV 导入。
- 导出使用同步流式响应（`StreamingResponseBody` 直接写 `HttpServletResponse` 输出流），不写临时文件、不入库。
- 行数硬上限 100,000，超过返回 `400 REPORTING_EXPORT_TOO_LARGE`。
- CSV 输出 UTF-8 BOM + `Content-Type: text/csv; charset=utf-8` + `Content-Disposition: attachment`。
- 每次导出写审计 `EXPORT_PERFORMED`（含 success/failure）。
- 导出仅 OWNER/ADMIN 可用；报表和搜索全员可读。

### 2. 全局搜索

- 实现 ILIKE `'%词%'` 子串匹配，不引入 `pg_trgm` 扩展、中文分词或拼音。
- 单家庭数据量（20,000 物品 / 100,000 批次）下 ILIKE 性能足够。
- 搜索端点 `GET /api/v1/reporting/search?q={keyword}&limitPerGroup=5`。
- 按实体类型分组返回（items / lots / locations），每组上限 20，默认 5。
- 每条命中用 `matchedFields` 列出命中字段，便于前端高亮。

### 3. 报表

- 5 张报表：当前库存与位置分布（stock-by-location）、临期批次（expiring-lots）、低库存物品（low-stock）、指定时间范围库存变化（stock-changes）、按成员/操作类型/物品筛选的流水（movements）。
- 统一分页 `page=1&pageSize=20`（上限 100）。
- 复杂报表 SQL 写在 `reporting` 自有 Mapper XML 中，作用在自有投影表上，不跨模块直连他表（见 ADR-004）。

### 4. 报表读模型

- `reporting` 模块维护 4 张扁平投影表：`reporting_search_index`、`reporting_stock_flat`、`reporting_movement_flat`、`reporting_location_flat`。
- 增量更新靠订阅源模块公开事件（StockChanged、ItemChanged、CategoryChanged 等）。
- 重建靠源模块的只读快照拉取端口（见 ADR-005）。
- 投影允许在源事务提交后短时间内最终一致；前端库存操作成功后立即显示 `inventory` 返回的最新库存，不等待投影。

### 5. 事件可靠性

- `reporting` 自有 `processed_event` / `dead-letter` / `EventRetryService`，与 reminder 同模式但隔离。
- 不重构 reminder 既有代码，两模块各自维护独立的 dead-letter 和重试机制。

### 6. 导出与备份的术语边界

- CSV 导出是"数据携带"（用户可读的筛选结果子集），不替代完整备份。
- 完整备份是 `pg_dump` + 文件卷镜像（见 ADR-009），由运维脚本触发，不经应用。
- 两者的批次标识和审计语义严格区分（`CONTEXT.md` 记录此边界）。

## 考虑过的备选

- **CSV 导入（两步预检）**：用户明确更正为阶段六不交付。导入逻辑复杂（模板解析、错误行展示、原子写入），延后到后续版本。
- **`pg_trgm` 全文搜索**：单家庭数据量下 ILIKE 足够，引入扩展增加部署复杂度。
- **后台任务生成大文件导出**：超过 100,000 行返回 400 引导改用更窄筛选，不引入任务表和异步通知机制，简化运维。
- **跨模块直连他表做报表**：破坏模块边界（见 ADR-011），`ModularityTests` 会拦截。投影表是正确的隔离方式。
- **reporting 复用 reminder 的 dead-letter 机制**：两模块事件处理逻辑不同（reminder 处理库存事件生成任务，reporting 处理多源事件更新投影），隔离更清晰。

## 后果

- 用户可以搜索和导出家庭数据，不依赖外部工具。
- 导出有硬上限保护，不会因超大数据集拖垮服务。
- 报表查询解耦于写模型，性能可控。
- 代价是投影表需要维护和重建机制，实现复杂度由 ADR-004/005 已覆盖。
