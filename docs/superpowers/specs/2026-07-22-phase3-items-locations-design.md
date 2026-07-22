# 阶段三：物品与位置设计方案

- **状态：** 待审批
- **日期：** 2026-07-22
- **覆盖规格：** `docs/superpowers/specs/2026-07-18-zija-design.md` 第 5.1–5.2、6.2–6.3、6.9、7、9–12 节
- **交付路线：** 阶段 3（物品与位置）

## 1. 目标与范围

### 1.1 必须达成的结果

- 活跃家庭成员可创建、查看、编辑、归档和恢复可复用的物品资料。
- 物品支持名称、`CONSUMABLE`/`DURABLE` 管理类型、分类、品牌、基础单位、标签、备注、封面、临期提醒配置和低库存配置。
- 分类采用家庭内树形字典；品牌、单位和标签均为家庭内共享字典。
- 单位定义小数精度，物品级低库存阈值必须符合该单位精度。
- 每个物品最多关联一张封面；系统只接受 JPEG、PNG 和 WebP，单文件不超过 5 MiB，真实内容类型必须与允许类型匹配。
- 活跃成员可建立、重命名、排序和移动家庭位置树；服务端拒绝循环、带子节点删除和已被库存引用的位置删除。
- 阶段四的库存模块可仅调用 `CatalogApi` 和 `LocationApi`，不需要反向访问这两个模块的 `internal` 包或持久化表。
- API、前端、PostgreSQL 集成、Playwright 和 Compose 冒烟测试覆盖上述主流程及失败分支。

### 1.2 不在范围内

- 批次、库存位、入库、领用、报损、移位、盘点和库存流水（阶段 4）。
- 根据物品规则生成、更新或关闭提醒任务（阶段 5）。阶段 3 只保存物品级提醒配置。
- 搜索投影、报表、CSV 导入导出（阶段 6）。
- 多封面、票据、说明书、任意附件集合、云对象存储或第三方图床。
- 计量单位换算；同一物品只使用一个基础单位。
- 手机端适配、条码、OCR、AI 识别和公开文件链接。

## 2. 已确认的设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 业务模块 | 新增独立的 `file`、`catalog` 和 `location` 模块 | 文件、物品字典和空间树具有不同生命周期；拆分后阶段四可保持单向依赖。 |
| 模块方向 | `catalog -> household, file`；`location -> household`；三个模块可调用 `system` 的 `SystemApi` 记录审计 | 与总体模块图一致，禁止 `catalog` 或 `location` 依赖未来的 `inventory`。 |
| 分类与单位权限 | 仅 Owner/Admin 可以创建、编辑、归档和恢复 | 分类层级与计量精度是全家共享的稳定规则。 |
| 品牌与标签权限 | 所有活跃成员可新增；仅 Owner/Admin 可重命名、归档和恢复 | 录入时保持低摩擦，同时由管理员处理重复和错误值。 |
| 字典生命周期 | 所有字典项只归档，不提供物理删除 | 已关联物品和历史展示保持可解释；归档项不再出现在新建选择器。 |
| 物品生命周期 | 所有物品只允许 `ACTIVE` 与 `ARCHIVED` 之间切换，不提供物理删除 | `catalog` 不需要查询或依赖阶段四库存引用关系，且历史链路始终保留。 |
| 封面存储 | 文件写入独立持久卷，PostgreSQL 仅保存元数据和系统生成对象键 | 满足私有部署和后续备份要求，避免把二进制大对象混入业务表。 |
| 位置删除 | 仅未引用、无子节点的叶节点可物理删除；一旦被库存模块标记引用则永久拒绝删除 | `location` 通过公开 API 维护引用事实，后续库存模块不反向耦合。 |
| 桌面交互 | 物品使用筛选栏、分页表格和右侧抽屉；位置使用树形工作区与详情抽屉 | 符合总体桌面信息架构，支持高频浏览和低干扰编辑。 |

## 3. 模块结构与公开契约

### 3.1 file 模块

```
com.zija.file/
  FileApi.java
  package-info.java
  internal/
    FileController.java
    FileService.java
    FileStorage.java
    FileContentInspector.java
    persistence/
      StoredFileEntity.java
      StoredFileMapper.java
```

