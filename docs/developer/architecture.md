# 架构与模块划分

知家后端采用 Spring Modulith 模块化单体架构，按业务能力划分模块，每个模块有独立的 `Api` 接口和 `internal` 实现。

## 模块清单

| 模块 | 职责 |
|---|---|
| `system` | 健康检查、安装信息、审计日志查询 |
| `identity` | 认证、用户与会话管理 |
| `household` | 家庭管理、引导、邀请 |
| `catalog` | 物品分类（品牌、单位、分类、标签） |
| `location` | 存储位置 |
| `file` | 文件存储与完整性检查 |
| `inventory` | 批次（Lot）、库存位、流水（Movement）、盘点（Stocktake）、幂等与一致性 |
| `reminder` | 提醒规则、通知、邮件摘要 |
| `reporting` | 读模型投影、报表查询端口、CSV 导出 |

每个模块遵循 `com.zija.<module>` 包结构：

```
com.zija.<module>/
  <Module>Api.java          # 公共接口（跨模块唯一契约）
  package-info.java         # @ApplicationModule
  internal/                 # 实现 — 其他模块不可访问
    <Module>Controller.java
    <Module>Service.java
    persistence/            # Mapper、Entity、XML — 模块内部
```

外部模块只能依赖另一个模块的公共 `Api` 接口及其公开 DTO/record，不得 import `internal` 包。模块依赖方向由 `ModularityTests` 自动验证。

## 核心概念

知家采用「不可变流水 + 派生库存位」的数据模型：

- **物品（Item）**：描述一种家庭用品「是什么」的资料记录，可复用。
- **批次（Lot）**：描述某次购入或某件独立资产的实例，独立的到期与库存。
- **库存位（Stock Position）**：某批次在某位置的当前数量，由不可变流水作为事实来源。
- **流水（Movement）**：库存数量的一次不可变变更记录，类型含入库 / 领用 / 报损 / 盘点调整 / 移位 / 冲正。

完整术语定义见 [`CONTEXT.md`](../../CONTEXT.md)。

## 持久化

- 简单 CRUD 用 MyBatis-Plus `BaseMapper` + Lambda QueryWrapper。
- 复杂查询（库存聚合、报表、CSV 导出）用自定义 Mapper XML，位于 `src/main/resources/mapper/`。
- 分页通过 `PaginationInnerInterceptor(DbType.POSTGRE_SQL)` 注册。
- 元数据实体（物品、位置、提醒规则）使用 `OptimisticLockerInnerInterceptor` 乐观锁。
- 库存扣减用显式 `SELECT ... FOR UPDATE`（自定义 XML），不依赖乐观锁插件。
- 不使用全局逻辑删除，禁用/归档通过显式业务状态字段。
- 实体类模块内部可见，不跨模块边界泄漏。
- UUID 主键（`id-type: assign_uuid`），下划线/驼峰映射默认开启。

## 事件与投影

`inventory` 与 `reminder` 通过 Spring Modulith 的 `ApplicationEventPublisher` 发布公开领域事件（`record` 类型）。`reporting` 模块订阅这些事件，按固定顺序重放到读模型投影（`reporting_stock_flat` / `reporting_movement_flat` / `reporting_search_index` 等）。

公共领域事件字段**只能追加，不可重排或删除**；消费者与序列化器必须容忍新键缺失。

完整的架构决策记录见 [`docs/adr/`](../adr/)。
