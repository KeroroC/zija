# ADR-011: 模块化单体架构与模块边界约定

## 状态

已批准。依赖图以各模块 `package-info.java` 为准。`inventory → file` 见 [ADR-019](019-attachments-in-file-module.md)；`ai` 与 `shared` 为后续加入的模块。

## 背景

知家后端是一个单体部署的 Spring Boot 应用，但业务代码必须按业务能力划分为独立模块，避免"大泥球"式的相互穿透。spec §8.4 定义了 9 个模块及其依赖方向，§8.3 要求持久化对象不得跨模块泄漏。如何在不引入微服务复杂度的前提下，保证模块边界在编译期和运行期都被强制约束？

## 决策

采用 **Spring Modulith 模块化单体**，以约定优于配置的方式执行模块边界：

1. **11 个模块**，按业务能力组织包结构：`shared`、`identity`、`household`、`catalog`、`location`、`inventory`、`reminder`、`file`、`reporting`、`system`、`ai`。`shared` 放跨模块枚举、错误码和问题辅助类型。每个模块的根包即公开 API，`internal` 子包默认对其它模块不可见。

2. **单向依赖**，依赖图由 `@ApplicationModule(allowedDependencies = ...)` 在 `package-info.java` 中声明：
   - `shared`、`system` → 无业务模块依赖
   - `identity` → `shared`, `system`
   - `household` → `shared`, `identity`, `system`
   - `catalog` → `shared`, `household`, `file`, `system`
   - `location` → `shared`, `household`, `system`
   - `inventory` → `shared`, `household`, `catalog`, `location`, `file`, `system`（批次附件，见 ADR-019）
   - `reminder` → `shared`, `household`, `catalog`, `inventory`, `system`
   - `file` → `shared`, `household`, `system`
   - `reporting` → `shared`, `household`, `catalog`, `location`, `inventory`, `identity`, `system`
   - `ai` → `shared`, `household`, `system`, `file`, `inventory`, `catalog`, `location`, `identity`, `reminder`

3. **公开契约 = Api 接口 + DTO record + 领域事件**。跨模块只能交换这三类公开类型；实体（Entity）、Mapper、XML 映射、持久化对象都位于 `internal/persistence/` 子包，不得作为跨模块类型。

4. **`ModularityTests` 自动验证**：CI 中的 `ApplicationModules.of(ZijaApplication.class).verify()` 持续检查依赖方向、`internal` 包不可见性、无循环依赖。任何违规在编译期即失败。

5. **不启用全局逻辑删除**（`@TableLogic`）。物品归档、成员停用和流水冲正均使用明确业务状态字段，审计敏感记录不得被通用删除能力隐藏。

6. **复杂 SQL 写在模块自有的 Mapper XML 中**。`reporting` 的报表 SQL 只作用在自有投影表上（见 ADR-004），不直连 `inventory` 或 `catalog` 的事务表。`inventory` 的展示查询可以在本模块 XML 里 JOIN `catalog` 表补物品名、批次号和单位（见 ADR-018）；这不把 catalog 实体暴露成跨模块类型。

## 考虑过的备选

- **微服务**：引入分布式事务、服务发现和网络延迟，单家庭私有部署的规模（20 成员 / 100 万流水）完全不需要。spec §3.3 明确不做。
- **OPEN 模块（全部子包可见）**：`ApplicationModule.Type.OPEN` 让所有子包对其它模块可见，仅在迁移期临时使用；正式模块一律用默认的封闭模式。
- **`@ApplicationModuleInterface` 注解暴露 API**：不存在此注解。模块根包默认就是 API 包，无需额外标注。额外暴露子包用 `@NamedInterface("spi")`，依赖方用 `allowedDependencies = {"模块名::spi"}`。

## 后果

- 模块边界由 `ModularityTests` 强制守护，违规即编译失败，不可绕过。
- 公共 Api/DTO/Event 的签名变更即跨模块契约变更，字段只能追加不可重排或删除（见 ADR-006 对 StockChangedEvent 的约束）。
- 代价是新建模块时需正确声明 `allowedDependencies`，且跨模块复用类型必须提升到公开 API 层。