`file` 只负责受控文件内容及其元数据，不理解物品、批次或库存。它通过 `HouseholdApi` 验证当前读取者或写入者属于目标家庭；它不接收客户端指定的磁盘路径、对象键或家庭 ID。

**FileApi 公开职责：**

- 校验并暂存不超过 5 MiB 的上传内容；公开命令使用 `byte[]`、原文件名和声明媒体类型，不向其他模块暴露 `MultipartFile` 或 Web 控制器类型。
- 对原文件名只保留规范化的 basename，拒绝控制字符和不受支持的扩展名；`.jpg`/`.jpeg`、`.png`、`.webp` 必须分别与检测出的 JPEG、PNG、WebP 签名匹配。
- 检测 JPEG、PNG、WebP 的真实文件签名；若客户端声明媒体类型非空，它也必须与检测结果匹配，然后返回不可变的 `StoredFileInfo`。
- 为调用方维护引用计数：关联封面时 `retain`，替换或移除封面时 `release`；引用数降至零后，在事务提交后删除元数据和实际文件。
- 在成员校验通过后，以已检测的媒体类型和 `X-Content-Type-Options: nosniff` 输出二进制内容。

实际文件先写入同一存储卷内的临时文件，再原子移动到由 UUID 生成的对象键。数据库事务回滚时，事务同步回调必须删除本次新增的对象；替换旧封面时，旧对象只能在成功提交后删除。这样文件系统与数据库不会因失败请求留下可访问的孤儿文件或丢失仍被引用的封面。

### 3.2 catalog 模块

```
com.zija.catalog/
  CatalogApi.java
  package-info.java
  internal/
    ItemController.java
    CatalogDictionaryController.java
    ItemService.java
    CatalogDictionaryService.java
    persistence/
      ItemEntity.java
      CategoryEntity.java
      BrandEntity.java
      UnitEntity.java
      TagEntity.java
      ItemTagEntity.java
      ItemMapper.java
      CategoryMapper.java
      BrandMapper.java
      UnitMapper.java
      TagMapper.java
```

`catalog` 负责物品定义及其可复用元数据。它只通过 `HouseholdApi` 取得活跃成员和家庭边界，通过 `FileApi` 管理封面，不能直接查询 `file` 的 Mapper 或表。阶段四只能通过 `CatalogApi` 读取物品、验证物品状态和单位精度。

**CatalogApi 最小公开能力：**

- `ItemInfo requireItem(UUID householdId, UUID itemId)`：返回可用于历史查询的物品；不存在或跨家庭时不泄露资源信息。
- `ItemInfo requireActiveItem(UUID householdId, UUID itemId)`：供未来新入库等命令使用，归档物品返回稳定业务错误。
- `UnitInfo requireUnit(UUID householdId, UUID unitId)`：返回单位精度，供数量和阈值校验使用。

### 3.3 location 模块

```
com.zija.location/
  LocationApi.java
  package-info.java
  internal/
    LocationController.java
    LocationService.java
    persistence/
      LocationEntity.java
      LocationMapper.java
      LocationMapper.xml
```

`location` 管理家庭内物理空间树，不保存库存数量、批次或流水。阶段四在创建第一个库存位或引用位置的流水前调用 `LocationApi.markReferenced(...)`；该写入在同一数据库事务内完成，以便回滚时不留下错误的引用标记。

**LocationApi 最小公开能力：**

- `LocationInfo requireLocation(UUID householdId, UUID locationId)`：验证位置存在且属于家庭。
- `void markReferenced(UUID householdId, UUID locationId)`：将 `ever_referenced` 置为 `true`，该状态不可回退。
- `LocationTree tree(UUID householdId)`：供后续库存选择器复用完整位置层级，而不访问 `location.internal`。

### 3.4 依赖规则

```
catalog  -> household, file, system
file     -> household, system
location -> household, system
inventory (阶段 4) -> catalog, location, household
```

`system` 是技术能力模块，调用仅限 `SystemApi.recordAudit(...)`。`ModularityTests` 必须继续验证所有模块只能引用对方根包公开的 API、DTO 或记录；任何 `*.internal.*` 导入、`catalog <-> location` 直接耦合或面向未来 `inventory` 的反向依赖均为失败。

## 4. 数据库设计

所有迁移是仅前进的 Flyway SQL。当前最新版本为 `V7`，阶段三按以下顺序新增：

1. `V8__create_stored_file.sql`
2. `V9__create_catalog.sql`
3. `V10__create_location.sql`

所有业务行均携带 `household_id`，控制器从会话中的活跃成员推导该值，绝不绑定请求体中的家庭标识。

### 4.1 文件元数据表

```sql
CREATE TABLE stored_file (
    id                  UUID PRIMARY KEY,
    household_id        UUID NOT NULL REFERENCES household(id),
    storage_key         VARCHAR(160) NOT NULL UNIQUE,
    original_filename   VARCHAR(255) NOT NULL,
    declared_media_type VARCHAR(100),
    detected_media_type VARCHAR(100) NOT NULL,
    byte_size           BIGINT NOT NULL,
    sha256              CHAR(64) NOT NULL,
    reference_count     INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stored_file_media_type CHECK (
        detected_media_type IN ('image/jpeg', 'image/png', 'image/webp')
    ),
    CONSTRAINT ck_stored_file_size CHECK (byte_size > 0 AND byte_size <= 5242880),
    CONSTRAINT ck_stored_file_references CHECK (reference_count >= 0)
);

CREATE INDEX idx_stored_file_household ON stored_file(household_id);
```

`storage_key` 使用服务端 UUID 和经过检测的扩展名生成，例如 `2026/07/<uuid>.webp`。它不使用原始文件名，也不会出现在审计日志、Problem Details 或前端状态中。数据库只保留经过 basename 提取和控制字符清理后的原文件名作为展示元数据。`catalog_item.cover_file_id` 只保存 UUID，不建立跨模块 SQL 外键；`FileApi` 的 retain/release 调用和数据库事务保证引用关系一致。

### 4.2 分类、品牌、单位和标签

```sql
CREATE TABLE catalog_category (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    parent_id       UUID,
    name            VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_category_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_catalog_category_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_catalog_category_name UNIQUE NULLS NOT DISTINCT
        (household_id, parent_id, name_normalized),
    CONSTRAINT fk_catalog_category_parent_same_household
        FOREIGN KEY (household_id, parent_id)
        REFERENCES catalog_category(household_id, id)
);

CREATE TABLE catalog_brand (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    name            VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_brand_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_catalog_brand_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_catalog_brand_name UNIQUE (household_id, name_normalized)
);

CREATE TABLE catalog_unit (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    name            VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    decimal_scale   SMALLINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_unit_scale CHECK (decimal_scale BETWEEN 0 AND 6),
    CONSTRAINT ck_catalog_unit_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_catalog_unit_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_catalog_unit_name UNIQUE (household_id, name_normalized)
);

CREATE TABLE catalog_tag (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    name            VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_tag_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_catalog_tag_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_catalog_tag_name UNIQUE (household_id, name_normalized)
);
```

字典名称在去除首尾空白、Unicode NFKC 规范化并用 `Locale.ROOT` 进行大小写折叠后写入 `name_normalized`。名称在同一家庭内不可与已归档记录重复；若需要复用名称，Owner/Admin 必须恢复原记录而不是创建一个新 ID。分类归档前必须先处理其活动子分类；单位一旦被任一物品使用，其 `decimal_scale` 不可修改，只能归档旧单位并创建新单位。品牌和标签没有颜色、图片或外部编码等首期字段。

### 4.3 物品与标签关联

```sql
CREATE TABLE catalog_item (
    id                        UUID PRIMARY KEY,
    household_id              UUID NOT NULL REFERENCES household(id),
    name                      VARCHAR(120) NOT NULL,
    management_type           VARCHAR(20) NOT NULL,
    category_id               UUID,
    brand_id                  UUID,
    unit_id                   UUID NOT NULL,
    cover_file_id             UUID,
    memo                      VARCHAR(4000),
    expiry_reminder_mode      VARCHAR(20) NOT NULL DEFAULT 'INHERIT',
    expiry_reminder_days      SMALLINT[],
    low_stock_mode            VARCHAR(20) NOT NULL DEFAULT 'INHERIT',
    low_stock_threshold       NUMERIC(18, 6),
    status                    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    archived_at               TIMESTAMPTZ,
    archived_by               UUID REFERENCES account(id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_item_type CHECK (
        management_type IN ('CONSUMABLE', 'DURABLE')
    ),
    CONSTRAINT ck_catalog_item_expiry_mode CHECK (
        expiry_reminder_mode IN ('INHERIT', 'DISABLED', 'CUSTOM')
    ),
    CONSTRAINT ck_catalog_item_low_stock_mode CHECK (
        low_stock_mode IN ('INHERIT', 'DISABLED', 'CUSTOM')
    ),
    CONSTRAINT ck_catalog_item_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_catalog_item_archive_state CHECK (
        (status = 'ACTIVE' AND archived_at IS NULL AND archived_by IS NULL)
        OR (status = 'ARCHIVED' AND archived_at IS NOT NULL AND archived_by IS NOT NULL)
    ),
    CONSTRAINT ck_catalog_item_expiry_config CHECK (
        (expiry_reminder_mode = 'CUSTOM' AND expiry_reminder_days IS NOT NULL)
        OR (expiry_reminder_mode <> 'CUSTOM' AND expiry_reminder_days IS NULL)
    ),
    CONSTRAINT ck_catalog_item_low_stock_config CHECK (
        (low_stock_mode = 'CUSTOM' AND low_stock_threshold IS NOT NULL)
        OR (low_stock_mode <> 'CUSTOM' AND low_stock_threshold IS NULL)
    ),
    CONSTRAINT uq_catalog_item_household_id UNIQUE (household_id, id),
    CONSTRAINT fk_catalog_item_category_same_household
        FOREIGN KEY (household_id, category_id)
        REFERENCES catalog_category(household_id, id),
    CONSTRAINT fk_catalog_item_brand_same_household
        FOREIGN KEY (household_id, brand_id)
        REFERENCES catalog_brand(household_id, id),
    CONSTRAINT fk_catalog_item_unit_same_household
        FOREIGN KEY (household_id, unit_id)
        REFERENCES catalog_unit(household_id, id)
);

CREATE TABLE catalog_item_tag (
    household_id UUID NOT NULL REFERENCES household(id),
    item_id      UUID NOT NULL,
    tag_id       UUID NOT NULL,
    PRIMARY KEY (household_id, item_id, tag_id),
    CONSTRAINT fk_catalog_item_tag_item_same_household
        FOREIGN KEY (household_id, item_id)
        REFERENCES catalog_item(household_id, id),
    CONSTRAINT fk_catalog_item_tag_tag_same_household
        FOREIGN KEY (household_id, tag_id)
        REFERENCES catalog_tag(household_id, id)
);

CREATE INDEX idx_catalog_item_household_status
    ON catalog_item(household_id, status);
CREATE INDEX idx_catalog_item_category ON catalog_item(category_id);
CREATE INDEX idx_catalog_item_brand ON catalog_item(brand_id);
CREATE INDEX idx_catalog_item_unit ON catalog_item(unit_id);
CREATE INDEX idx_catalog_item_tag_tag ON catalog_item_tag(household_id, tag_id, item_id);
```

这些组合外键保证分类父子关系、物品字典关联、标签关联和位置父子关系不会跨越家庭边界。名称在家庭内不强制唯一。`CUSTOM` 临期提醒日必须是 1 至 3650 的互异正整数，按从大到小保存；`INHERIT` 和 `DISABLED` 不保存数组。`CUSTOM` 低库存阈值必须大于零，且 `BigDecimal.stripTrailingZeros().scale()` 不得超过关联单位的 `decimal_scale`。阶段三只保存配置：`INHERIT` 的实际默认值和任务计算由阶段五引入。

服务端不允许将归档分类、品牌、单位或标签新关联到物品。历史物品保留已有的归档字典项和封面引用，并在详情/列表中显示原始名称。物品归档后不出现在 `requireActiveItem` 和阶段四新入库选择器中；恢复后再次成为可选项。

### 4.4 位置表

```sql
CREATE TABLE location (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household(id),
    parent_id       UUID,
    name            VARCHAR(100) NOT NULL,
    name_normalized VARCHAR(100) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    ever_referenced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_location_household_id UNIQUE (household_id, id),
    CONSTRAINT uq_location_name UNIQUE NULLS NOT DISTINCT
        (household_id, parent_id, name_normalized),
    CONSTRAINT fk_location_parent_same_household
        FOREIGN KEY (household_id, parent_id)
        REFERENCES location(household_id, id)
);

CREATE INDEX idx_location_household_parent_order
    ON location(household_id, parent_id, sort_order, id);
```

位置使用邻接表而非把路径写死在行内。读取整棵树使用递归 CTE 或按父节点分组构建；移动节点使用事务和递归祖先检查，目标父节点不能是节点自身或其后代。移动和排序会重新编号受影响兄弟节点的 `sort_order`，同一次请求携带被移动节点的 `version` 以防止静默覆盖。

`DELETE /locations/{id}` 仅在以下条件同时满足时物理删除：节点没有子节点、`ever_referenced = false`、调用者提交的版本仍匹配。阶段四在任意库存位或流水首次使用位置前调用 `markReferenced`；因此有当前库存或任何历史库存记录的位置都不会被删除，从而保留库存历史可解释性。

## 5. API 与错误契约

所有端点位于 `/api/v1`，受保护端点继续使用阶段二的同源会话和 CSRF Cookie/Header 机制。所有写入请求均通过 `@Valid` 校验并返回 RFC 7807 Problem Details；列表返回 `items`、`total`、`page`、`pageSize`，页码从 1 开始。

### 5.1 物品与封面

| 方法与路径 | 行为 | 授权 |
|---|---|---|
| `GET /items` | 分页列表；支持 `q`、`managementType`、`categoryId`、`brandId`、`tagId`、`status`、`page`、`pageSize`、`sort` | 活跃成员 |
| `POST /items` | 新建物品；所有关联字典必须属于当前家庭且为 `ACTIVE` | 活跃成员 |
| `GET /items/{id}` | 获取详情、标签、封面引用和配置 | 活跃成员 |
| `PUT /items/{id}` | 更新资料与配置；请求体包含 `version` | 活跃成员 |
| `POST /items/{id}/archive` | 将物品设为 `ARCHIVED` | 活跃成员 |
| `POST /items/{id}/restore` | 恢复为 `ACTIVE`；校验关联字典仍可用于选择 | 活跃成员 |
| `POST /items/{id}/cover` | `multipart/form-data`，字段为 `file` 和 `version`；原子替换封面 | 活跃成员 |
| `DELETE /items/{id}/cover` | 移除封面并释放文件引用；请求提供 `version` | 活跃成员 |
| `GET /files/{fileId}/content` | 输出受家庭成员校验保护的封面二进制 | 活跃成员 |

`sort` 只允许白名单字段 `name`、`createdAt`、`updatedAt`；不将客户端字符串直接拼入 SQL。分页大小范围为 1 至 100。列表缩略图 URL 由后端提供受保护的文件内容地址，不提供原始存储地址。

### 5.2 字典

| 资源 | 查询 | 新增 | 编辑、归档、恢复 |
|---|---|---|---|
| 分类 | `GET /categories/tree?includeArchived=false` | `POST /categories` | `PUT /categories/{id}`、`PUT /categories/{id}/position`、`POST /categories/{id}/archive`、`POST /categories/{id}/restore`；Owner/Admin |
| 品牌 | `GET /brands?includeArchived=false` | `POST /brands`；活跃成员 | `PUT /brands/{id}`、`POST /brands/{id}/archive`、`POST /brands/{id}/restore`；Owner/Admin |
| 单位 | `GET /units?includeArchived=false` | `POST /units`；Owner/Admin | `PUT /units/{id}`、`POST /units/{id}/archive`、`POST /units/{id}/restore`；Owner/Admin |
| 标签 | `GET /tags?includeArchived=false` | `POST /tags`；活跃成员 | `PUT /tags/{id}`、`POST /tags/{id}/archive`、`POST /tags/{id}/restore`；Owner/Admin |

所有编辑和位置变更请求都含 `version`。分类位置变更使用与位置树相同的 `parentId`、目标排序位置和循环检查。为了维护稳定字典，普通成员不能通过“新增同名值”间接恢复已归档品牌或标签。

### 5.3 位置

| 方法与路径 | 行为 | 授权 |
|---|---|---|
| `GET /locations/tree` | 返回当前家庭完整位置树及每个节点的 `id`、`parentId`、名称、排序和版本 | 活跃成员 |
| `GET /locations/{id}` | 返回节点、祖先路径和子节点；阶段三的库存摘要字段为空，不伪造库存数据 | 活跃成员 |
| `POST /locations` | 在指定父节点或根节点下新增位置 | 活跃成员 |
| `PUT /locations/{id}` | 重命名节点；请求体包含 `version` | 活跃成员 |
| `PUT /locations/{id}/position` | 移动子树或改变同级排序；请求体包含目标父节点、目标索引和 `version` | 活跃成员 |
| `DELETE /locations/{id}` | 仅删除未引用空叶节点 | 活跃成员 |

`LocationApi.markReferenced` 是模块内部服务调用，不暴露为 HTTP 端点。阶段三没有库存数据时，位置详情明确显示“库存将在阶段四启用”，而不是显示模拟数量、临期批次或最近流水。

### 5.4 稳定错误码

| 错误码 | 状态 | 触发条件 |
|---|---:|---|
| `CATALOG_VERSION_CONFLICT` | 409 | 物品或字典更新的乐观锁版本不匹配 |
| `CATALOG_ARCHIVED_DICTIONARY` | 409 | 新建/更新物品尝试关联归档字典项 |
| `CATALOG_UNIT_PRECISION_INVALID` | 422 | 低库存阈值超过基础单位允许的小数精度 |
| `CATALOG_DICTIONARY_NAME_EXISTS` | 409 | 同一家庭内新增或重命名后与活动/归档字典同名 |
| `CATALOG_CATEGORY_CYCLE` | 409 | 分类移动到自身或后代节点 |
| `CATALOG_CATEGORY_HAS_CHILDREN` | 409 | 归档含活动子分类的分类 |
| `CATALOG_UNIT_PRECISION_LOCKED` | 409 | 已被物品使用的单位尝试修改小数精度 |
| `FILE_TOO_LARGE` | 413 | 文件大于 5 MiB |
| `FILE_MEDIA_TYPE_UNSUPPORTED` | 415 | 声明或检测媒体类型不在 JPEG、PNG、WebP 范围内 |
| `FILE_SIGNATURE_MISMATCH` | 422 | 原文件扩展名或声明媒体类型与真实签名不一致 |
| `LOCATION_VERSION_CONFLICT` | 409 | 位置更新或移动版本不匹配 |
| `LOCATION_CYCLE` | 409 | 移动到自身或后代节点 |
| `LOCATION_HAS_CHILDREN` | 409 | 删除仍含子节点的位置 |
| `LOCATION_REFERENCED` | 409 | 删除 `ever_referenced = true` 的位置 |

资源不存在、跨家庭资源或已停用成员不返回任何其他家庭的存在信息。未认证、权限不足、CSRF 失败沿用 `AUTHENTICATION_REQUIRED`、`ACCESS_DENIED`、`CSRF_TOKEN_INVALID` 等阶段二错误契约。

## 6. 权限与审计

### 6.1 权限矩阵

| 能力 | Owner | Admin | Member |
|---|---:|---:|---:|
| 查看物品、字典、位置和封面 | 是 | 是 | 是 |
| 新建、编辑、归档、恢复物品及上传/移除封面 | 是 | 是 | 是 |
| 新增品牌、标签 | 是 | 是 | 是 |
| 重命名、归档、恢复品牌、标签 | 是 | 是 | 否 |
| 管理分类和单位 | 是 | 是 | 否 |
| 新增、重命名、排序、移动、删除符合条件的位置 | 是 | 是 | 是 |

控制器使用 `@RequireMember`、`@RequireAdmin` 和 `@RequireOwner` 强制授权；前端仅按角色隐藏或禁用无权限入口。任何请求均必须通过 `HouseholdApi.requireActiveMember`，停用成员不能读取封面或继续修改资料。

### 6.2 审计事件

每个成功的写操作调用 `SystemApi.recordAudit(...)`。事件 `detail` 仅含资源 UUID、变化字段名、原/新显示值（限长度）或文件校验和前缀，绝不含磁盘路径、完整原文件名、二进制内容、会话标识或 CSRF Token。

- `ITEM_CREATED`、`ITEM_UPDATED`、`ITEM_ARCHIVED`、`ITEM_RESTORED`
- `ITEM_COVER_UPLOADED`、`ITEM_COVER_REMOVED`
- `CATEGORY_CREATED`、`CATEGORY_UPDATED`、`CATEGORY_ARCHIVED`、`CATEGORY_RESTORED`
- `BRAND_CREATED`、`BRAND_UPDATED`、`BRAND_ARCHIVED`、`BRAND_RESTORED`
- `UNIT_CREATED`、`UNIT_UPDATED`、`UNIT_ARCHIVED`、`UNIT_RESTORED`
- `TAG_CREATED`、`TAG_UPDATED`、`TAG_ARCHIVED`、`TAG_RESTORED`
- `LOCATION_CREATED`、`LOCATION_RENAMED`、`LOCATION_MOVED`、`LOCATION_DELETED`

失败的业务校验不写成功审计事件；安全过滤器和现有请求日志继续记录 `requestId`，但不记录上传内容。

## 7. 前端设计

### 7.1 导航与路由

认证后 `AppShell` 启用“物品资料”和“位置管理”。Owner/Admin 可通过“家庭设置”进入物品字典页；普通成员看不到该入口，但可在物品表单内新增品牌和标签。阶段四至七的入口继续显示为未启用，避免误导用户进入空页面。

新增页面和主要组件职责如下：

| 路由或组件 | 职责 |
|---|---|
| `/items` 与 `ItemsPage` | 固定筛选栏、分页表格、创建入口和右侧详情抽屉 |
| `ItemFormDrawer` | 新建/编辑物品、单位精度提示、提醒配置和标签选择 |
| `ItemCoverUpload` | 封面预览、选择、替换、移除和服务端错误呈现 |
| `/settings/catalog` 与 `CatalogSettingsPage` | Owner/Admin 管理分类树、单位、品牌和标签 |
| `/locations` 与 `LocationsPage` | 树形工作区、节点工具栏和右侧详情/编辑抽屉 |
| `LocationMoveDialog` | 明确选择目标父节点和目标排序位置，展示循环或并发失败提示 |

### 7.2 物品资料工作流

物品页在 1280px 及以上显示封面缩略图、名称、管理类型、分类、品牌、单位、标签、低库存配置、状态和更新时间；在 1024px 时隐藏次要列但保留名称、类型、单位、状态与操作。筛选栏固定在表格上方，使用 Element Plus 输入、选择器、标签和分页组件。详情在右侧抽屉打开，编辑使用独立表单抽屉，不在表格单元格直接修改业务资料。

表单必填名称、管理类型和基础单位。品牌与标签选择器提供“新建”动作，但新建请求成功后才把服务端返回的字典项放入当前选择；不在客户端伪造 ID。封面在上传前显示类型和大小提示，上传后由服务端响应中的文件 ID 和受保护 URL 驱动预览。归档和移除封面属于可见影响操作，均需要确认。

### 7.3 位置工作流

位置页以可展开树呈现根节点及子节点，节点选择后在右侧抽屉显示完整路径、子节点、版本和阶段四库存占位说明。新增、重命名、移动和排序使用显式命令按钮与对话框；移动不能仅依赖拖放，因此键盘和辅助技术用户也可完成同一操作。删除按钮只在服务端预检结果允许时显示，实际请求仍必须处理 `LOCATION_HAS_CHILDREN`、`LOCATION_REFERENCED` 与版本冲突。

所有角色差异在 UI 中以禁用/隐藏控制表达，但不得替代后端授权。状态和错误同时使用文字、图标和颜色，不依赖颜色单独传达含义。

## 8. 配置、部署与恢复

新增配置项：

```dotenv
ZIJA_FILE_STORAGE_PATH=/var/lib/zija/files
```

后端将其绑定为 `zija.file.storage-path`，启动时创建或验证该目录可读、可写且不是临时工作目录。Compose 为应用挂载命名卷 `zija-files` 到该路径；宿主机部署文档说明该卷必须与 PostgreSQL 备份使用同一备份批次标识。

阶段三不实现完整备份/恢复命令（阶段七），但 README 必须说明：仅备份 PostgreSQL 不足以恢复封面；恢复时要先恢复数据库与同批次文件卷，再启动应用验证已存储的文件引用。Flyway 不提供 down migration；逻辑回退通过归档/恢复资料，灾难回退通过已验证的数据库与文件卷备份完成。

## 9. 测试策略

### 9.1 后端单元与 Web 测试

- `CatalogDictionaryService`：名称规范化、权限、分类移动循环、归档/恢复、分类活动子节点限制、单位精度锁定和重复值处理。
- `ItemService`：跨家庭隔离、归档/恢复、归档字典拒绝、阈值精度、提醒配置、标签替换和审计详情白名单。
- `FileContentInspector` 与 `FileService`：JPEG/PNG/WebP 签名、大小上限、媒体类型不匹配、事务回滚清理、替换与移除时的引用计数。
- `LocationService`：创建、重命名、同级排序、移动子树、循环拒绝、空叶删除、`markReferenced` 不可回退和乐观锁冲突。
- 控制器：所有 API 的认证、角色、CSRF、校验和 Problem Details；普通成员新增品牌/标签成功但维护字典失败；跨家庭 UUID 不泄露。

### 9.2 PostgreSQL 集成与模块测试

- Testcontainers 从 `V1` 至 `V10` 迁移空数据库，验证新表、约束、索引和 `UNIQUE NULLS NOT DISTINCT` 行为。
- Mapper 集成测试验证物品分页和白名单排序、分类/位置递归树、同级重排、乐观锁及跨家庭过滤。
- 文件集成测试使用每次测试独立的临时存储根目录，验证真实文件内容和数据库元数据在提交/回滚后的状态一致。
- `ModularityTests` 验证 `file`、`catalog`、`location` 的公开 API 边界和依赖方向；`OpenApiContractTest` 更新阶段三 API 基线。

### 9.3 前端与端到端测试

- Vitest：`ItemsPage` 筛选、分页、详情抽屉、表单字段错误、归档确认、角色可见性、封面上传失败；`CatalogSettingsPage` 的管理员控制；`LocationsPage` 的树加载、移动确认和 API 错误提示。
- API 模块测试：上传使用 CSRF Header、`FormData` 和统一 Problem Details 处理，不将文件内容写入 Pinia。
- Playwright：现有引导/登录场景之后，创建分类、单位、品牌和标签；创建消耗品与耐用品；上传合法封面；归档并恢复物品；创建位置层级；验证循环移动和越权字典维护被拒绝。
- Compose 冒烟：上传封面后重启应用容器，使用受保护端点仍能读取同一文件；验证文件卷未被容器重建清空。

## 10. 阶段验收标准

阶段三只有同时满足以下条件才算完成：

1. 活跃成员能从桌面 UI 创建、编辑、归档和恢复消耗品与耐用品资料，并在不使用真实成员姓名的测试数据中看到正确状态。
2. Owner/Admin 能维护分类与单位；所有成员能新增品牌和标签；权限不足的直接 API 调用返回 `403 ACCESS_DENIED`。
3. 所有物品只使用一个基础单位；不符合单位精度的阈值被后端以 `422 CATALOG_UNIT_PRECISION_INVALID` 拒绝。
4. 合法 JPEG、PNG、WebP 封面可上传、替换、移除和受保护读取；超限、伪造或不支持内容被稳定错误码拒绝；应用重启后合法封面仍存在。
5. 成员能建立位置层级并完成新增、重命名、排序和移动；循环、带子节点删除、已引用位置删除和并发覆盖被拒绝。
6. 阶段四可以通过 `CatalogApi` 和 `LocationApi` 验证物品、单位和位置，不需要访问这三个新模块的 `internal` 包或持久化表。
7. 后端测试、前端测试、生产构建、扩展后的 Playwright、Compose 冒烟、模块验证和 `git diff --check` 全部通过；README 与 `.env.example` 记录文件卷配置和恢复前提。
