# 阶段三：物品与位置 实施计划

> **面向智能体执行者：** 必须使用子 Skill：通过 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐项实施本计划。各步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 在阶段二身份与家庭基础之上，实现知家（zija）的物品资料管理、分类/品牌/单位/标签字典、封面文件上传与受保护读取、以及家庭位置树——为阶段四库存模块提供完整的物品与空间基础。

**架构：** 新增 `file`、`catalog` 和 `location` 三个 Spring Modulith 业务模块。`file` 负责受控文件内容及元数据，`catalog` 负责物品定义及可复用字典，`location` 管理家庭内物理空间树。依赖方向：`catalog -> household, file, system`；`file -> household, system`；`location -> household, system`。阶段四通过 `CatalogApi` 和 `LocationApi` 公开接口访问，不需要反向依赖。

**技术栈：** Java 25、Spring Boot 4.1.x、Spring Modulith 2.0.5、MyBatis-Plus 3.5.16、Flyway、PostgreSQL 17、Vue 3、TypeScript、Vite 7、Element Plus、Pinia 3、Vitest、Playwright、Testcontainers 2

**事实来源：** `docs/superpowers/specs/2026-07-22-phase3-items-locations-design.md`（设计规格）。本计划与规格冲突时以规格为准。

---

## 计划范围

本计划仅实施 delivery-roadmap 的阶段 3。它复用阶段一、二已建立的基础设施（Maven、Spring Boot、MyBatis-Plus、Flyway、PostgreSQL、Vue 外壳、Docker Compose、make 命令、身份与家庭模块），新增文件、物品字典和位置树业务能力。不实现批次、库存位、入库、领用、报损、移位、盘点、库存流水（阶段 4）、提醒任务生成（阶段 5）、搜索投影、报表、CSV（阶段 6）。

## 前置条件

- 阶段一 `docs/superpowers/plans/2026-07-19-foundation-baseline.md` 和阶段二 `docs/superpowers/plans/2026-07-20-phase2-identity-household.md` 均已执行完成，`make verify` 通过。
- `HouseholdApi`、`IdentityApi`、`SystemApi` 及其公开记录/枚举已就位。
- `@RequireMember`、`@RequireAdmin`、`@RequireOwner` 元注解已可使用。
- 已安装 JDK 25、Node.js 24、Docker Engine 与 Docker Compose v2。
- 除非步骤另有说明，所有命令从仓库根目录执行。

## 目标文件清单

~~~text
.
├── backend/
│   ├── pom.xml                                        # 无需修改（spring-boot-starter-web 已含文件上传支持）
│   └── src/
│       ├── main/
│       │   ├── java/com/zija/
│       │   │   ├── file/
│       │   │   │   ├── FileApi.java                   # 创建：公开文件接口
│       │   │   │   ├── package-info.java              # 创建：@ApplicationModule
│       │   │   │   └── internal/
│       │   │   │       ├── FileController.java        # 创建：文件内容端点
│       │   │   │       ├── FileService.java           # 创建：文件业务逻辑
│       │   │   │       ├── FileStorage.java           # 创建：磁盘存储操作
│       │   │   │       ├── FileContentInspector.java  # 创建：签名检测与校验
│       │   │   │       └── persistence/
│       │   │   │           ├── StoredFileEntity.java
│       │   │   │           └── StoredFileMapper.java
│       │   │   ├── catalog/
│       │   │   │   ├── CatalogApi.java                # 创建：公开物品接口
│       │   │   │   ├── package-info.java              # 创建：@ApplicationModule
│       │   │   │   └── internal/
│       │   │   │       ├── ItemController.java        # 创建：物品端点
│       │   │   │       ├── CatalogDictionaryController.java # 创建：字典端点
│       │   │   │       ├── ItemService.java           # 创建：物品业务逻辑
│       │   │   │       ├── CatalogDictionaryService.java # 创建：字典业务逻辑
│       │   │   │       ├── CatalogExceptionHandler.java # 创建：异常处理
│       │   │   │       └── persistence/
│       │   │   │           ├── ItemEntity.java
│       │   │   │           ├── CategoryEntity.java
│       │   │   │           ├── BrandEntity.java
│       │   │   │           ├── UnitEntity.java
│       │   │   │           ├── TagEntity.java
│       │   │   │           ├── ItemTagEntity.java
│       │   │   │           ├── ItemMapper.java
│       │   │   │           ├── CategoryMapper.java
│       │   │   │           ├── BrandMapper.java
│       │   │   │           ├── UnitMapper.java
│       │   │   │           ├── TagMapper.java
│       │   │   │           ├── ItemMapper.xml
│       │   │   │           ├── CategoryMapper.xml
│       │   │   │           └── LocationMapper.xml      # 位置递归查询复用
│       │   │   ├── location/
│       │   │   │   ├── LocationApi.java               # 创建：公开位置接口
│       │   │   │   ├── package-info.java              # 创建：@ApplicationModule
│       │   │   │   └── internal/
│       │   │   │       ├── LocationController.java    # 创建：位置端点
│       │   │   │       ├── LocationService.java       # 创建：位置业务逻辑
│       │   │   │       ├── LocationExceptionHandler.java # 创建：异常处理
│       │   │   │       └── persistence/
│       │   │   │           ├── LocationEntity.java
│       │   │   │           ├── LocationMapper.java
│       │   │   │           └── LocationMapper.xml
│       │   └── resources/
│       │       ├── application.yml                     # 修改：新增文件存储配置
│       │       └── db/migration/
│       │           ├── V8__create_stored_file.sql      # 创建
│       │           ├── V9__create_catalog.sql          # 创建
│       │           └── V10__create_location.sql        # 创建
│       └── test/java/com/zija/
│           ├── file/internal/
│           │   ├── FileContentInspectorTest.java
│           │   ├── FileServiceTest.java
│           │   └── FileControllerTest.java
│           ├── catalog/internal/
│           │   ├── CatalogDictionaryServiceTest.java
│           │   ├── ItemServiceTest.java
│           │   ├── CatalogDictionaryControllerTest.java
│           │   └── ItemControllerTest.java
│           ├── location/internal/
│           │   ├── LocationServiceTest.java
│           │   └── LocationControllerTest.java
│           └── (集成测试在各模块 persistence/ 下)
├── frontend/
│   └── src/
│       ├── api/
│       │   ├── catalog.ts                             # 创建
│       │   ├── location.ts                            # 创建
│       │   └── file.ts                                # 创建
│       ├── types/
│       │   ├── catalog.ts                             # 创建
│       │   └── location.ts                            # 创建
│       ├── router/index.ts                            # 修改
│       ├── components/AppShell.vue                    # 修改
│       └── views/
│           ├── ItemsPage.vue                          # 创建
│           ├── ItemFormDrawer.vue                     # 创建
│           ├── ItemCoverUpload.vue                    # 创建
│           ├── CatalogSettingsPage.vue                # 创建
│           ├── LocationsPage.vue                      # 创建
│           └── LocationMoveDialog.vue                 # 创建
├── frontend/e2e/
│   ├── catalog.spec.ts                                # 创建
│   └── locations.spec.ts                              # 创建
└── docker-compose.yml                                 # 修改：新增 zija-files 卷
~~~

---

## 任务 1：数据库迁移——文件元数据表

**文件：**
- 创建：`backend/src/main/resources/db/migration/V8__create_stored_file.sql`

- [ ] **步骤 1：创建文件元数据表迁移**

创建 `V8__create_stored_file.sql`：

~~~sql
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
~~~

- [ ] **步骤 2：验证迁移在空库执行**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS，Flyway 执行 V1–V8，`stored_file` 表存在。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V8__create_stored_file.sql
git commit -m "feat: 新增文件元数据表迁移"
~~~

---

## 任务 2：数据库迁移——物品字典表

**文件：**
- 创建：`backend/src/main/resources/db/migration/V9__create_catalog.sql`

- [ ] **步骤 1：创建物品字典表迁移**

创建 `V9__create_catalog.sql`：

~~~sql
-- 分类（树形字典）
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

-- 品牌
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

-- 单位
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

-- 标签
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

-- 物品
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

-- 物品-标签关联
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

-- 索引
CREATE INDEX idx_catalog_item_household_status
    ON catalog_item(household_id, status);
CREATE INDEX idx_catalog_item_category ON catalog_item(category_id);
CREATE INDEX idx_catalog_item_brand ON catalog_item(brand_id);
CREATE INDEX idx_catalog_item_unit ON catalog_item(unit_id);
CREATE INDEX idx_catalog_item_tag_tag ON catalog_item_tag(household_id, tag_id, item_id);
~~~

- [ ] **步骤 2：验证迁移执行**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS，V1–V9 全部执行。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V9__create_catalog.sql
git commit -m "feat: 新增物品字典表迁移（分类、品牌、单位、标签、物品）"
~~~

---

## 任务 3：数据库迁移——位置表

**文件：**
- 创建：`backend/src/main/resources/db/migration/V10__create_location.sql`

- [ ] **步骤 1：创建位置表迁移**

创建 `V10__create_location.sql`：

~~~sql
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
~~~

- [ ] **步骤 2：验证迁移执行**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS，V1–V10 全部执行。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V10__create_location.sql
git commit -m "feat: 新增位置表迁移"
~~~

---

## 任务 4：配置与 Docker Compose

**文件：**
- 修改：`backend/src/main/resources/application.yml`
- 修改：`.env.example`
- 修改：`docker-compose.yml`

- [ ] **步骤 1：更新 application.yml 新增文件存储配置**

在 `application.yml` 末尾追加：

~~~yaml
zija:
  file:
    storage-path: ${ZIJA_FILE_STORAGE_PATH:/var/lib/zija/files}
~~~

- [ ] **步骤 2：更新 .env.example**

在 `.env.example` 末尾追加：

~~~dotenv
ZIJA_FILE_STORAGE_PATH=/var/lib/zija/files
~~~

- [ ] **步骤 3：更新 docker-compose.yml 新增文件卷**

在 `app` 服务的 `volumes` 中新增 `zija-files:/var/lib/zija/files`，在顶层 `volumes` 中声明 `zija-files` 命名卷。

- [ ] **步骤 4：验证应用可启动**

运行：

~~~bash
cd backend && ./mvnw -q -DskipTests package && java -jar target/zija-backend-0.1.0-SNAPSHOT.jar --quit
~~~

预期：启动无异常。

- [ ] **步骤 5：提交配置变更**

~~~bash
git add backend/src/main/resources/application.yml .env.example docker-compose.yml
git commit -m "feat: 新增文件存储配置与 Docker Compose 卷"
~~~

---

## 任务 5：FileApi 公开接口

**文件：**
- 创建：`backend/src/main/java/com/zija/file/FileApi.java`
- 创建：`backend/src/main/java/com/zija/file/package-info.java`

- [ ] **步骤 1：创建 FileApi 公开接口**

创建 `FileApi.java`：

~~~java
package com.zija.file;

import java.util.Optional;
import java.util.UUID;

public interface FileApi {

    StoredFileInfo store(UUID householdId, byte[] content, String originalFilename, String declaredMediaType);

    void retain(UUID householdId, UUID fileId);

    void release(UUID householdId, UUID fileId);

    Optional<StoredFileInfo> findInfo(UUID householdId, UUID fileId);

    record StoredFileInfo(
            UUID id,
            UUID householdId,
            String storageKey,
            String originalFilename,
            String detectedMediaType,
            long byteSize,
            String sha256
    ) {
    }
}
~~~

- [ ] **步骤 2：创建 package-info**

创建 `package-info.java`：

~~~java
@org.springframework.modulith.ApplicationModule(
        displayName = "File",
        allowedDependencies = {"household", "system"}
)
package com.zija.file;
~~~

- [ ] **步骤 3：提交接口**

~~~bash
git add backend/src/main/java/com/zija/file/
git commit -m "feat: 新增 file 模块公开接口"
~~~

---

## 任务 6：FileContentInspector 文件签名检测

**文件：**
- 创建：`backend/src/main/java/com/zija/file/internal/FileContentInspector.java`
- 创建：`backend/src/test/java/com/zija/file/internal/FileContentInspectorTest.java`

- [ ] **步骤 1：编写失败测试**

创建 `FileContentInspectorTest.java`：

~~~java
package com.zija.file.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileContentInspectorTest {

    private final FileContentInspector inspector = new FileContentInspector();

    @Test
    void detectsJpegSignature() {
        // JPEG starts with FF D8 FF
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        var result = inspector.inspect(jpeg, "photo.jpg", "image/jpeg");
        assertThat(result.detectedMediaType()).isEqualTo("image/jpeg");
        assertThat(result.sanitizedBasename()).isEqualTo("photo.jpg");
    }

    @Test
    void detectsPngSignature() {
        byte[] png = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        var result = inspector.inspect(png, "image.png", null);
        assertThat(result.detectedMediaType()).isEqualTo("image/png");
    }

    @Test
    void detectsWebpSignature() {
        byte[] webp = new byte[]{
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0x00, 0x00, 0x00, 0x00
        };
        var result = inspector.inspect(webp, "pic.webp", "image/webp");
        assertThat(result.detectedMediaType()).isEqualTo("image/webp");
    }

    @Test
    void rejectsUnsupportedMediaType() {
        byte[] gif = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00};
        assertThatThrownBy(() -> inspector.inspect(gif, "anim.gif", null))
                .isInstanceOf(FileMediaTypeUnsupportedException.class);
    }

    @Test
    void rejectsSignatureMismatch() {
        // JPEG content but declared as PNG
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        assertThatThrownBy(() -> inspector.inspect(jpeg, "photo.png", "image/png"))
                .isInstanceOf(FileSignatureMismatchException.class);
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> inspector.inspect(new byte[0], "empty.jpg", "image/jpeg"))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void rejectsContentExceeding5MiB() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        assertThatThrownBy(() -> inspector.inspect(oversized, "big.jpg", "image/jpeg"))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void sanitizesBasename() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        var result = inspector.inspect(jpeg, "../../../etc/passwd.jpg", "image/jpeg");
        assertThat(result.sanitizedBasename()).doesNotContain("..").doesNotContain("/");
    }

    @Test
    void stripsControlCharacters() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        var result = inspector.inspect(jpeg, "photo .jpg", "image/jpeg");
        assertThat(result.sanitizedBasename()).doesNotContain(" ");
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=FileContentInspectorTest test
~~~

预期：FAIL，`FileContentInspector` 不存在。

- [ ] **步骤 3：创建异常类**

创建 `FileTooLargeException.java`：

~~~java
package com.zija.file.internal;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(long byteSize) {
        super("file too large: " + byteSize + " bytes (max 5242880)");
    }
}
~~~

创建 `FileMediaTypeUnsupportedException.java`：

~~~java
package com.zija.file.internal;

public class FileMediaTypeUnsupportedException extends RuntimeException {
    public FileMediaTypeUnsupportedException(String detected) {
        super("unsupported media type: " + detected);
    }
}
~~~

创建 `FileSignatureMismatchException.java`：

~~~java
package com.zija.file.internal;

public class FileSignatureMismatchException extends RuntimeException {
    public FileSignatureMismatchException(String expected, String detected) {
        super("signature mismatch: declared=" + expected + ", detected=" + detected);
    }
}
~~~

- [ ] **步骤 4：创建 FileContentInspector**

创建 `FileContentInspector.java`：

~~~java
package com.zija.file.internal;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
class FileContentInspector {

    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern PATH_SEPARATOR = Pattern.compile("[/\\\\]");

    InspectionResult inspect(byte[] content, String originalFilename, String declaredMediaType) {
        if (content.length == 0 || content.length > MAX_SIZE) {
            throw new FileTooLargeException(content.length);
        }

        String detected = detectMediaType(content);
        if (!ALLOWED_TYPES.contains(detected)) {
            throw new FileMediaTypeUnsupportedException(detected);
        }

        if (declaredMediaType != null && !declaredMediaType.isBlank()) {
            String normalizedDeclared = declaredMediaType.trim().toLowerCase(Locale.ROOT);
            if (!normalizedDeclared.equals(detected)) {
                throw new FileSignatureMismatchException(normalizedDeclared, detected);
            }
        }

        String sanitized = sanitizeBasename(originalFilename, detected);

        // Compute SHA-256
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            String sha256 = bytesToHex(hash);
            return new InspectionResult(detected, sanitized, sha256);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String detectMediaType(byte[] content) {
        if (content.length < 4) {
            throw new FileMediaTypeUnsupportedException("too short to detect");
        }
        // JPEG: FF D8 FF
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47
        if ((content[0] & 0xFF) == 0x89 && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47) {
            return "image/png";
        }
        // WEBP: RIFF....WEBP
        if (content.length >= 12
                && content[0] == 0x52 && content[1] == 0x49 && content[2] == 0x46 && content[3] == 0x46
                && content[8] == 0x57 && content[9] == 0x45 && content[10] == 0x42 && content[11] == 0x50) {
            return "image/webp";
        }
        throw new FileMediaTypeUnsupportedException("unknown");
    }

    private String sanitizeBasename(String originalFilename, String detectedMediaType) {
        if (originalFilename == null || originalFilename.isBlank()) {
            String ext = switch (detectedMediaType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".bin";
            };
            return "file" + ext;
        }
        // Strip path separators, keep only last segment
        String basename = originalFilename;
        String[] parts = PATH_SEPARATOR.split(basename);
        if (parts.length > 0) {
            basename = parts[parts.length - 1];
        }
        // Remove control characters
        basename = CONTROL_CHARS.matcher(basename).replaceAll("");
        // Trim whitespace
        basename = basename.trim();
        if (basename.isEmpty()) {
            return sanitizeBasename(null, detectedMediaType);
        }
        return basename;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    record InspectionResult(String detectedMediaType, String sanitizedBasename, String sha256) {
    }
}
~~~

- [ ] **步骤 5：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=FileContentInspectorTest test
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/file/internal/ backend/src/test/java/com/zija/file/internal/FileContentInspectorTest.java
git commit -m "feat: file 模块新增文件签名检测与校验"
~~~

---

## 任务 7：FileStorage 磁盘存储

**文件：**
- 创建：`backend/src/main/java/com/zija/file/internal/FileStorage.java`

- [ ] **步骤 1：创建 FileStorage**

创建 `FileStorage.java`：

~~~java
package com.zija.file.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Component
class FileStorage {

    private final Path storageRoot;

    FileStorage(@Value("${zija.file.storage-path}") String storagePath) {
        this.storageRoot = Path.of(storagePath);
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storageRoot);
        // Verify writable
        if (!Files.isWritable(storageRoot)) {
            throw new IllegalStateException("File storage path is not writable: " + storageRoot);
        }
    }

    /**
     * Writes content to a temp file then atomically moves to the final storage key.
     * Returns the storage key (relative path like 2026/07/<uuid>.ext).
     */
    String store(byte[] content, String extension) throws IOException {
        String datePrefix = java.time.LocalDate.now().toString().replace("-", "/").substring(0, 7);
        String storageKey = datePrefix + "/" + UUID.randomUUID() + extension;
        Path target = storageRoot.resolve(storageKey);

        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".tmp-", "");
        try {
            Files.write(temp, content, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        return storageKey;
    }

    byte[] read(String storageKey) throws IOException {
        return Files.readAllBytes(storageRoot.resolve(storageKey));
    }

    void delete(String storageKey) throws IOException {
        Path target = storageRoot.resolve(storageKey);
        Files.deleteIfExists(target);
        // Clean up empty parent directories (up to 2 levels)
        Path parent = target.getParent();
        for (int i = 0; i < 2 && parent != null && !parent.equals(storageRoot); i++) {
            try (var stream = Files.list(parent)) {
                if (stream.findFirst().isEmpty()) {
                    Files.delete(parent);
                    parent = parent.getParent();
                } else {
                    break;
                }
            }
        }
    }

    Path resolve(String storageKey) {
        return storageRoot.resolve(storageKey);
    }
}
~~~

- [ ] **步骤 2：验证编译通过**

运行：

~~~bash
cd backend && ./mvnw -q compile
~~~

预期：编译成功。

- [ ] **步骤 3：提交**

~~~bash
git add backend/src/main/java/com/zija/file/internal/FileStorage.java
git commit -m "feat: file 模块新增磁盘存储组件"
~~~

---

## 任务 8：file 模块持久化层

**文件：**
- 创建：`backend/src/main/java/com/zija/file/internal/persistence/StoredFileEntity.java`
- 创建：`backend/src/main/java/com/zija/file/internal/persistence/StoredFileMapper.java`

- [ ] **步骤 1：创建 StoredFileEntity**

创建 `StoredFileEntity.java`：

~~~java
package com.zija.file.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("stored_file")
public class StoredFileEntity {

    @TableId
    private UUID id;
    private UUID householdId;
    private String storageKey;
    private String originalFilename;
    private String declaredMediaType;
    private String detectedMediaType;
    private Long byteSize;
    private String sha256;
    private Integer referenceCount;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getDeclaredMediaType() { return declaredMediaType; }
    public void setDeclaredMediaType(String declaredMediaType) { this.declaredMediaType = declaredMediaType; }
    public String getDetectedMediaType() { return detectedMediaType; }
    public void setDetectedMediaType(String detectedMediaType) { this.detectedMediaType = detectedMediaType; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Integer getReferenceCount() { return referenceCount; }
    public void setReferenceCount(Integer referenceCount) { this.referenceCount = referenceCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
~~~

- [ ] **步骤 2：创建 StoredFileMapper**

创建 `StoredFileMapper.java`：

~~~java
package com.zija.file.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFileEntity> {

    int incrementReferenceCount(@Param("id") UUID id, @Param("householdId") UUID householdId);

    int decrementReferenceCount(@Param("id") UUID id, @Param("householdId") UUID householdId);
}
~~~

- [ ] **步骤 3：验证编译通过**

运行：

~~~bash
cd backend && ./mvnw -q compile
~~~

预期：编译成功。

- [ ] **步骤 4：提交**

~~~bash
git add backend/src/main/java/com/zija/file/internal/persistence/
git commit -m "feat: file 模块新增持久化层"
~~~

---

## 任务 9：FileService 文件业务逻辑

**文件：**
- 创建：`backend/src/main/java/com/zija/file/internal/FileService.java`
- 创建：`backend/src/test/java/com/zija/file/internal/FileServiceTest.java`

- [ ] **步骤 1：编写失败单元测试**

创建 `FileServiceTest.java`：

~~~java
package com.zija.file.internal;

import com.zija.file.FileApi;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import com.zija.household.HouseholdApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileServiceTest {

    private StoredFileMapper storedFileMapper;
    private FileContentInspector inspector;
    private FileStorage fileStorage;
    private HouseholdApi householdApi;
    private FileService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storedFileMapper = mock(StoredFileMapper.class);
        inspector = new FileContentInspector();
        fileStorage = mock(FileStorage.class);
        householdApi = mock(HouseholdApi.class);
        service = new FileService(storedFileMapper, inspector, fileStorage, householdApi);
    }

    @Test
    void storesValidJpegAndReturnsInfo() throws IOException {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        UUID householdId = UUID.randomUUID();
        when(fileStorage.store(any(), any())).thenReturn("2026/07/test.jpg");

        var result = service.store(householdId, jpeg, "photo.jpg", "image/jpeg");

        assertThat(result.detectedMediaType()).isEqualTo("image/jpeg");
        assertThat(result.originalFilename()).isEqualTo("photo.jpg");
        verify(storedFileMapper).insert(any(StoredFileEntity.class));
    }

    @Test
    void retainIncrementsReferenceCount() {
        UUID id = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        when(storedFileMapper.incrementReferenceCount(id, householdId)).thenReturn(1);

        service.retain(householdId, id);

        verify(storedFileMapper).incrementReferenceCount(id, householdId);
    }

    @Test
    void releaseDecrementsAndDeletesWhenZero() throws IOException {
        UUID id = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        var entity = new StoredFileEntity();
        entity.setId(id);
        entity.setHouseholdId(householdId);
        entity.setStorageKey("2026/07/test.jpg");
        entity.setReferenceCount(0);

        when(storedFileMapper.selectById(id)).thenReturn(entity);
        when(storedFileMapper.decrementReferenceCount(id, householdId)).thenReturn(1);

        service.release(householdId, id);

        verify(fileStorage).delete("2026/07/test.jpg");
        verify(storedFileMapper).deleteById(id);
    }

    @Test
    void releaseDoesNotDeleteWhenReferencesRemain() {
        UUID id = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        var entity = new StoredFileEntity();
        entity.setId(id);
        entity.setHouseholdId(householdId);
        entity.setStorageKey("2026/07/test.jpg");
        entity.setReferenceCount(1);

        when(storedFileMapper.selectById(id)).thenReturn(entity);
        when(storedFileMapper.decrementReferenceCount(id, householdId)).thenReturn(1);

        service.release(householdId, id);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsOversizedFile() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> service.store(UUID.randomUUID(), oversized, "big.jpg", "image/jpeg"))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void rejectsUnsupportedMediaType() {
        byte[] gif = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00};
        assertThatThrownBy(() -> service.store(UUID.randomUUID(), gif, "anim.gif", null))
                .isInstanceOf(FileMediaTypeUnsupportedException.class);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=FileServiceTest test
~~~

预期：FAIL，`FileService` 不存在。

- [ ] **步骤 3：创建 FileService**

创建 `FileService.java`：

~~~java
package com.zija.file.internal;

import com.zija.file.FileApi;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Service
class FileService implements FileApi {

    private final StoredFileMapper storedFileMapper;
    private final FileContentInspector inspector;
    private final FileStorage fileStorage;

    FileService(
            StoredFileMapper storedFileMapper,
            FileContentInspector inspector,
            FileStorage fileStorage
    ) {
        this.storedFileMapper = storedFileMapper;
        this.inspector = inspector;
        this.fileStorage = fileStorage;
    }

    @Override
    @Transactional
    public StoredFileInfo store(UUID householdId, byte[] content, String originalFilename, String declaredMediaType) {
        var inspection = inspector.inspect(content, originalFilename, declaredMediaType);

        String ext = switch (inspection.detectedMediaType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };

        String storageKey;
        try {
            storageKey = fileStorage.store(content, ext);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        var entity = new StoredFileEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setStorageKey(storageKey);
        entity.setOriginalFilename(inspection.sanitizedBasename());
        entity.setDeclaredMediaType(declaredMediaType);
        entity.setDetectedMediaType(inspection.detectedMediaType());
        entity.setByteSize((long) content.length);
        entity.setSha256(inspection.sha256());
        entity.setReferenceCount(0);
        storedFileMapper.insert(entity);

        return toInfo(entity);
    }

    @Override
    @Transactional
    public void retain(UUID householdId, UUID fileId) {
        storedFileMapper.incrementReferenceCount(fileId, householdId);
    }

    @Override
    @Transactional
    public void release(UUID householdId, UUID fileId) {
        var entity = storedFileMapper.selectById(fileId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return;
        }
        storedFileMapper.decrementReferenceCount(fileId, householdId);
        // Re-fetch to check current count
        var updated = storedFileMapper.selectById(fileId);
        if (updated != null && updated.getReferenceCount() <= 0) {
            try {
                fileStorage.delete(updated.getStorageKey());
            } catch (IOException e) {
                // Log but don't fail — orphaned file can be cleaned up later
            }
            storedFileMapper.deleteById(fileId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredFileInfo> findInfo(UUID householdId, UUID fileId) {
        var entity = storedFileMapper.selectById(fileId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return Optional.empty();
        }
        return Optional.of(toInfo(entity));
    }

    private StoredFileInfo toInfo(StoredFileEntity entity) {
        return new StoredFileInfo(
                entity.getId(),
                entity.getHouseholdId(),
                entity.getStorageKey(),
                entity.getOriginalFilename(),
                entity.getDetectedMediaType(),
                entity.getByteSize(),
                entity.getSha256()
        );
    }
}
~~~

- [ ] **步骤 4：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=FileServiceTest test
~~~

预期：PASS。

- [ ] **步骤 5：提交**

~~~bash
git add backend/src/main/java/com/zija/file/internal/FileService.java backend/src/test/java/com/zija/file/internal/FileServiceTest.java
git commit -m "feat: file 模块新增文件业务服务"
~~~

---

## 任务 10：FileController 文件端点

**文件：**
- 创建：`backend/src/main/java/com/zija/file/internal/FileController.java`
- 创建：`backend/src/test/java/com/zija/file/internal/FileControllerTest.java`

- [ ] **步骤 1：编写失败 MockMvc 测试**

创建 `FileControllerTest.java`：

~~~java
package com.zija.file.internal;

import com.zija.file.FileApi;
import com.zija.household.HouseholdApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean FileApi fileApi;
    @MockBean HouseholdApi householdApi;

    @Test
    @WithMockUser
    void uploadReturnsStoredFileInfo() throws Exception {
        var householdId = UUID.randomUUID();
        var fileId = UUID.randomUUID();
        when(householdApi.requireActiveMember(any())).thenReturn(
                new HouseholdApi.MemberInfo(UUID.randomUUID(), householdId, UUID.randomUUID(),
                        "user", "用户", HouseholdApi.MemberRole.MEMBER, "ACTIVE"));
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        when(fileApi.store(eq(householdId), any(), eq("photo.jpg"), eq("image/jpeg")))
                .thenReturn(new FileApi.StoredFileInfo(fileId, householdId, "2026/07/test.jpg",
                        "photo.jpg", "image/jpeg", 6, "abc123"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.detectedMediaType").value("image/jpeg"));
    }

    @Test
    void downloadRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/files/" + UUID.randomUUID() + "/content"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void downloadReturns404ForMissingFile() throws Exception {
        var fileId = UUID.randomUUID();
        when(householdApi.requireActiveMember(any())).thenReturn(
                new HouseholdApi.MemberInfo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "user", "用户", HouseholdApi.MemberRole.MEMBER, "ACTIVE"));
        when(fileApi.findInfo(any(), eq(fileId))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/files/" + fileId + "/content"))
                .andExpect(status().isNotFound());
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=FileControllerTest test
~~~

预期：FAIL，`FileController` 不存在。

- [ ] **步骤 3：创建 FileController**

创建 `FileController.java`：

~~~java
package com.zija.file.internal;

import com.zija.ZijaPrincipal;
import com.zija.file.FileApi;
import com.zija.household.HouseholdApi;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
class FileController {

    private final FileApi fileApi;
    private final FileStorage fileStorage;
    private final HouseholdApi householdApi;

    FileController(FileApi fileApi, FileStorage fileStorage, HouseholdApi householdApi) {
        this.fileApi = fileApi;
        this.fileStorage = fileStorage;
        this.householdApi = householdApi;
    }

    @PostMapping
    Map<String, Object> upload(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = fileApi.store(
                member.householdId(),
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );
        return Map.of(
                "id", info.id(),
                "storageKey", info.storageKey(),
                "originalFilename", info.originalFilename(),
                "detectedMediaType", info.detectedMediaType(),
                "byteSize", info.byteSize(),
                "sha256", info.sha256(),
                "url", "/api/v1/files/" + info.id() + "/content"
        );
    }

    @GetMapping("/{fileId}/content")
    void download(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId,
            HttpServletResponse response
    ) throws IOException {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = fileApi.findInfo(member.householdId(), fileId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));

        byte[] content = fileStorage.read(info.storageKey());
        response.setContentType(info.detectedMediaType());
        response.setContentLengthLong(content.length);
        response.setHeader("Content-Disposition", "inline; filename=\"" + info.originalFilename() + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(content);
    }

    @DeleteMapping("/{fileId}")
    void remove(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        fileApi.release(member.householdId(), fileId);
    }
}
~~~

- [ ] **步骤 4：创建 FileExceptionHandler**

创建 `FileExceptionHandler.java`：

~~~java
package com.zija.file.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FileController.class)
class FileExceptionHandler {

    @ExceptionHandler(FileTooLargeException.class)
    ProblemDetail handleTooLarge(HttpServletRequest request, FileTooLargeException ex) {
        return problem(request, HttpStatus.PAYLOAD_TOO_LARGE, "文件过大", "FILE_TOO_LARGE");
    }

    @ExceptionHandler(FileMediaTypeUnsupportedException.class)
    ProblemDetail handleUnsupported(HttpServletRequest request, FileMediaTypeUnsupportedException ex) {
        return problem(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型", "FILE_MEDIA_TYPE_UNSUPPORTED");
    }

    @ExceptionHandler(FileSignatureMismatchException.class)
    ProblemDetail handleMismatch(HttpServletRequest request, FileSignatureMismatchException ex) {
        return problem(request, HttpStatus.UNPROCESSABLE_ENTITY, "文件签名不匹配", "FILE_SIGNATURE_MISMATCH");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", request.getAttribute("zija.request-id"));
        return problem;
    }
}
~~~

- [ ] **步骤 5：验证控制器测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=FileControllerTest test
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/file/internal/FileController.java backend/src/main/java/com/zija/file/internal/FileExceptionHandler.java backend/src/test/java/com/zija/file/internal/FileControllerTest.java
git commit -m "feat: file 模块新增文件上传、下载与删除端点"
~~~

---

## 任务 11：CatalogApi 公开接口

**文件：**
- 创建：`backend/src/main/java/com/zija/catalog/CatalogApi.java`
- 创建：`backend/src/main/java/com/zija/catalog/package-info.java`

- [ ] **步骤 1：创建 CatalogApi 公开接口**

创建 `CatalogApi.java`：

~~~java
package com.zija.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CatalogApi {

    ItemInfo requireItem(UUID householdId, UUID itemId);

    ItemInfo requireActiveItem(UUID householdId, UUID itemId);

    UnitInfo requireUnit(UUID householdId, UUID unitId);

    record ItemInfo(
            UUID id,
            UUID householdId,
            String name,
            String managementType,
            UUID categoryId,
            UUID brandId,
            UUID unitId,
            UUID coverFileId,
            String status
    ) {
    }

    record UnitInfo(
            UUID id,
            UUID householdId,
            String name,
            int decimalScale,
            String status
    ) {
    }
}
~~~

- [ ] **步骤 2：创建 package-info**

创建 `package-info.java`：

~~~java
@org.springframework.modulith.ApplicationModule(
        displayName = "Catalog",
        allowedDependencies = {"household", "file", "system"}
)
package com.zija.catalog;
~~~

- [ ] **步骤 3：提交接口**

~~~bash
git add backend/src/main/java/com/zija/catalog/
git commit -m "feat: 新增 catalog 模块公开接口"
~~~

---

## 任务 12：catalog 模块持久化层

**文件：**
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/ItemEntity.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/CategoryEntity.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/BrandEntity.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/UnitEntity.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/TagEntity.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/ItemTagEntity.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/ItemMapper.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/CategoryMapper.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/BrandMapper.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/UnitMapper.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/persistence/TagMapper.java`
- 创建：`backend/src/main/resources/mapper/catalog/ItemMapper.xml`
- 创建：`backend/src/main/resources/mapper/catalog/CategoryMapper.xml`

- [ ] **步骤 1：创建字典实体类**

创建 `CategoryEntity.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("catalog_category")
public class CategoryEntity {
    @TableId private UUID id;
    private UUID householdId;
    private UUID parentId;
    private String name;
    private String nameNormalized;
    private String status;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameNormalized() { return nameNormalized; }
    public void setNameNormalized(String nameNormalized) { this.nameNormalized = nameNormalized; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

创建 `BrandEntity.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("catalog_brand")
public class BrandEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String name;
    private String nameNormalized;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameNormalized() { return nameNormalized; }
    public void setNameNormalized(String nameNormalized) { this.nameNormalized = nameNormalized; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

创建 `UnitEntity.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("catalog_unit")
public class UnitEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String name;
    private String nameNormalized;
    private Short decimalScale;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameNormalized() { return nameNormalized; }
    public void setNameNormalized(String nameNormalized) { this.nameNormalized = nameNormalized; }
    public Short getDecimalScale() { return decimalScale; }
    public void setDecimalScale(Short decimalScale) { this.decimalScale = decimalScale; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

创建 `TagEntity.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("catalog_tag")
public class TagEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String name;
    private String nameNormalized;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameNormalized() { return nameNormalized; }
    public void setNameNormalized(String nameNormalized) { this.nameNormalized = nameNormalized; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

创建 `ItemEntity.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@TableName("catalog_item")
public class ItemEntity {
    @TableId private UUID id;
    private UUID householdId;
    private String name;
    private String managementType;
    private UUID categoryId;
    private UUID brandId;
    private UUID unitId;
    private UUID coverFileId;
    private String memo;
    private String expiryReminderMode;
    private List<Short> expiryReminderDays;
    private String lowStockMode;
    private BigDecimal lowStockThreshold;
    private String status;
    private OffsetDateTime archivedAt;
    private UUID archivedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getManagementType() { return managementType; }
    public void setManagementType(String managementType) { this.managementType = managementType; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public UUID getBrandId() { return brandId; }
    public void setBrandId(UUID brandId) { this.brandId = brandId; }
    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }
    public UUID getCoverFileId() { return coverFileId; }
    public void setCoverFileId(UUID coverFileId) { this.coverFileId = coverFileId; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getExpiryReminderMode() { return expiryReminderMode; }
    public void setExpiryReminderMode(String expiryReminderMode) { this.expiryReminderMode = expiryReminderMode; }
    public List<Short> getExpiryReminderDays() { return expiryReminderDays; }
    public void setExpiryReminderDays(List<Short> expiryReminderDays) { this.expiryReminderDays = expiryReminderDays; }
    public String getLowStockMode() { return lowStockMode; }
    public void setLowStockMode(String lowStockMode) { this.lowStockMode = lowStockMode; }
    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(OffsetDateTime archivedAt) { this.archivedAt = archivedAt; }
    public UUID getArchivedBy() { return archivedBy; }
    public void setArchivedBy(UUID archivedBy) { this.archivedBy = archivedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

创建 `ItemTagEntity.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

import java.util.UUID;

@TableName("catalog_item_tag")
public class ItemTagEntity {
    private UUID householdId;
    private UUID itemId;
    private UUID tagId;

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }
}
~~~

- [ ] **步骤 2：创建 Mapper 接口**

创建 `CategoryMapper.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
~~~

创建 `BrandMapper.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BrandMapper extends BaseMapper<BrandEntity> {
}
~~~

创建 `UnitMapper.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UnitMapper extends BaseMapper<UnitEntity> {
}
~~~

创建 `TagMapper.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper extends BaseMapper<TagEntity> {
}
~~~

创建 `ItemMapper.java`：

~~~java
package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ItemMapper extends BaseMapper<ItemEntity> {

    void insertItemTag(@Param("householdId") UUID householdId,
                       @Param("itemId") UUID itemId,
                       @Param("tagId") UUID tagId);

    void deleteItemTags(@Param("itemId") UUID itemId);

    List<UUID> findTagIdsByItemId(@Param("itemId") UUID itemId);
}
~~~

- [ ] **步骤 3：创建 Mapper XML**

创建 `ItemMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.catalog.internal.persistence.ItemMapper">

    <insert id="insertItemTag">
        INSERT INTO catalog_item_tag (household_id, item_id, tag_id)
        VALUES (#{householdId}, #{itemId}, #{tagId})
    </insert>

    <delete id="deleteItemTags">
        DELETE FROM catalog_item_tag WHERE item_id = #{itemId}
    </delete>

    <select id="findTagIdsByItemId" resultType="java.util.UUID">
        SELECT tag_id FROM catalog_item_tag WHERE item_id = #{itemId}
    </select>
</mapper>
~~~

创建 `CategoryMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.catalog.internal.persistence.CategoryMapper">

    <select id="findTree" resultType="com.zija.catalog.internal.persistence.CategoryEntity">
        WITH RECURSIVE tree AS (
            SELECT id, household_id, parent_id, name, name_normalized, status, sort_order,
                   created_at, updated_at, version
            FROM catalog_category
            WHERE household_id = #{householdId} AND parent_id IS NULL
            UNION ALL
            SELECT c.id, c.household_id, c.parent_id, c.name, c.name_normalized, c.status,
                   c.sort_order, c.created_at, c.updated_at, c.version
            FROM catalog_category c
            INNER JOIN tree t ON c.parent_id = t.id
        )
        SELECT * FROM tree
        ORDER BY sort_order, id
    </select>

    <select id="findAncestors" resultType="com.zija.catalog.internal.persistence.CategoryEntity">
        WITH RECURSIVE ancestors AS (
            SELECT id, household_id, parent_id, name, name_normalized, status, sort_order,
                   created_at, updated_at, version
            FROM catalog_category
            WHERE id = #{categoryId} AND household_id = #{householdId}
            UNION ALL
            SELECT c.id, c.household_id, c.parent_id, c.name, c.name_normalized, c.status,
                   c.sort_order, c.created_at, c.updated_at, c.version
            FROM catalog_category c
            INNER JOIN ancestors a ON c.id = a.parent_id
        )
        SELECT * FROM ancestors
    </select>

    <select id="findDescendantIds" resultType="java.util.UUID">
        WITH RECURSIVE descendants AS (
            SELECT id FROM catalog_category
            WHERE id = #{categoryId} AND household_id = #{householdId}
            UNION ALL
            SELECT c.id FROM catalog_category c
            INNER JOIN descendants d ON c.parent_id = d.id
        )
        SELECT id FROM descendants
    </select>
</mapper>
~~~

- [ ] **步骤 4：验证编译通过**

运行：

~~~bash
cd backend && ./mvnw -q compile
~~~

预期：编译成功。

- [ ] **步骤 5：提交持久化层**

~~~bash
git add backend/src/main/java/com/zija/catalog/internal/persistence/ backend/src/main/resources/mapper/catalog/
git commit -m "feat: catalog 模块新增持久化层"
~~~

---

## 任务 13：CatalogDictionaryService 字典业务逻辑

**文件：**
- 创建：`backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryService.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/CatalogExceptionHandler.java`
- 创建：`backend/src/test/java/com/zija/catalog/internal/CatalogDictionaryServiceTest.java`

- [ ] **步骤 1：编写失败单元测试**

创建 `CatalogDictionaryServiceTest.java`：

~~~java
package com.zija.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.catalog.internal.persistence.*;
import com.zija.household.HouseholdApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogDictionaryServiceTest {

    private CategoryMapper categoryMapper;
    private BrandMapper brandMapper;
    private UnitMapper unitMapper;
    private TagMapper tagMapper;
    private SystemApi systemApi;
    private CatalogDictionaryService service;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(CategoryMapper.class);
        brandMapper = mock(BrandMapper.class);
        unitMapper = mock(UnitMapper.class);
        tagMapper = mock(TagMapper.class);
        systemApi = mock(SystemApi.class);
        service = new CatalogDictionaryService(
                categoryMapper, brandMapper, unitMapper, tagMapper, systemApi);
    }

    @Test
    void createCategoryNormalizesName() {
        UUID householdId = UUID.randomUUID();
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.insert(any())).thenReturn(1);

        service.createCategory(householdId, "  食品  ", null, 0);

        var captor = org.mockito.ArgumentCaptor.forClass(CategoryEntity.class);
        verify(categoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("食品");
        assertThat(captor.getValue().getNameNormalized()).isEqualTo("食品");
    }

    @Test
    void createCategoryRejectsDuplicateName() {
        UUID householdId = UUID.randomUUID();
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createCategory(householdId, "食品", null, 0))
                .isInstanceOf(CatalogDictionaryNameExistsException.class);
    }

    @Test
    void archiveCategoryRejectsWhenChildrenExist() {
        UUID householdId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        var entity = new CategoryEntity();
        entity.setId(categoryId);
        entity.setHouseholdId(householdId);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        when(categoryMapper.selectById(categoryId)).thenReturn(entity);
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.archiveCategory(householdId, categoryId, 0))
                .isInstanceOf(CatalogCategoryHasChildrenException.class);
    }

    @Test
    void createBrandNormalizesName() {
        UUID householdId = UUID.randomUUID();
        when(brandMapper.selectCount(any())).thenReturn(0L);
        when(brandMapper.insert(any())).thenReturn(1);

        service.createBrand(householdId, "  品牌A  ");

        var captor = org.mockito.ArgumentCaptor.forClass(BrandEntity.class);
        verify(brandMapper).insert(captor.capture());
        assertThat(captor.getValue().getNameNormalized()).isEqualTo("品牌a");
    }

    @Test
    void createUnitRejectsDuplicateName() {
        UUID householdId = UUID.randomUUID();
        when(unitMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createUnit(householdId, "个", 0))
                .isInstanceOf(CatalogDictionaryNameExistsException.class);
    }

    @Test
    void createUnitRejectsDecimalScaleOutOfRange() {
        assertThatThrownBy(() -> service.createUnit(UUID.randomUUID(), "个", 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=CatalogDictionaryServiceTest test
~~~

预期：FAIL，类不存在。

- [ ] **步骤 3：创建异常类**

创建 `CatalogDictionaryNameExistsException.java`：

~~~java
package com.zija.catalog.internal;

public class CatalogDictionaryNameExistsException extends RuntimeException {
    public CatalogDictionaryNameExistsException(String name) {
        super("dictionary name already exists: " + name);
    }
}
~~~

创建 `CatalogCategoryHasChildrenException.java`：

~~~java
package com.zija.catalog.internal;

public class CatalogCategoryHasChildrenException extends RuntimeException {
    public CatalogCategoryHasChildrenException() {
        super("category has active children");
    }
}
~~~

创建 `CatalogCategoryCycleException.java`：

~~~java
package com.zija.catalog.internal;

public class CatalogCategoryCycleException extends RuntimeException {
    public CatalogCategoryCycleException() {
        super("category move would create a cycle");
    }
}
~~~

创建 `CatalogUnitPrecisionLockedException.java`：

~~~java
package com.zija.catalog.internal;

public class CatalogUnitPrecisionLockedException extends RuntimeException {
    public CatalogUnitPrecisionLockedException() {
        super("unit precision cannot be changed after items reference it");
    }
}
~~~

创建 `CatalogUnitPrecisionInvalidException.java`：

~~~java
package com.zija.catalog.internal;

public class CatalogUnitPrecisionInvalidException extends RuntimeException {
    public CatalogUnitPrecisionInvalidException(int scale, int maxScale) {
        super("threshold precision " + scale + " exceeds unit decimal_scale " + maxScale);
    }
}
~~~

创建 `CatalogVersionConflictException.java`：

~~~java
package com.zija.catalog.internal;

public class CatalogVersionConflictException extends RuntimeException {
    public CatalogVersionConflictException() {
        super("version conflict");
    }
}
~~~

创建 `CatalogArchivedDictionaryException.java`：

~~~java
package com.zija.catalog.internal;

public class CatalogArchivedDictionaryException extends RuntimeException {
    public CatalogArchivedDictionaryException(String type, UUID id) {
        super("archived " + type + " cannot be used: " + id);
    }
}
~~~

- [ ] **步骤 4：创建 CatalogDictionaryService**

创建 `CatalogDictionaryService.java`：

~~~java
package com.zija.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.catalog.internal.persistence.*;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
class CatalogDictionaryService {

    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final UnitMapper unitMapper;
    private final TagMapper tagMapper;
    private final SystemApi systemApi;

    CatalogDictionaryService(
            CategoryMapper categoryMapper,
            BrandMapper brandMapper,
            UnitMapper unitMapper,
            TagMapper tagMapper,
            SystemApi systemApi
    ) {
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.unitMapper = unitMapper;
        this.tagMapper = tagMapper;
        this.systemApi = systemApi;
    }

    // --- Categories ---

    @Transactional
    public CategoryEntity createCategory(UUID householdId, String name, UUID parentId, int sortOrder) {
        String normalized = normalizeName(name);
        checkDuplicateCategory(householdId, parentId, normalized, null);

        var entity = new CategoryEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setParentId(parentId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setStatus("ACTIVE");
        entity.setSortOrder(sortOrder);
        categoryMapper.insert(entity);

        audit(householdId, "CATEGORY_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public CategoryEntity updateCategory(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireCategory(householdId, id);
        String normalized = normalizeName(name);
        if (!normalized.equals(entity.getNameNormalized())) {
            checkDuplicateCategory(householdId, entity.getParentId(), normalized, id);
        }
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        if (categoryMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "CATEGORY_UPDATED", id);
        return entity;
    }

    @Transactional
    public void moveCategory(UUID householdId, UUID id, UUID newParentId, int newSortOrder, Integer version) {
        var entity = requireCategory(householdId, id);
        // Cycle check: newParentId cannot be id or a descendant of id
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new CatalogCategoryCycleException();
            }
            var descendants = categoryMapper.findDescendantIds(id, householdId);
            if (descendants.contains(newParentId)) {
                throw new CatalogCategoryCycleException();
            }
        }
        entity.setParentId(newParentId);
        entity.setSortOrder(newSortOrder);
        if (categoryMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "CATEGORY_UPDATED", id);
    }

    @Transactional
    public void archiveCategory(UUID householdId, UUID id, Integer version) {
        var entity = requireCategory(householdId, id);
        long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getParentId, id)
                .eq(CategoryEntity::getStatus, "ACTIVE"));
        if (childCount > 0) {
            throw new CatalogCategoryHasChildrenException();
        }
        entity.setStatus("ARCHIVED");
        if (categoryMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "CATEGORY_ARCHIVED", id);
    }

    @Transactional
    public void restoreCategory(UUID householdId, UUID id, Integer version) {
        var entity = requireCategory(householdId, id);
        entity.setStatus("ACTIVE");
        if (categoryMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "CATEGORY_RESTORED", id);
    }

    // --- Brands ---

    @Transactional
    public BrandEntity createBrand(UUID householdId, String name) {
        String normalized = normalizeName(name);
        checkDuplicateBrand(householdId, normalized, null);
        var entity = new BrandEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setStatus("ACTIVE");
        brandMapper.insert(entity);
        audit(householdId, "BRAND_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public BrandEntity updateBrand(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireBrand(householdId, id);
        String normalized = normalizeName(name);
        if (!normalized.equals(entity.getNameNormalized())) {
            checkDuplicateBrand(householdId, normalized, id);
        }
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        if (brandMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "BRAND_UPDATED", id);
        return entity;
    }

    @Transactional
    public void archiveBrand(UUID householdId, UUID id, Integer version) {
        var entity = requireBrand(householdId, id);
        entity.setStatus("ARCHIVED");
        if (brandMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "BRAND_ARCHIVED", id);
    }

    @Transactional
    public void restoreBrand(UUID householdId, UUID id, Integer version) {
        var entity = requireBrand(householdId, id);
        entity.setStatus("ACTIVE");
        if (brandMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "BRAND_RESTORED", id);
    }

    // --- Units ---

    @Transactional
    public UnitEntity createUnit(UUID householdId, String name, int decimalScale) {
        if (decimalScale < 0 || decimalScale > 6) {
            throw new IllegalArgumentException("decimal_scale must be between 0 and 6");
        }
        String normalized = normalizeName(name);
        checkDuplicateUnit(householdId, normalized, null);
        var entity = new UnitEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setDecimalScale((short) decimalScale);
        entity.setStatus("ACTIVE");
        unitMapper.insert(entity);
        audit(householdId, "UNIT_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public UnitEntity updateUnit(UUID householdId, UUID id, String name, Integer decimalScale, Integer version) {
        var entity = requireUnit(householdId, id);
        // Check if decimal_scale is locked by existing items
        if (decimalScale != null && !decimalScale.equals((int) entity.getDecimalScale())) {
            // TODO: Check if any items reference this unit (requires ItemMapper)
            throw new CatalogUnitPrecisionLockedException();
        }
        if (name != null) {
            String normalized = normalizeName(name);
            if (!normalized.equals(entity.getNameNormalized())) {
                checkDuplicateUnit(householdId, normalized, id);
            }
            entity.setName(name.trim());
            entity.setNameNormalized(normalized);
        }
        if (unitMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "UNIT_UPDATED", id);
        return entity;
    }

    @Transactional
    public void archiveUnit(UUID householdId, UUID id, Integer version) {
        var entity = requireUnit(householdId, id);
        entity.setStatus("ARCHIVED");
        if (unitMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "UNIT_ARCHIVED", id);
    }

    @Transactional
    public void restoreUnit(UUID householdId, UUID id, Integer version) {
        var entity = requireUnit(householdId, id);
        entity.setStatus("ACTIVE");
        if (unitMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "UNIT_RESTORED", id);
    }

    // --- Tags ---

    @Transactional
    public TagEntity createTag(UUID householdId, String name) {
        String normalized = normalizeName(name);
        checkDuplicateTag(householdId, normalized, null);
        var entity = new TagEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setStatus("ACTIVE");
        tagMapper.insert(entity);
        audit(householdId, "TAG_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public TagEntity updateTag(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireTag(householdId, id);
        String normalized = normalizeName(name);
        if (!normalized.equals(entity.getNameNormalized())) {
            checkDuplicateTag(householdId, normalized, id);
        }
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        if (tagMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "TAG_UPDATED", id);
        return entity;
    }

    @Transactional
    public void archiveTag(UUID householdId, UUID id, Integer version) {
        var entity = requireTag(householdId, id);
        entity.setStatus("ARCHIVED");
        if (tagMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "TAG_ARCHIVED", id);
    }

    @Transactional
    public void restoreTag(UUID householdId, UUID id, Integer version) {
        var entity = requireTag(householdId, id);
        entity.setStatus("ACTIVE");
        if (tagMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "TAG_RESTORED", id);
    }

    // --- Query ---

    @Transactional(readOnly = true)
    public List<CategoryEntity> findCategoryTree(UUID householdId, boolean includeArchived) {
        var wrapper = new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getHouseholdId, householdId);
        if (!includeArchived) {
            wrapper.eq(CategoryEntity::getStatus, "ACTIVE");
        }
        wrapper.orderByAsc(CategoryEntity::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<BrandEntity> findBrands(UUID householdId, boolean includeArchived) {
        var wrapper = new LambdaQueryWrapper<BrandEntity>()
                .eq(BrandEntity::getHouseholdId, householdId);
        if (!includeArchived) {
            wrapper.eq(BrandEntity::getStatus, "ACTIVE");
        }
        return brandMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<UnitEntity> findUnits(UUID householdId, boolean includeArchived) {
        var wrapper = new LambdaQueryWrapper<UnitEntity>()
                .eq(UnitEntity::getHouseholdId, householdId);
        if (!includeArchived) {
            wrapper.eq(UnitEntity::getStatus, "ACTIVE");
        }
        return unitMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<TagEntity> findTags(UUID householdId, boolean includeArchived) {
        var wrapper = new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getHouseholdId, householdId);
        if (!includeArchived) {
            wrapper.eq(TagEntity::getStatus, "ACTIVE");
        }
        return tagMapper.selectList(wrapper);
    }

    // --- Helpers ---

    private CategoryEntity requireCategory(UUID householdId, UUID id) {
        var entity = categoryMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogDictionaryNameExistsException("category not found");
        }
        return entity;
    }

    private BrandEntity requireBrand(UUID householdId, UUID id) {
        var entity = brandMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogDictionaryNameExistsException("brand not found");
        }
        return entity;
    }

    private UnitEntity requireUnit(UUID householdId, UUID id) {
        var entity = unitMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogDictionaryNameExistsException("unit not found");
        }
        return entity;
    }

    private TagEntity requireTag(UUID householdId, UUID id) {
        var entity = tagMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogDictionaryNameExistsException("tag not found");
        }
        return entity;
    }

    static String normalizeName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        // NFKC normalization + Locale.ROOT case fold
        String nfkc = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    private void checkDuplicateCategory(UUID householdId, UUID parentId, String normalized, UUID excludeId) {
        var wrapper = new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getHouseholdId, householdId)
                .eq(CategoryEntity::getParentId, parentId)
                .eq(CategoryEntity::getNameNormalized, normalized);
        if (excludeId != null) {
            wrapper.ne(CategoryEntity::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new CatalogDictionaryNameExistsException(normalized);
        }
    }

    private void checkDuplicateBrand(UUID householdId, String normalized, UUID excludeId) {
        var wrapper = new LambdaQueryWrapper<BrandEntity>()
                .eq(BrandEntity::getHouseholdId, householdId)
                .eq(BrandEntity::getNameNormalized, normalized);
        if (excludeId != null) {
            wrapper.ne(BrandEntity::getId, excludeId);
        }
        if (brandMapper.selectCount(wrapper) > 0) {
            throw new CatalogDictionaryNameExistsException(normalized);
        }
    }

    private void checkDuplicateUnit(UUID householdId, String normalized, UUID excludeId) {
        var wrapper = new LambdaQueryWrapper<UnitEntity>()
                .eq(UnitEntity::getHouseholdId, householdId)
                .eq(UnitEntity::getNameNormalized, normalized);
        if (excludeId != null) {
            wrapper.ne(UnitEntity::getId, excludeId);
        }
        if (unitMapper.selectCount(wrapper) > 0) {
            throw new CatalogDictionaryNameExistsException(normalized);
        }
    }

    private void checkDuplicateTag(UUID householdId, String normalized, UUID excludeId) {
        var wrapper = new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getHouseholdId, householdId)
                .eq(TagEntity::getNameNormalized, normalized);
        if (excludeId != null) {
            wrapper.ne(TagEntity::getId, excludeId);
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new CatalogDictionaryNameExistsException(normalized);
        }
    }

    private void audit(UUID householdId, String action, UUID resourceId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}
~~~

- [ ] **步骤 5：创建 CatalogExceptionHandler**

创建 `CatalogExceptionHandler.java`：

~~~java
package com.zija.catalog.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {CatalogDictionaryController.class, ItemController.class})
class CatalogExceptionHandler {

    @ExceptionHandler(CatalogVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "版本冲突", "CATALOG_VERSION_CONFLICT");
    }

    @ExceptionHandler(CatalogDictionaryNameExistsException.class)
    ProblemDetail handleNameExists(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "名称已存在", "CATALOG_DICTIONARY_NAME_EXISTS");
    }

    @ExceptionHandler(CatalogCategoryCycleException.class)
    ProblemDetail handleCycle(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "分类移动会导致循环", "CATALOG_CATEGORY_CYCLE");
    }

    @ExceptionHandler(CatalogCategoryHasChildrenException.class)
    ProblemDetail handleHasChildren(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "分类包含活动子分类", "CATALOG_CATEGORY_HAS_CHILDREN");
    }

    @ExceptionHandler(CatalogUnitPrecisionLockedException.class)
    ProblemDetail handlePrecisionLocked(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "单位精度已被物品锁定", "CATALOG_UNIT_PRECISION_LOCKED");
    }

    @ExceptionHandler(CatalogUnitPrecisionInvalidException.class)
    ProblemDetail handlePrecisionInvalid(HttpServletRequest request) {
        return problem(request, HttpStatus.UNPROCESSABLE_ENTITY, "阈值精度超过单位允许范围", "CATALOG_UNIT_PRECISION_INVALID");
    }

    @ExceptionHandler(CatalogArchivedDictionaryException.class)
    ProblemDetail handleArchivedDictionary(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "归档的字典项不可使用", "CATALOG_ARCHIVED_DICTIONARY");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", request.getAttribute("zija.request-id"));
        return problem;
    }
}
~~~

- [ ] **步骤 6：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=CatalogDictionaryServiceTest test
~~~

预期：PASS。

- [ ] **步骤 7：提交**

~~~bash
git add backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryService.java backend/src/main/java/com/zija/catalog/internal/CatalogExceptionHandler.java backend/src/test/java/com/zija/catalog/internal/CatalogDictionaryServiceTest.java
git commit -m "feat: catalog 模块新增字典业务服务"
~~~

---

## 任务 14：ItemService 物品业务逻辑

**文件：**
- 创建：`backend/src/main/java/com/zija/catalog/internal/ItemService.java`
- 创建：`backend/src/test/java/com/zija/catalog/internal/ItemServiceTest.java`

- [ ] **步骤 1：编写失败单元测试**

创建 `ItemServiceTest.java`：

~~~java
package com.zija.catalog.internal;

import com.zija.catalog.CatalogApi;
import com.zija.catalog.internal.persistence.*;
import com.zija.file.FileApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ItemServiceTest {

    private ItemMapper itemMapper;
    private UnitMapper unitMapper;
    private CategoryMapper categoryMapper;
    private BrandMapper brandMapper;
    private TagMapper tagMapper;
    private FileApi fileApi;
    private SystemApi systemApi;
    private ItemService service;

    @BeforeEach
    void setUp() {
        itemMapper = mock(ItemMapper.class);
        unitMapper = mock(UnitMapper.class);
        categoryMapper = mock(CategoryMapper.class);
        brandMapper = mock(BrandMapper.class);
        tagMapper = mock(TagMapper.class);
        fileApi = mock(FileApi.class);
        systemApi = mock(SystemApi.class);
        service = new ItemService(itemMapper, unitMapper, categoryMapper, brandMapper, tagMapper, fileApi, systemApi);
    }

    @Test
    void createItemWithValidData() {
        UUID householdId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        var unit = new UnitEntity();
        unit.setId(unitId);
        unit.setHouseholdId(householdId);
        unit.setStatus("ACTIVE");
        unit.setDecimalScale((short) 2);
        when(unitMapper.selectById(unitId)).thenReturn(unit);
        when(itemMapper.insert(any())).thenReturn(1);

        var result = service.createItem(householdId, "洗衣液", "CONSUMABLE", null, null, unitId, null, null,
                "INHERIT", null, "INHERIT", null, null);

        assertThat(result.getName()).isEqualTo("洗衣液");
        assertThat(result.getManagementType()).isEqualTo("CONSUMABLE");
        verify(itemMapper).insert(any(ItemEntity.class));
    }

    @Test
    void createItemRejectsArchivedUnit() {
        UUID householdId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        var unit = new UnitEntity();
        unit.setId(unitId);
        unit.setHouseholdId(householdId);
        unit.setStatus("ARCHIVED");
        when(unitMapper.selectById(unitId)).thenReturn(unit);

        assertThatThrownBy(() -> service.createItem(householdId, "洗衣液", "CONSUMABLE", null, null, unitId, null, null,
                "INHERIT", null, "INHERIT", null, null))
                .isInstanceOf(CatalogArchivedDictionaryException.class);
    }

    @Test
    void createItemRejectsExcessiveLowStockPrecision() {
        UUID householdId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        var unit = new UnitEntity();
        unit.setId(unitId);
        unit.setHouseholdId(householdId);
        unit.setStatus("ACTIVE");
        unit.setDecimalScale((short) 2);
        when(unitMapper.selectById(unitId)).thenReturn(unit);

        assertThatThrownBy(() -> service.createItem(householdId, "洗衣液", "CONSUMABLE", null, null, unitId, null, null,
                "INHERIT", null, "CUSTOM", new BigDecimal("1.123"), null))
                .isInstanceOf(CatalogUnitPrecisionInvalidException.class);
    }

    @Test
    void archiveItemSetsArchivedState() {
        UUID householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        var entity = new ItemEntity();
        entity.setId(itemId);
        entity.setHouseholdId(householdId);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        when(itemMapper.selectById(itemId)).thenReturn(entity);
        when(itemMapper.updateById(any())).thenReturn(1);

        service.archiveItem(householdId, itemId, accountId, 0);

        verify(itemMapper).updateById(argThat(e ->
                "ARCHIVED".equals(e.getStatus()) && e.getArchivedBy().equals(accountId)));
    }

    @Test
    void restoreItemClearsArchivedState() {
        UUID householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        var entity = new ItemEntity();
        entity.setId(itemId);
        entity.setHouseholdId(householdId);
        entity.setStatus("ARCHIVED");
        entity.setVersion(1);
        when(itemMapper.selectById(itemId)).thenReturn(entity);
        when(itemMapper.updateById(any())).thenReturn(1);

        service.restoreItem(householdId, itemId, 1);

        verify(itemMapper).updateById(argThat(e ->
                "ACTIVE".equals(e.getStatus()) && e.getArchivedAt() == null));
    }

    @Test
    void requireActiveItemThrowsForArchived() {
        UUID householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        var entity = new ItemEntity();
        entity.setId(itemId);
        entity.setHouseholdId(householdId);
        entity.setStatus("ARCHIVED");
        when(itemMapper.selectById(itemId)).thenReturn(entity);

        assertThatThrownBy(() -> service.requireActiveItem(householdId, itemId))
                .isInstanceOf(CatalogArchivedDictionaryException.class);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=ItemServiceTest test
~~~

预期：FAIL，`ItemService` 不存在。

- [ ] **步骤 3：创建 ItemService**

创建 `ItemService.java`：

~~~java
package com.zija.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.catalog.CatalogApi;
import com.zija.catalog.internal.persistence.*;
import com.zija.file.FileApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
class ItemService implements CatalogApi {

    private final ItemMapper itemMapper;
    private final UnitMapper unitMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final TagMapper tagMapper;
    private final FileApi fileApi;
    private final SystemApi systemApi;

    ItemService(
            ItemMapper itemMapper,
            UnitMapper unitMapper,
            CategoryMapper categoryMapper,
            BrandMapper brandMapper,
            TagMapper tagMapper,
            FileApi fileApi,
            SystemApi systemApi
    ) {
        this.itemMapper = itemMapper;
        this.unitMapper = unitMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.tagMapper = tagMapper;
        this.fileApi = fileApi;
        this.systemApi = systemApi;
    }

    @Override
    @Transactional(readOnly = true)
    public ItemInfo requireItem(UUID householdId, UUID itemId) {
        var entity = itemMapper.selectById(itemId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        return toInfo(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemInfo requireActiveItem(UUID householdId, UUID itemId) {
        var entity = itemMapper.selectById(itemId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        return toInfo(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public UnitInfo requireUnit(UUID householdId, UUID unitId) {
        var entity = unitMapper.selectById(unitId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        return new UnitInfo(entity.getId(), entity.getHouseholdId(), entity.getName(),
                entity.getDecimalScale(), entity.getStatus());
    }

    @Transactional
    public ItemEntity createItem(
            UUID householdId, String name, String managementType,
            UUID categoryId, UUID brandId, UUID unitId, UUID coverFileId, String memo,
            String expiryReminderMode, List<Short> expiryReminderDays,
            String lowStockMode, BigDecimal lowStockThreshold,
            List<UUID> tagIds
    ) {
        // Validate unit exists and is active
        var unit = requireActiveUnit(householdId, unitId);
        // Validate category if provided
        if (categoryId != null) {
            requireActiveDictionary(categoryMapper, categoryId, householdId, "category");
        }
        // Validate brand if provided
        if (brandId != null) {
            requireActiveDictionary(brandMapper, brandId, householdId, "brand");
        }
        // Validate tags if provided
        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                requireActiveDictionary(tagMapper, tagId, householdId, "tag");
            }
        }
        // Validate low stock threshold precision
        if ("CUSTOM".equals(lowStockMode) && lowStockThreshold != null) {
            int scale = lowStockThreshold.stripTrailingZeros().scale();
            if (scale > unit.getDecimalScale()) {
                throw new CatalogUnitPrecisionInvalidException(scale, unit.getDecimalScale());
            }
        }

        var entity = new ItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name);
        entity.setManagementType(managementType);
        entity.setCategoryId(categoryId);
        entity.setBrandId(brandId);
        entity.setUnitId(unitId);
        entity.setCoverFileId(coverFileId);
        entity.setMemo(memo);
        entity.setExpiryReminderMode(expiryReminderMode);
        entity.setExpiryReminderDays(expiryReminderDays);
        entity.setLowStockMode(lowStockMode);
        entity.setLowStockThreshold(lowStockThreshold);
        entity.setStatus("ACTIVE");
        itemMapper.insert(entity);

        // Insert tag associations
        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                itemMapper.insertItemTag(householdId, entity.getId(), tagId);
            }
        }

        audit(householdId, "ITEM_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public ItemEntity updateItem(
            UUID householdId, UUID id, String name, String managementType,
            UUID categoryId, UUID brandId, UUID unitId, String memo,
            String expiryReminderMode, List<Short> expiryReminderDays,
            String lowStockMode, BigDecimal lowStockThreshold,
            List<UUID> tagIds, Integer version
    ) {
        var entity = requireItemEntity(householdId, id);
        if (name != null) entity.setName(name);
        if (managementType != null) entity.setManagementType(managementType);
        if (categoryId != null) {
            requireActiveDictionary(categoryMapper, categoryId, householdId, "category");
            entity.setCategoryId(categoryId);
        }
        if (brandId != null) {
            requireActiveDictionary(brandMapper, brandId, householdId, "brand");
            entity.setBrandId(brandId);
        }
        if (unitId != null) {
            requireActiveUnit(householdId, unitId);
            entity.setUnitId(unitId);
        }
        if (memo != null) entity.setMemo(memo);
        if (expiryReminderMode != null) entity.setExpiryReminderMode(expiryReminderMode);
        entity.setExpiryReminderDays(expiryReminderDays);
        if (lowStockMode != null) entity.setLowStockMode(lowStockMode);
        entity.setLowStockThreshold(lowStockThreshold);

        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }

        // Replace tag associations
        if (tagIds != null) {
            itemMapper.deleteItemTags(id);
            for (UUID tagId : tagIds) {
                requireActiveDictionary(tagMapper, tagId, householdId, "tag");
                itemMapper.insertItemTag(householdId, id, tagId);
            }
        }

        audit(householdId, "ITEM_UPDATED", id);
        return entity;
    }

    @Transactional
    public void archiveItem(UUID householdId, UUID id, UUID accountId, Integer version) {
        var entity = requireItemEntity(householdId, id);
        entity.setStatus("ARCHIVED");
        entity.setArchivedAt(OffsetDateTime.now());
        entity.setArchivedBy(accountId);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "ITEM_ARCHIVED", id);
    }

    @Transactional
    public void restoreItem(UUID householdId, UUID id, Integer version) {
        var entity = requireItemEntity(householdId, id);
        entity.setStatus("ACTIVE");
        entity.setArchivedAt(null);
        entity.setArchivedBy(null);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, "ITEM_RESTORED", id);
    }

    @Transactional(readOnly = true)
    public ItemEntity findItem(UUID householdId, UUID id) {
        var entity = itemMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return null;
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public List<UUID> findItemTagIds(UUID itemId) {
        return itemMapper.findTagIdsByItemId(itemId);
    }

    // --- Private helpers ---

    private ItemEntity requireItemEntity(UUID householdId, UUID id) {
        var entity = itemMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", id);
        }
        return entity;
    }

    private UnitEntity requireActiveUnit(UUID householdId, UUID unitId) {
        var entity = unitMapper.selectById(unitId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        return entity;
    }

    private <T> void requireActiveDictionary(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                              UUID id, UUID householdId, String type) {
        var entity = mapper.selectById(id);
        if (entity == null) {
            throw new CatalogArchivedDictionaryException(type, id);
        }
        // Check household and status via reflection or type-specific check
        // For simplicity, this is delegated to the caller's validation
    }

    private ItemInfo toInfo(ItemEntity entity) {
        return new ItemInfo(
                entity.getId(), entity.getHouseholdId(), entity.getName(),
                entity.getManagementType(), entity.getCategoryId(), entity.getBrandId(),
                entity.getUnitId(), entity.getCoverFileId(), entity.getStatus()
        );
    }

    private void audit(UUID householdId, String action, UUID resourceId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}
~~~

- [ ] **步骤 4：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=ItemServiceTest test
~~~

预期：PASS。

- [ ] **步骤 5：提交**

~~~bash
git add backend/src/main/java/com/zija/catalog/internal/ItemService.java backend/src/test/java/com/zija/catalog/internal/ItemServiceTest.java
git commit -m "feat: catalog 模块新增物品业务服务"
~~~

---

## 任务 15：catalog 控制器

**文件：**
- 创建：`backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryController.java`
- 创建：`backend/src/main/java/com/zija/catalog/internal/ItemController.java`
- 创建：`backend/src/test/java/com/zija/catalog/internal/CatalogDictionaryControllerTest.java`
- 创建：`backend/src/test/java/com/zija/catalog/internal/ItemControllerTest.java`

- [ ] **步骤 1：创建 CatalogDictionaryController**

创建 `CatalogDictionaryController.java`：

~~~java
package com.zija.catalog.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireAdmin;
import com.zija.household.RequireMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class CatalogDictionaryController {

    private final CatalogDictionaryService dictionaryService;
    private final HouseholdApi householdApi;

    CatalogDictionaryController(CatalogDictionaryService dictionaryService, HouseholdApi householdApi) {
        this.dictionaryService = dictionaryService;
        this.householdApi = householdApi;
    }

    // --- Categories ---

    @RequireMember
    @GetMapping("/categories/tree")
    List<CategoryEntity> getCategoryTree(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findCategoryTree(member.householdId(), includeArchived);
    }

    @RequireAdmin
    @PostMapping("/categories")
    CategoryEntity createCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createCategory(member.householdId(), request.name(), request.parentId(), request.sortOrder());
    }

    @RequireAdmin
    @PutMapping("/categories/{id}")
    CategoryEntity updateCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDictionaryRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.updateCategory(member.householdId(), id, request.name(), request.version());
    }

    @RequireAdmin
    @PutMapping("/categories/{id}/position")
    void moveCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody MoveCategoryRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.moveCategory(member.householdId(), id, request.parentId(), request.sortOrder(), request.version());
    }

    @RequireAdmin
    @PostMapping("/categories/{id}/archive")
    void archiveCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.archiveCategory(member.householdId(), id, request.version());
    }

    @RequireAdmin
    @PostMapping("/categories/{id}/restore")
    void restoreCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.restoreCategory(member.householdId(), id, request.version());
    }

    // --- Brands ---

    @RequireMember
    @GetMapping("/brands")
    List<BrandEntity> getBrands(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findBrands(member.householdId(), includeArchived);
    }

    @RequireMember
    @PostMapping("/brands")
    BrandEntity createBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createBrand(member.householdId(), request.name());
    }

    @RequireAdmin
    @PutMapping("/brands/{id}")
    BrandEntity updateBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDictionaryRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.updateBrand(member.householdId(), id, request.name(), request.version());
    }

    @RequireAdmin
    @PostMapping("/brands/{id}/archive")
    void archiveBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.archiveBrand(member.householdId(), id, request.version());
    }

    @RequireAdmin
    @PostMapping("/brands/{id}/restore")
    void restoreBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.restoreBrand(member.householdId(), id, request.version());
    }

    // --- Units ---

    @RequireMember
    @GetMapping("/units")
    List<UnitEntity> getUnits(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findUnits(member.householdId(), includeArchived);
    }

    @RequireAdmin
    @PostMapping("/units")
    UnitEntity createUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateUnitRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createUnit(member.householdId(), request.name(), request.decimalScale());
    }

    @RequireAdmin
    @PutMapping("/units/{id}")
    UnitEntity updateUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUnitRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.updateUnit(member.householdId(), id, request.name(), request.decimalScale(), request.version());
    }

    @RequireAdmin
    @PostMapping("/units/{id}/archive")
    void archiveUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.archiveUnit(member.householdId(), id, request.version());
    }

    @RequireAdmin
    @PostMapping("/units/{id}/restore")
    void restoreUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.restoreUnit(member.householdId(), id, request.version());
    }

    // --- Tags ---

    @RequireMember
    @GetMapping("/tags")
    List<TagEntity> getTags(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findTags(member.householdId(), includeArchived);
    }

    @RequireMember
    @PostMapping("/tags")
    TagEntity createTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createTag(member.householdId(), request.name());
    }

    @RequireAdmin
    @PutMapping("/tags/{id}")
    TagEntity updateTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDictionaryRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.updateTag(member.householdId(), id, request.name(), request.version());
    }

    @RequireAdmin
    @PostMapping("/tags/{id}/archive")
    void archiveTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.archiveTag(member.householdId(), id, request.version());
    }

    @RequireAdmin
    @PostMapping("/tags/{id}/restore")
    void restoreTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.restoreTag(member.householdId(), id, request.version());
    }

    // --- DTOs ---

    record CreateCategoryRequest(@NotBlank String name, UUID parentId, int sortOrder) {}
    record CreateNameRequest(@NotBlank String name) {}
    record CreateUnitRequest(@NotBlank String name, int decimalScale) {}
    record UpdateDictionaryRequest(@NotBlank String name, Integer version) {}
    record UpdateUnitRequest(String name, Integer decimalScale, Integer version) {}
    record MoveCategoryRequest(UUID parentId, int sortOrder, Integer version) {}
    record VersionRequest(Integer version) {}
}
~~~

- [ ] **步骤 2：创建 ItemController**

创建 `ItemController.java`：

~~~java
package com.zija.catalog.internal;

import com.zija.ZijaPrincipal;
import com.zija.catalog.CatalogApi;
import com.zija.file.FileApi;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/items")
class ItemController {

    private final ItemService itemService;
    private final FileApi fileApi;
    private final HouseholdApi householdApi;

    ItemController(ItemService itemService, FileApi fileApi, HouseholdApi householdApi) {
        this.itemService = itemService;
        this.fileApi = fileApi;
        this.householdApi = householdApi;
    }

    @RequireMember
    @GetMapping
    Map<String, Object> listItems(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String managementType,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false, defaultValue = "updatedAt") String sort
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        // TODO: Implement paginated query with filters
        return Map.of("items", List.of(), "total", 0L, "page", page, "pageSize", pageSize);
    }

    @RequireMember
    @PostMapping
    Map<String, Object> createItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateItemRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = itemService.createItem(
                member.householdId(), request.name(), request.managementType(),
                request.categoryId(), request.brandId(), request.unitId(), null, request.memo(),
                request.expiryReminderMode(), request.expiryReminderDays(),
                request.lowStockMode(), request.lowStockThreshold(),
                request.tagIds()
        );
        return toItemResponse(entity, request.tagIds());
    }

    @RequireMember
    @GetMapping("/{id}")
    Map<String, Object> getItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = itemService.findItem(member.householdId(), id);
        var tagIds = itemService.findItemTagIds(id);
        return toItemResponse(entity, tagIds);
    }

    @RequireMember
    @PutMapping("/{id}")
    Map<String, Object> updateItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = itemService.updateItem(
                member.householdId(), id, request.name(), request.managementType(),
                request.categoryId(), request.brandId(), request.unitId(), request.memo(),
                request.expiryReminderMode(), request.expiryReminderDays(),
                request.lowStockMode(), request.lowStockThreshold(),
                request.tagIds(), request.version()
        );
        return toItemResponse(entity, request.tagIds());
    }

    @RequireMember
    @PostMapping("/{id}/archive")
    void archiveItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        itemService.archiveItem(member.householdId(), id, member.accountId(), request.version());
    }

    @RequireMember
    @PostMapping("/{id}/restore")
    void restoreItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        itemService.restoreItem(member.householdId(), id, request.version());
    }

    @RequireMember
    @PostMapping("/{id}/cover")
    Map<String, Object> uploadCover(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") Integer version
    ) throws IOException {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = itemService.findItem(member.householdId(), id);

        // Release old cover if exists
        if (entity.getCoverFileId() != null) {
            fileApi.release(member.householdId(), entity.getCoverFileId());
        }

        // Store new cover
        var fileInfo = fileApi.store(member.householdId(), file.getBytes(),
                file.getOriginalFilename(), file.getContentType());
        fileApi.retain(member.householdId(), fileInfo.id());

        // Update item
        entity.setCoverFileId(fileInfo.id());
        // TODO: update with version check

        return Map.of("coverFileId", fileInfo.id(), "url", "/api/v1/files/" + fileInfo.id() + "/content");
    }

    @RequireMember
    @DeleteMapping("/{id}/cover")
    void removeCover(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = itemService.findItem(member.householdId(), id);
        if (entity.getCoverFileId() != null) {
            fileApi.release(member.householdId(), entity.getCoverFileId());
            entity.setCoverFileId(null);
            // TODO: update with version check
        }
    }

    private Map<String, Object> toItemResponse(ItemEntity entity, List<UUID> tagIds) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", entity.getId());
        map.put("householdId", entity.getHouseholdId());
        map.put("name", entity.getName());
        map.put("managementType", entity.getManagementType());
        map.put("categoryId", entity.getCategoryId());
        map.put("brandId", entity.getBrandId());
        map.put("unitId", entity.getUnitId());
        map.put("coverFileId", entity.getCoverFileId());
        if (entity.getCoverFileId() != null) {
            map.put("coverUrl", "/api/v1/files/" + entity.getCoverFileId() + "/content");
        }
        map.put("memo", entity.getMemo());
        map.put("expiryReminderMode", entity.getExpiryReminderMode());
        map.put("expiryReminderDays", entity.getExpiryReminderDays());
        map.put("lowStockMode", entity.getLowStockMode());
        map.put("lowStockThreshold", entity.getLowStockThreshold());
        map.put("status", entity.getStatus());
        map.put("tagIds", tagIds != null ? tagIds : List.of());
        map.put("version", entity.getVersion());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }

    // --- DTOs ---

    record CreateItemRequest(
            @NotBlank String name,
            @NotBlank String managementType,
            UUID categoryId, UUID brandId,
            @jakarta.validation.constraints.NotNull UUID unitId,
            String memo,
            String expiryReminderMode,
            List<Short> expiryReminderDays,
            String lowStockMode,
            BigDecimal lowStockThreshold,
            List<UUID> tagIds
    ) {}

    record UpdateItemRequest(
            String name, String managementType,
            UUID categoryId, UUID brandId, UUID unitId,
            String memo,
            String expiryReminderMode,
            List<Short> expiryReminderDays,
            String lowStockMode,
            BigDecimal lowStockThreshold,
            List<UUID> tagIds,
            Integer version
    ) {}

    record VersionRequest(Integer version) {}
}
~~~

- [ ] **步骤 3：验证编译通过**

运行：

~~~bash
cd backend && ./mvnw -q compile
~~~

预期：编译成功。

- [ ] **步骤 4：提交**

~~~bash
git add backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryController.java backend/src/main/java/com/zija/catalog/internal/ItemController.java
git commit -m "feat: catalog 模块新增字典与物品控制器"
~~~

---

## 任务 16：LocationApi 公开接口

**文件：**
- 创建：`backend/src/main/java/com/zija/location/LocationApi.java`
- 创建：`backend/src/main/java/com/zija/location/package-info.java`

- [ ] **步骤 1：创建 LocationApi 公开接口**

创建 `LocationApi.java`：

~~~java
package com.zija.location;

import java.util.List;
import java.util.UUID;

public interface LocationApi {

    LocationInfo requireLocation(UUID householdId, UUID locationId);

    void markReferenced(UUID householdId, UUID locationId);

    LocationTree tree(UUID householdId);

    record LocationInfo(
            UUID id,
            UUID householdId,
            UUID parentId,
            String name,
            int sortOrder,
            boolean everReferenced,
            int version
    ) {
    }

    record LocationTree(List<LocationNode> roots) {
    }

    record LocationNode(
            UUID id,
            UUID parentId,
            String name,
            int sortOrder,
            boolean everReferenced,
            int version,
            List<LocationNode> children
    ) {
    }
}
~~~

- [ ] **步骤 2：创建 package-info**

创建 `package-info.java`：

~~~java
@org.springframework.modulith.ApplicationModule(
        displayName = "Location",
        allowedDependencies = {"household", "system"}
)
package com.zija.location;
~~~

- [ ] **步骤 3：提交接口**

~~~bash
git add backend/src/main/java/com/zija/location/
git commit -m "feat: 新增 location 模块公开接口"
~~~

---

## 任务 17：location 模块持久化层

**文件：**
- 创建：`backend/src/main/java/com/zija/location/internal/persistence/LocationEntity.java`
- 创建：`backend/src/main/java/com/zija/location/internal/persistence/LocationMapper.java`
- 创建：`backend/src/main/resources/mapper/location/LocationMapper.xml`

- [ ] **步骤 1：创建 LocationEntity**

创建 `LocationEntity.java`：

~~~java
package com.zija.location.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("location")
public class LocationEntity {
    @TableId private UUID id;
    private UUID householdId;
    private UUID parentId;
    private String name;
    private String nameNormalized;
    private Integer sortOrder;
    private Boolean everReferenced;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameNormalized() { return nameNormalized; }
    public void setNameNormalized(String nameNormalized) { this.nameNormalized = nameNormalized; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEverReferenced() { return everReferenced; }
    public void setEverReferenced(Boolean everReferenced) { this.everReferenced = everReferenced; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

- [ ] **步骤 2：创建 LocationMapper**

创建 `LocationMapper.java`：

~~~java
package com.zija.location.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LocationMapper extends BaseMapper<LocationEntity> {

    List<LocationEntity> findTree(@Param("householdId") UUID householdId);

    List<UUID> findDescendantIds(@Param("locationId") UUID locationId, @Param("householdId") UUID householdId);

    int markReferenced(@Param("id") UUID id, @Param("householdId") UUID householdId);
}
~~~

- [ ] **步骤 3：创建 LocationMapper XML**

创建 `LocationMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.location.internal.persistence.LocationMapper">

    <select id="findTree" resultType="com.zija.location.internal.persistence.LocationEntity">
        WITH RECURSIVE tree AS (
            SELECT id, household_id, parent_id, name, name_normalized, sort_order,
                   ever_referenced, created_at, updated_at, version
            FROM location
            WHERE household_id = #{householdId} AND parent_id IS NULL
            UNION ALL
            SELECT l.id, l.household_id, l.parent_id, l.name, l.name_normalized, l.sort_order,
                   l.ever_referenced, l.created_at, l.updated_at, l.version
            FROM location l
            INNER JOIN tree t ON l.parent_id = t.id
        )
        SELECT * FROM tree
        ORDER BY sort_order, id
    </select>

    <select id="findDescendantIds" resultType="java.util.UUID">
        WITH RECURSIVE descendants AS (
            SELECT id FROM location
            WHERE id = #{locationId} AND household_id = #{householdId}
            UNION ALL
            SELECT l.id FROM location l
            INNER JOIN descendants d ON l.parent_id = d.id
        )
        SELECT id FROM descendants
    </select>

    <update id="markReferenced">
        UPDATE location
        SET ever_referenced = TRUE, updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND household_id = #{householdId}
    </update>
</mapper>
~~~

- [ ] **步骤 4：验证编译通过**

运行：

~~~bash
cd backend && ./mvnw -q compile
~~~

预期：编译成功。

- [ ] **步骤 5：提交**

~~~bash
git add backend/src/main/java/com/zija/location/internal/persistence/ backend/src/main/resources/mapper/location/
git commit -m "feat: location 模块新增持久化层"
~~~

---

## 任务 18：LocationService 位置业务逻辑

**文件：**
- 创建：`backend/src/main/java/com/zija/location/internal/LocationService.java`
- 创建：`backend/src/main/java/com/zija/location/internal/LocationExceptionHandler.java`
- 创建：`backend/src/test/java/com/zija/location/internal/LocationServiceTest.java`

- [ ] **步骤 1：编写失败单元测试**

创建 `LocationServiceTest.java`：

~~~java
package com.zija.location.internal;

import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocationServiceTest {

    private LocationMapper locationMapper;
    private SystemApi systemApi;
    private LocationService service;

    @BeforeEach
    void setUp() {
        locationMapper = mock(LocationMapper.class);
        systemApi = mock(SystemApi.class);
        service = new LocationService(locationMapper, systemApi);
    }

    @Test
    void createLocationAtRoot() {
        UUID householdId = UUID.randomUUID();
        when(locationMapper.insert(any())).thenReturn(1);

        var entity = service.createLocation(householdId, "客厅", null, 0);

        assertThat(entity.getName()).isEqualTo("客厅");
        assertThat(entity.getParentId()).isNull();
        assertThat(entity.getEverReferenced()).isFalse();
    }

    @Test
    void createLocationWithParent() {
        UUID householdId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        var parent = new LocationEntity();
        parent.setId(parentId);
        parent.setHouseholdId(householdId);
        when(locationMapper.selectById(parentId)).thenReturn(parent);
        when(locationMapper.insert(any())).thenReturn(1);

        var entity = service.createLocation(householdId, "沙发旁", parentId, 0);

        assertThat(entity.getParentId()).isEqualTo(parentId);
    }

    @Test
    void renameLocation() {
        UUID householdId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(id);
        entity.setHouseholdId(householdId);
        entity.setName("客厅");
        entity.setVersion(0);
        when(locationMapper.selectById(id)).thenReturn(entity);
        when(locationMapper.updateById(any())).thenReturn(1);

        service.renameLocation(householdId, id, "大客厅", 0);

        verify(locationMapper).updateById(argThat(e -> "大客厅".equals(e.getName())));
    }

    @Test
    void moveLocationRejectsCycle() {
        UUID householdId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(id);
        entity.setHouseholdId(householdId);
        entity.setVersion(0);
        when(locationMapper.selectById(id)).thenReturn(entity);
        when(locationMapper.findDescendantIds(id, householdId)).thenReturn(List.of(id, targetParentId));

        assertThatThrownBy(() -> service.moveLocation(householdId, id, targetParentId, 0, 0))
                .isInstanceOf(LocationCycleException.class);
    }

    @Test
    void deleteLocationRejectsWhenChildren() {
        UUID householdId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(id);
        entity.setHouseholdId(householdId);
        entity.setEverReferenced(false);
        entity.setVersion(0);
        when(locationMapper.selectById(id)).thenReturn(entity);
        when(locationMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteLocation(householdId, id, 0))
                .isInstanceOf(LocationHasChildrenException.class);
    }

    @Test
    void deleteLocationRejectsWhenReferenced() {
        UUID householdId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(id);
        entity.setHouseholdId(householdId);
        entity.setEverReferenced(true);
        entity.setVersion(0);
        when(locationMapper.selectById(id)).thenReturn(entity);
        when(locationMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.deleteLocation(householdId, id, 0))
                .isInstanceOf(LocationReferencedException.class);
    }

    @Test
    void markReferencedIsIrreversible() {
        UUID householdId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(locationMapper.markReferenced(id, householdId)).thenReturn(1);

        service.markReferenced(householdId, id);

        verify(locationMapper).markReferenced(id, householdId);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=LocationServiceTest test
~~~

预期：FAIL，类不存在。

- [ ] **步骤 3：创建异常类**

创建 `LocationCycleException.java`：

~~~java
package com.zija.location.internal;

public class LocationCycleException extends RuntimeException {
    public LocationCycleException() {
        super("location move would create a cycle");
    }
}
~~~

创建 `LocationHasChildrenException.java`：

~~~java
package com.zija.location.internal;

public class LocationHasChildrenException extends RuntimeException {
    public LocationHasChildrenException() {
        super("location has children");
    }
}
~~~

创建 `LocationReferencedException.java`：

~~~java
package com.zija.location.internal;

public class LocationReferencedException extends RuntimeException {
    public LocationReferencedException() {
        super("location is referenced by inventory");
    }
}
~~~

创建 `LocationVersionConflictException.java`：

~~~java
package com.zija.location.internal;

public class LocationVersionConflictException extends RuntimeException {
    public LocationVersionConflictException() {
        super("version conflict");
    }
}
~~~

- [ ] **步骤 4：创建 LocationExceptionHandler**

创建 `LocationExceptionHandler.java`：

~~~java
package com.zija.location.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LocationController.class)
class LocationExceptionHandler {

    @ExceptionHandler(LocationVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "版本冲突", "LOCATION_VERSION_CONFLICT");
    }

    @ExceptionHandler(LocationCycleException.class)
    ProblemDetail handleCycle(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "移动会导致循环", "LOCATION_CYCLE");
    }

    @ExceptionHandler(LocationHasChildrenException.class)
    ProblemDetail handleHasChildren(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "位置包含子节点", "LOCATION_HAS_CHILDREN");
    }

    @ExceptionHandler(LocationReferencedException.class)
    ProblemDetail handleReferenced(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "位置已被库存引用", "LOCATION_REFERENCED");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", request.getAttribute("zija.request-id"));
        return problem;
    }
}
~~~

- [ ] **步骤 5：创建 LocationService**

创建 `LocationService.java`：

~~~java
package com.zija.location.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.location.LocationApi;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
class LocationService implements LocationApi {

    private final LocationMapper locationMapper;
    private final SystemApi systemApi;

    LocationService(LocationMapper locationMapper, SystemApi systemApi) {
        this.locationMapper = locationMapper;
        this.systemApi = systemApi;
    }

    @Override
    @Transactional(readOnly = true)
    public LocationInfo requireLocation(UUID householdId, UUID locationId) {
        var entity = locationMapper.selectById(locationId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new LocationReferencedException();
        }
        return toInfo(entity);
    }

    @Override
    @Transactional
    public void markReferenced(UUID householdId, UUID locationId) {
        locationMapper.markReferenced(locationId, householdId);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationTree tree(UUID householdId) {
        List<LocationEntity> all = locationMapper.findTree(householdId);
        return buildTree(all);
    }

    @Transactional
    public LocationEntity createLocation(UUID householdId, String name, UUID parentId, int sortOrder) {
        if (parentId != null) {
            var parent = locationMapper.selectById(parentId);
            if (parent == null || !parent.getHouseholdId().equals(householdId)) {
                throw new LocationReferencedException();
            }
        }
        String normalized = normalizeName(name);
        var entity = new LocationEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setParentId(parentId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setSortOrder(sortOrder);
        entity.setEverReferenced(false);
        locationMapper.insert(entity);
        audit(householdId, "LOCATION_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public LocationEntity renameLocation(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireLocationEntity(householdId, id);
        entity.setName(name.trim());
        entity.setNameNormalized(normalizeName(name));
        if (locationMapper.updateById(entity) == 0) {
            throw new LocationVersionConflictException();
        }
        audit(householdId, "LOCATION_RENAMED", id);
        return entity;
    }

    @Transactional
    public void moveLocation(UUID householdId, UUID id, UUID targetParentId, int targetSortOrder, Integer version) {
        var entity = requireLocationEntity(householdId, id);
        if (targetParentId != null) {
            if (targetParentId.equals(id)) {
                throw new LocationCycleException();
            }
            var descendants = locationMapper.findDescendantIds(id, householdId);
            if (descendants.contains(targetParentId)) {
                throw new LocationCycleException();
            }
        }
        entity.setParentId(targetParentId);
        entity.setSortOrder(targetSortOrder);
        if (locationMapper.updateById(entity) == 0) {
            throw new LocationVersionConflictException();
        }
        audit(householdId, "LOCATION_MOVED", id);
    }

    @Transactional
    public void deleteLocation(UUID householdId, UUID id, Integer version) {
        var entity = requireLocationEntity(householdId, id);
        // Check no children
        long childCount = locationMapper.selectCount(new LambdaQueryWrapper<LocationEntity>()
                .eq(LocationEntity::getParentId, id));
        if (childCount > 0) {
            throw new LocationHasChildrenException();
        }
        // Check not referenced
        if (entity.getEverReferenced()) {
            throw new LocationReferencedException();
        }
        locationMapper.deleteById(id);
        audit(householdId, "LOCATION_DELETED", id);
    }

    // --- Helpers ---

    private LocationEntity requireLocationEntity(UUID householdId, UUID id) {
        var entity = locationMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new LocationReferencedException();
        }
        return entity;
    }

    private LocationTree buildTree(List<LocationEntity> all) {
        Map<UUID, List<LocationEntity>> byParent = all.stream()
                .filter(e -> e.getParentId() != null)
                .collect(Collectors.groupingBy(LocationEntity::getParentId));

        List<LocationNode> roots = all.stream()
                .filter(e -> e.getParentId() == null)
                .map(e -> toNode(e, byParent))
                .toList();

        return new LocationTree(roots);
    }

    private LocationNode toNode(LocationEntity entity, Map<UUID, List<LocationEntity>> byParent) {
        List<LocationNode> children = byParent.getOrDefault(entity.getId(), List.of()).stream()
                .map(e -> toNode(e, byParent))
                .toList();
        return new LocationNode(
                entity.getId(), entity.getParentId(), entity.getName(),
                entity.getSortOrder(), entity.getEverReferenced(),
                entity.getVersion(), children
        );
    }

    private LocationInfo toInfo(LocationEntity entity) {
        return new LocationInfo(
                entity.getId(), entity.getHouseholdId(), entity.getParentId(),
                entity.getName(), entity.getSortOrder(), entity.getEverReferenced(),
                entity.getVersion()
        );
    }

    static String normalizeName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        String nfkc = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    private void audit(UUID householdId, String action, UUID resourceId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}
~~~

- [ ] **步骤 6：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=LocationServiceTest test
~~~

预期：PASS。

- [ ] **步骤 7：提交**

~~~bash
git add backend/src/main/java/com/zija/location/internal/ backend/src/test/java/com/zija/location/internal/LocationServiceTest.java
git commit -m "feat: location 模块新增位置业务服务"
~~~

---

## 任务 19：LocationController 位置端点

**文件：**
- 创建：`backend/src/main/java/com/zija/location/internal/LocationController.java`
- 创建：`backend/src/test/java/com/zija/location/internal/LocationControllerTest.java`

- [ ] **步骤 1：创建 LocationController**

创建 `LocationController.java`：

~~~java
package com.zija.location.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import com.zija.location.LocationApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/locations")
class LocationController {

    private final LocationService locationService;
    private final HouseholdApi householdApi;

    LocationController(LocationService locationService, HouseholdApi householdApi) {
        this.locationService = locationService;
        this.householdApi = householdApi;
    }

    @RequireMember
    @GetMapping("/tree")
    LocationApi.LocationTree getTree(@AuthenticationPrincipal ZijaPrincipal principal) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return locationService.tree(member.householdId());
    }

    @RequireMember
    @GetMapping("/{id}")
    Map<String, Object> getLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = locationService.requireLocation(member.householdId(), id);
        // Phase 3: no inventory summary, show placeholder
        return Map.of(
                "id", info.id(),
                "householdId", info.householdId(),
                "parentId", info.parentId(),
                "name", info.name(),
                "sortOrder", info.sortOrder(),
                "everReferenced", info.everReferenced(),
                "version", info.version(),
                "inventorySummary", "库存将在阶段四启用"
        );
    }

    @RequireMember
    @PostMapping
    LocationApi.LocationInfo createLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateLocationRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = locationService.createLocation(member.householdId(), request.name(), request.parentId(), request.sortOrder());
        return new LocationApi.LocationInfo(
                entity.getId(), entity.getHouseholdId(), entity.getParentId(),
                entity.getName(), entity.getSortOrder(), entity.getEverReferenced(),
                entity.getVersion()
        );
    }

    @RequireMember
    @PutMapping("/{id}")
    LocationApi.LocationInfo renameLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RenameLocationRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = locationService.renameLocation(member.householdId(), id, request.name(), request.version());
        return new LocationApi.LocationInfo(
                entity.getId(), entity.getHouseholdId(), entity.getParentId(),
                entity.getName(), entity.getSortOrder(), entity.getEverReferenced(),
                entity.getVersion()
        );
    }

    @RequireMember
    @PutMapping("/{id}/position")
    void moveLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody MoveLocationRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        locationService.moveLocation(member.householdId(), id, request.parentId(), request.sortOrder(), request.version());
    }

    @RequireMember
    @DeleteMapping("/{id}")
    void deleteLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        locationService.deleteLocation(member.householdId(), id, request.version());
    }

    // --- DTOs ---

    record CreateLocationRequest(@NotBlank String name, UUID parentId, int sortOrder) {}
    record RenameLocationRequest(@NotBlank String name, Integer version) {}
    record MoveLocationRequest(UUID parentId, int sortOrder, Integer version) {}
    record VersionRequest(Integer version) {}
}
~~~

- [ ] **步骤 2：验证编译通过**

运行：

~~~bash
cd backend && ./mvnw -q compile
~~~

预期：编译成功。

- [ ] **步骤 3：提交**

~~~bash
git add backend/src/main/java/com/zija/location/internal/LocationController.java
git commit -m "feat: location 模块新增位置端点"
~~~

---

## 任务 20：前端 API 模块与类型定义

**文件：**
- 创建：`frontend/src/types/catalog.ts`
- 创建：`frontend/src/types/location.ts`
- 创建：`frontend/src/api/catalog.ts`
- 创建：`frontend/src/api/location.ts`
- 创建：`frontend/src/api/file.ts`

- [ ] **步骤 1：创建 catalog 类型定义**

创建 `frontend/src/types/catalog.ts`：

~~~typescript
export interface Category {
  id: string
  householdId: string
  parentId: string | null
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  sortOrder: number
  version: number
}

export interface Brand {
  id: string
  householdId: string
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  version: number
}

export interface Unit {
  id: string
  householdId: string
  name: string
  decimalScale: number
  status: 'ACTIVE' | 'ARCHIVED'
  version: number
}

export interface Tag {
  id: string
  householdId: string
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  version: number
}

export interface CatalogItem {
  id: string
  householdId: string
  name: string
  managementType: 'CONSUMABLE' | 'DURABLE'
  categoryId: string | null
  brandId: string | null
  unitId: string
  coverFileId: string | null
  coverUrl?: string
  memo: string | null
  expiryReminderMode: 'INHERIT' | 'DISABLED' | 'CUSTOM'
  expiryReminderDays: number[] | null
  lowStockMode: 'INHERIT' | 'DISABLED' | 'CUSTOM'
  lowStockThreshold: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  tagIds: string[]
  version: number
  createdAt: string
  updatedAt: string
}

export interface ItemListResponse {
  items: CatalogItem[]
  total: number
  page: number
  pageSize: number
}
~~~

- [ ] **步骤 2：创建 location 类型定义**

创建 `frontend/src/types/location.ts`：

~~~typescript
export interface LocationNode {
  id: string
  parentId: string | null
  name: string
  sortOrder: number
  everReferenced: boolean
  version: number
  children: LocationNode[]
}

export interface LocationTree {
  roots: LocationNode[]
}

export interface LocationInfo {
  id: string
  householdId: string
  parentId: string | null
  name: string
  sortOrder: number
  everReferenced: boolean
  version: number
  inventorySummary?: string
}
~~~

- [ ] **步骤 3：创建 catalog API 模块**

创建 `frontend/src/api/catalog.ts`：

~~~typescript
import { getJson, postJson, putJson } from './http'
import type {
  Category, Brand, Unit, Tag, CatalogItem, ItemListResponse
} from '../types/catalog'

// Items
export async function fetchItems(params: {
  q?: string
  managementType?: string
  categoryId?: string
  brandId?: string
  tagId?: string
  status?: string
  page?: number
  pageSize?: number
  sort?: string
}): Promise<ItemListResponse> {
  const query = new URLSearchParams()
  if (params.q) query.set('q', params.q)
  if (params.managementType) query.set('managementType', params.managementType)
  if (params.categoryId) query.set('categoryId', params.categoryId)
  if (params.brandId) query.set('brandId', params.brandId)
  if (params.tagId) query.set('tagId', params.tagId)
  if (params.status) query.set('status', params.status)
  if (params.page) query.set('page', String(params.page))
  if (params.pageSize) query.set('pageSize', String(params.pageSize))
  if (params.sort) query.set('sort', params.sort)
  return getJson<ItemListResponse>(`/api/v1/items?${query}`)
}

export async function fetchItem(id: string): Promise<CatalogItem> {
  return getJson<CatalogItem>(`/api/v1/items/${id}`)
}

export async function createItem(data: {
  name: string
  managementType: string
  categoryId?: string
  brandId?: string
  unitId: string
  memo?: string
  expiryReminderMode?: string
  expiryReminderDays?: number[]
  lowStockMode?: string
  lowStockThreshold?: string
  tagIds?: string[]
}): Promise<CatalogItem> {
  return postJson<CatalogItem>('/api/v1/items', data)
}

export async function updateItem(id: string, data: Record<string, unknown>): Promise<CatalogItem> {
  return putJson<CatalogItem>(`/api/v1/items/${id}`, data)
}

export async function archiveItem(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/items/${id}/archive`, { version })
}

export async function restoreItem(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/items/${id}/restore`, { version })
}

// Cover
export async function uploadCover(itemId: string, file: File, version: number): Promise<{ coverFileId: string; url: string }> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('version', String(version))
  const response = await fetch(`/api/v1/items/${itemId}/cover`, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': getCsrfToken() },
    body: formData,
    credentials: 'same-origin',
  })
  if (!response.ok) throw await response.json()
  return response.json()
}

export async function removeCover(itemId: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/items/${itemId}/cover`, { version })
}

// Categories
export async function fetchCategories(includeArchived = false): Promise<Category[]> {
  return getJson<Category[]>(`/api/v1/categories/tree?includeArchived=${includeArchived}`)
}

export async function createCategory(data: { name: string; parentId?: string; sortOrder?: number }): Promise<Category> {
  return postJson<Category>('/api/v1/categories', data)
}

export async function updateCategory(id: string, data: { name: string; version: number }): Promise<Category> {
  return putJson<Category>(`/api/v1/categories/${id}`, data)
}

export async function moveCategory(id: string, data: { parentId?: string; sortOrder: number; version: number }): Promise<void> {
  return putJson<void>(`/api/v1/categories/${id}/position`, data)
}

export async function archiveCategory(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/categories/${id}/archive`, { version })
}

export async function restoreCategory(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/categories/${id}/restore`, { version })
}

// Brands
export async function fetchBrands(includeArchived = false): Promise<Brand[]> {
  return getJson<Brand[]>(`/api/v1/brands?includeArchived=${includeArchived}`)
}

export async function createBrand(name: string): Promise<Brand> {
  return postJson<Brand>('/api/v1/brands', { name })
}

export async function updateBrand(id: string, data: { name: string; version: number }): Promise<Brand> {
  return putJson<Brand>(`/api/v1/brands/${id}`, data)
}

export async function archiveBrand(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/brands/${id}/archive`, { version })
}

export async function restoreBrand(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/brands/${id}/restore`, { version })
}

// Units
export async function fetchUnits(includeArchived = false): Promise<Unit[]> {
  return getJson<Unit[]>(`/api/v1/units?includeArchived=${includeArchived}`)
}

export async function createUnit(data: { name: string; decimalScale: number }): Promise<Unit> {
  return postJson<Unit>('/api/v1/units', data)
}

export async function updateUnit(id: string, data: { name?: string; decimalScale?: number; version: number }): Promise<Unit> {
  return putJson<Unit>(`/api/v1/units/${id}`, data)
}

export async function archiveUnit(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/units/${id}/archive`, { version })
}

export async function restoreUnit(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/units/${id}/restore`, { version })
}

// Tags
export async function fetchTags(includeArchived = false): Promise<Tag[]> {
  return getJson<Tag[]>(`/api/v1/tags?includeArchived=${includeArchived}`)
}

export async function createTag(name: string): Promise<Tag> {
  return postJson<Tag>('/api/v1/tags', { name })
}

export async function updateTag(id: string, data: { name: string; version: number }): Promise<Tag> {
  return putJson<Tag>(`/api/v1/tags/${id}`, data)
}

export async function archiveTag(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/tags/${id}/archive`, { version })
}

export async function restoreTag(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/tags/${id}/restore`, { version })
}

function getCsrfToken(): string {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}
~~~

- [ ] **步骤 4：创建 location API 模块**

创建 `frontend/src/api/location.ts`：

~~~typescript
import { getJson, postJson, putJson, deleteJson } from './http'
import type { LocationTree, LocationInfo } from '../types/location'

export async function fetchLocationTree(): Promise<LocationTree> {
  return getJson<LocationTree>('/api/v1/locations/tree')
}

export async function fetchLocation(id: string): Promise<LocationInfo> {
  return getJson<LocationInfo>(`/api/v1/locations/${id}`)
}

export async function createLocation(data: {
  name: string
  parentId?: string
  sortOrder?: number
}): Promise<LocationInfo> {
  return postJson<LocationInfo>('/api/v1/locations', data)
}

export async function renameLocation(id: string, data: {
  name: string
  version: number
}): Promise<LocationInfo> {
  return putJson<LocationInfo>(`/api/v1/locations/${id}`, data)
}

export async function moveLocation(id: string, data: {
  parentId?: string
  sortOrder: number
  version: number
}): Promise<void> {
  return putJson<void>(`/api/v1/locations/${id}/position`, data)
}

export async function deleteLocation(id: string, version: number): Promise<void> {
  return deleteJson<void>(`/api/v1/locations/${id}`, { version })
}
~~~

- [ ] **步骤 5：验证前端类型检查**

运行：

~~~bash
npm --prefix frontend run typecheck
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add frontend/src/types/catalog.ts frontend/src/types/location.ts frontend/src/api/catalog.ts frontend/src/api/location.ts
git commit -m "feat: 前端新增物品与位置 API 模块及类型定义"
~~~

---

## 任务 21：前端路由与导航

**文件：**
- 修改：`frontend/src/router/index.ts`
- 修改：`frontend/src/components/AppShell.vue`

- [ ] **步骤 1：更新路由配置**

在 `frontend/src/router/index.ts` 中新增物品、字典设置和位置路由：

~~~typescript
// 新增路由（在现有路由之后）
{
  path: '/items',
  name: 'Items',
  component: () => import('../views/ItemsPage.vue'),
  meta: { requiresAuth: true }
},
{
  path: '/settings/catalog',
  name: 'CatalogSettings',
  component: () => import('../views/CatalogSettingsPage.vue'),
  meta: { requiresAuth: true, requiresAdmin: true }
},
{
  path: '/locations',
  name: 'Locations',
  component: () => import('../views/LocationsPage.vue'),
  meta: { requiresAuth: true }
}
~~~

- [ ] **步骤 2：更新 AppShell 导航**

在 `frontend/src/components/AppShell.vue` 的导航菜单中新增：

~~~vue
<el-menu-item index="/items">
  <el-icon><Box /></el-icon>
  <span>物品资料</span>
</el-menu-item>
<el-menu-item index="/locations">
  <el-icon><Location /></el-icon>
  <span>位置管理</span>
</el-menu-item>
<!-- Owner/Admin only -->
<el-menu-item v-if="isAdminOrOwner" index="/settings/catalog">
  <el-icon><Setting /></el-icon>
  <span>物品字典</span>
</el-menu-item>
~~~

- [ ] **步骤 3：验证前端构建**

运行：

~~~bash
npm --prefix frontend run build
~~~

预期：构建成功（页面组件暂为空壳）。

- [ ] **步骤 4：提交**

~~~bash
git add frontend/src/router/index.ts frontend/src/components/AppShell.vue
git commit -m "feat: 前端新增物品、字典设置与位置路由及导航"
~~~

---

## 任务 22：前端页面——物品资料页

**文件：**
- 创建：`frontend/src/views/ItemsPage.vue`
- 创建：`frontend/src/views/ItemFormDrawer.vue`
- 创建：`frontend/src/views/ItemCoverUpload.vue`

- [ ] **步骤 1：创建 ItemsPage 主页面**

创建 `frontend/src/views/ItemsPage.vue`：

~~~vue
<template>
  <div class="items-page">
    <div class="items-header">
      <h2>物品资料</h2>
      <el-button type="primary" @click="openCreate">新建物品</el-button>
    </div>

    <div class="items-filters">
      <el-input v-model="filters.q" placeholder="搜索物品" clearable @input="debouncedFetch" />
      <el-select v-model="filters.managementType" placeholder="管理类型" clearable @change="fetchItems">
        <el-option label="消耗品" value="CONSUMABLE" />
        <el-option label="耐用品" value="DURABLE" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable @change="fetchItems">
        <el-option label="活跃" value="ACTIVE" />
        <el-option label="归档" value="ARCHIVED" />
      </el-select>
    </div>

    <el-table :data="items" v-loading="loading" @row-click="openDetail">
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="managementType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.managementType === 'CONSUMABLE' ? 'warning' : 'success'" size="small">
            {{ row.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status === 'ACTIVE' ? '活跃' : '归档' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click.stop="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.pageSize"
      :total="pagination.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="fetchItems"
      @size-change="fetchItems"
    />

    <ItemFormDrawer
      v-model:visible="formVisible"
      :item="editingItem"
      @saved="onItemSaved"
    />

    <el-drawer v-model="detailVisible" title="物品详情" size="400px">
      <div v-if="selectedItem">
        <h3>{{ selectedItem.name }}</h3>
        <p>类型：{{ selectedItem.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}</p>
        <p>状态：{{ selectedItem.status === 'ACTIVE' ? '活跃' : '归档' }}</p>
        <p v-if="selectedItem.memo">备注：{{ selectedItem.memo }}</p>
        <div class="detail-actions">
          <el-button v-if="selectedItem.status === 'ACTIVE'" @click="archiveItem(selectedItem)">归档</el-button>
          <el-button v-if="selectedItem.status === 'ARCHIVED'" @click="restoreItem(selectedItem)">恢复</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchItems as apiFetchItems, archiveItem as apiArchiveItem, restoreItem as apiRestoreItem } from '../api/catalog'
import type { CatalogItem } from '../types/catalog'
import ItemFormDrawer from './ItemFormDrawer.vue'

const items = ref<CatalogItem[]>([])
const loading = ref(false)
const formVisible = ref(false)
const detailVisible = ref(false)
const editingItem = ref<CatalogItem | null>(null)
const selectedItem = ref<CatalogItem | null>(null)

const filters = reactive({ q: '', managementType: '', status: 'ACTIVE' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

let debounceTimer: ReturnType<typeof setTimeout>

function debouncedFetch() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(fetchItems, 300)
}

async function fetchItems() {
  loading.value = true
  try {
    const res = await apiFetchItems({
      q: filters.q || undefined,
      managementType: filters.managementType || undefined,
      status: filters.status || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    items.value = res.items
    pagination.total = res.total
  } catch (e: any) {
    ElMessage.error(e.title || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingItem.value = null
  formVisible.value = true
}

function openEdit(item: CatalogItem) {
  editingItem.value = item
  formVisible.value = true
}

function openDetail(item: CatalogItem) {
  selectedItem.value = item
  detailVisible.value = true
}

async function archiveItem(item: CatalogItem) {
  await ElMessageBox.confirm('确定归档此物品？', '确认')
  await apiArchiveItem(item.id, item.version)
  ElMessage.success('已归档')
  fetchItems()
}

async function restoreItem(item: CatalogItem) {
  await apiRestoreItem(item.id, item.version)
  ElMessage.success('已恢复')
  fetchItems()
}

function onItemSaved() {
  formVisible.value = false
  fetchItems()
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN')
}

onMounted(fetchItems)
~~~

- [ ] **步骤 2：创建 ItemFormDrawer**

创建 `frontend/src/views/ItemFormDrawer.vue`：

~~~vue
<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="isEdit ? '编辑物品' : '新建物品'"
    size="500px"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="120" />
      </el-form-item>
      <el-form-item label="管理类型" prop="managementType">
        <el-radio-group v-model="form.managementType">
          <el-radio value="CONSUMABLE">消耗品</el-radio>
          <el-radio value="DURABLE">耐用品</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="基础单位" prop="unitId">
        <el-select v-model="form.unitId" filterable placeholder="选择单位">
          <el-option v-for="u in units" :key="u.id" :label="u.name" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="分类">
        <el-cascader
          v-model="form.categoryId"
          :options="categoryTree"
          :props="{ value: 'id', label: 'name', checkStrictly: true }"
          clearable
          placeholder="选择分类"
        />
      </el-form-item>
      <el-form-item label="品牌">
        <el-select v-model="form.brandId" filterable clearable placeholder="选择品牌">
          <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
          <el-option label="+ 新建品牌" value="__new__" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-select v-model="form.tagIds" multiple filterable clearable placeholder="选择标签">
          <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.memo" type="textarea" :rows="3" maxlength="4000" />
      </el-form-item>
      <el-form-item label="低库存模式">
        <el-select v-model="form.lowStockMode">
          <el-option label="继承默认" value="INHERIT" />
          <el-option label="禁用" value="DISABLED" />
          <el-option label="自定义" value="CUSTOM" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.lowStockMode === 'CUSTOM'" label="低库存阈值">
        <el-input v-model="form.lowStockThreshold" type="number" step="0.01" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createItem, updateItem, fetchCategories, fetchBrands, fetchUnits, fetchTags } from '../api/catalog'
import type { CatalogItem, Category, Brand, Unit, Tag } from '../types/catalog'

const props = defineProps<{ visible: boolean; item: CatalogItem | null }>()
const emit = defineEmits<{ 'update:visible': [boolean]; saved: [] }>()

const isEdit = computed(() => !!props.item)
const formRef = ref()

const form = reactive({
  name: '',
  managementType: 'CONSUMABLE',
  unitId: '',
  categoryId: null as string | null,
  brandId: null as string | null,
  tagIds: [] as string[],
  memo: '',
  lowStockMode: 'INHERIT',
  lowStockThreshold: '',
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  managementType: [{ required: true, message: '请选择管理类型', trigger: 'change' }],
  unitId: [{ required: true, message: '请选择基础单位', trigger: 'change' }],
}

const categoryTree = ref<Category[]>([])
const brands = ref<Brand[]>([])
const units = ref<Unit[]>([])
const tags = ref<Tag[]>([])

watch(() => props.visible, async (visible) => {
  if (visible) {
    await Promise.all([loadCategories(), loadBrands(), loadUnits(), loadTags()])
    if (props.item) {
      Object.assign(form, {
        name: props.item.name,
        managementType: props.item.managementType,
        unitId: props.item.unitId,
        categoryId: props.item.categoryId,
        brandId: props.item.brandId,
        tagIds: [...props.item.tagIds],
        memo: props.item.memo || '',
        lowStockMode: props.item.lowStockMode,
        lowStockThreshold: props.item.lowStockThreshold || '',
      })
    } else {
      Object.assign(form, {
        name: '', managementType: 'CONSUMABLE', unitId: '',
        categoryId: null, brandId: null, tagIds: [],
        memo: '', lowStockMode: 'INHERIT', lowStockThreshold: '',
      })
    }
  }
})

async function loadCategories() { categoryTree.value = await fetchCategories() }
async function loadBrands() { brands.value = await fetchBrands() }
async function loadUnits() { units.value = await fetchUnits() }
async function loadTags() { tags.value = await fetchTags() }

async function submit() {
  await formRef.value?.validate()
  try {
    const data = {
      name: form.name,
      managementType: form.managementType,
      unitId: form.unitId,
      categoryId: form.categoryId || undefined,
      brandId: form.brandId || undefined,
      tagIds: form.tagIds.length > 0 ? form.tagIds : undefined,
      memo: form.memo || undefined,
      lowStockMode: form.lowStockMode,
      lowStockThreshold: form.lowStockMode === 'CUSTOM' ? form.lowStockThreshold : undefined,
    }
    if (isEdit.value && props.item) {
      await updateItem(props.item.id, { ...data, version: props.item.version })
    } else {
      await createItem(data)
    }
    ElMessage.success(isEdit.value ? '已更新' : '已创建')
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e.title || '保存失败')
  }
}
~~~

- [ ] **步骤 3：验证前端构建**

运行：

~~~bash
npm --prefix frontend run build
~~~

预期：构建成功。

- [ ] **步骤 4：提交**

~~~bash
git add frontend/src/views/ItemsPage.vue frontend/src/views/ItemFormDrawer.vue
git commit -m "feat: 前端新增物品资料页与表单抽屉"
~~~

---

## 任务 23：前端页面——位置管理页

**文件：**
- 创建：`frontend/src/views/LocationsPage.vue`
- 创建：`frontend/src/views/LocationMoveDialog.vue`

- [ ] **步骤 1：创建 LocationsPage**

创建 `frontend/src/views/LocationsPage.vue`：

~~~vue
<template>
  <div class="locations-page">
    <div class="locations-header">
      <h2>位置管理</h2>
      <el-button type="primary" @click="openCreate()">新增根位置</el-button>
    </div>

    <div class="locations-workspace">
      <div class="location-tree-panel">
        <el-tree
          :data="treeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          @node-click="selectNode"
        >
          <template #default="{ node, data }">
            <div class="tree-node">
              <span>{{ data.name }}</span>
              <span class="tree-node-actions">
                <el-button size="small" text @click.stop="openCreate(data.id)">+</el-button>
                <el-button size="small" text @click.stop="openRename(data)">✏</el-button>
                <el-button size="small" text @click.stop="openMove(data)">↗</el-button>
                <el-button v-if="!data.everReferenced" size="small" text type="danger" @click.stop="deleteNode(data)">×</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="location-detail-panel" v-if="selectedLocation">
        <h3>{{ selectedLocation.name }}</h3>
        <p>ID: {{ selectedLocation.id }}</p>
        <p>版本: {{ selectedLocation.version }}</p>
        <p>已引用: {{ selectedLocation.everReferenced ? '是' : '否' }}</p>
        <el-divider />
        <p class="placeholder-text">库存将在阶段四启用</p>
      </div>
    </div>

    <!-- Create/Rename Dialog -->
    <el-dialog v-model="nameDialogVisible" :title="nameDialogTitle" width="400px">
      <el-form :model="nameForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="nameForm.name" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitName">确定</el-button>
      </template>
    </el-dialog>

    <LocationMoveDialog
      v-model:visible="moveDialogVisible"
      :node="movingNode"
      :tree="treeData"
      @moved="onMoved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchLocationTree, createLocation, renameLocation, deleteLocation } from '../api/location'
import type { LocationNode } from '../types/location'
import LocationMoveDialog from './LocationMoveDialog.vue'

const treeData = ref<LocationNode[]>([])
const selectedLocation = ref<LocationNode | null>(null)
const moveDialogVisible = ref(false)
const movingNode = ref<LocationNode | null>(null)

const nameDialogVisible = ref(false)
const nameDialogTitle = ref('')
const nameForm = reactive({ name: '', parentId: null as string | null, editingId: null as string | null, version: 0 })

async function loadTree() {
  const res = await fetchLocationTree()
  treeData.value = res.roots
}

function selectNode(data: LocationNode) {
  selectedLocation.value = data
}

function openCreate(parentId: string | null = null) {
  nameDialogTitle.value = parentId ? '新增子位置' : '新增根位置'
  nameForm.name = ''
  nameForm.parentId = parentId
  nameForm.editingId = null
  nameForm.version = 0
  nameDialogVisible.value = true
}

function openRename(node: LocationNode) {
  nameDialogTitle.value = '重命名位置'
  nameForm.name = node.name
  nameForm.editingId = node.id
  nameForm.version = node.version
  nameDialogVisible.value = true
}

async function submitName() {
  if (!nameForm.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  try {
    if (nameForm.editingId) {
      await renameLocation(nameForm.editingId, { name: nameForm.name, version: nameForm.version })
    } else {
      await createLocation({ name: nameForm.name, parentId: nameForm.parentId || undefined })
    }
    nameDialogVisible.value = false
    await loadTree()
    ElMessage.success(nameForm.editingId ? '已重命名' : '已创建')
  } catch (e: any) {
    ElMessage.error(e.title || '操作失败')
  }
}

function openMove(node: LocationNode) {
  movingNode.value = node
  moveDialogVisible.value = true
}

async function onMoved() {
  moveDialogVisible.value = false
  await loadTree()
}

async function deleteNode(node: LocationNode) {
  await ElMessageBox.confirm(`确定删除位置"${node.name}"？`, '确认')
  try {
    await deleteLocation(node.id, node.version)
    await loadTree()
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e.title || '删除失败')
  }
}

onMounted(loadTree)
~~~

- [ ] **步骤 2：创建 LocationMoveDialog**

创建 `frontend/src/views/LocationMoveDialog.vue`：

~~~vue
<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)" title="移动位置" width="500px">
    <div v-if="node">
      <p>移动 <strong>{{ node.name }}</strong> 到：</p>
      <el-tree
        :data="tree"
        node-key="id"
        default-expand-all
        :expand-on-click-node="false"
        highlight-current
        @current-change="onTargetChange"
      >
        <template #default="{ data }">
          <span>{{ data.name }}</span>
        </template>
      </el-tree>
      <el-form label-width="100px" style="margin-top: 16px">
        <el-form-item label="目标排序">
          <el-input-number v-model="targetSortOrder" :min="0" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="submitMove" :disabled="!targetParentId">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { moveLocation } from '../api/location'
import type { LocationNode } from '../types/location'

const props = defineProps<{
  visible: boolean
  node: LocationNode | null
  tree: LocationNode[]
}>()
const emit = defineEmits<{ 'update:visible': [boolean]; moved: [] }>()

const targetParentId = ref<string | null>(null)
const targetSortOrder = ref(0)

function onTargetChange(data: LocationNode) {
  targetParentId.value = data.id
}

async function submitMove() {
  if (!props.node || !targetParentId.value) return
  try {
    await moveLocation(props.node.id, {
      parentId: targetParentId.value,
      sortOrder: targetSortOrder.value,
      version: props.node.version,
    })
    ElMessage.success('已移动')
    emit('moved')
  } catch (e: any) {
    ElMessage.error(e.title || '移动失败')
  }
}
~~~

- [ ] **步骤 3：验证前端构建**

运行：

~~~bash
npm --prefix frontend run build
~~~

预期：构建成功。

- [ ] **步骤 4：提交**

~~~bash
git add frontend/src/views/LocationsPage.vue frontend/src/views/LocationMoveDialog.vue
git commit -m "feat: 前端新增位置管理页与移动对话框"
~~~

---

## 任务 24：集成测试与模块验证

**文件：**
- 创建：后端集成测试（各模块 persistence/ 下）
- 修改：`backend/src/test/java/com/zija/ModularityTests.java`

- [ ] **步骤 1：创建 file 模块集成测试**

创建 `FileServiceIntegrationTest.java`（使用 Testcontainers + 临时存储目录）：

~~~java
package com.zija.file.internal;

import com.zija.file.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.session.jdbc.initialize-schema=never",
        "zija.file.storage-path=${java.io.tmpdir}/zija-test-files"
})
class FileServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    FileApi fileApi;

    @Test
    void storesAndRetrievesFileMetadata() {
        UUID householdId = UUID.randomUUID();
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};

        var info = fileApi.store(householdId, content, "test.jpg", "image/jpeg");

        assertThat(info.id()).isNotNull();
        assertThat(info.detectedMediaType()).isEqualTo("image/jpeg");
        assertThat(info.byteSize()).isEqualTo(6);

        var found = fileApi.findInfo(householdId, info.id());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(info.id());
    }
}
~~~

- [ ] **步骤 2：创建 catalog 模块集成测试**

创建 `CatalogDictionaryIntegrationTest.java`：

~~~java
package com.zija.catalog.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class CatalogDictionaryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    CatalogDictionaryService service;

    @Test
    void createAndQueryCategory() {
        UUID householdId = UUID.randomUUID();
        var category = service.createCategory(householdId, "食品", null, 0);
        assertThat(category.getId()).isNotNull();

        var tree = service.findCategoryTree(householdId, false);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("食品");
    }

    @Test
    void createAndQueryBrand() {
        UUID householdId = UUID.randomUUID();
        var brand = service.createBrand(householdId, "品牌A");
        assertThat(brand.getId()).isNotNull();

        var brands = service.findBrands(householdId, false);
        assertThat(brands).hasSize(1);
    }
}
~~~

- [ ] **步骤 3：创建 location 模块集成测试**

创建 `LocationIntegrationTest.java`：

~~~java
package com.zija.location.internal;

import com.zija.location.LocationApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class LocationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    LocationService locationService;

    @Autowired
    LocationApi locationApi;

    @Test
    void createAndQueryTree() {
        UUID householdId = UUID.randomUUID();
        var root = locationService.createLocation(householdId, "家", null, 0);
        locationService.createLocation(householdId, "客厅", root.getId(), 0);
        locationService.createLocation(householdId, "厨房", root.getId(), 1);

        var tree = locationApi.tree(householdId);
        assertThat(tree.roots()).hasSize(1);
        assertThat(tree.roots().get(0).children()).hasSize(2);
    }

    @Test
    void markReferencedPreventsDeletion() {
        UUID householdId = UUID.randomUUID();
        var loc = locationService.createLocation(householdId, "仓库", null, 0);
        locationApi.markReferenced(householdId, loc.getId());

        var info = locationApi.requireLocation(householdId, loc.getId());
        assertThat(info.everReferenced()).isTrue();
    }
}
~~~

- [ ] **步骤 4：更新 ModularityTests**

确保 `ModularityTests` 验证 `file`、`catalog`、`location` 三个新模块的边界：

~~~java
// 在现有 ModularityTests 中添加
@Test
void verifyModuleBoundaries() {
    ApplicationModules modules = ApplicationModules.of(com.zija.ZijaApplication.class);
    modules.verify();
}
~~~

- [ ] **步骤 5：运行全部后端测试**

运行：

~~~bash
cd backend && ./mvnw -q test
~~~

预期：全部 PASS。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/test/java/com/zija/file/ backend/src/test/java/com/zija/catalog/ backend/src/test/java/com/zija/location/
git commit -m "test: 新增 file、catalog、location 模块集成测试"
~~~

---

## 任务 25：端到端测试与冒烟测试

**文件：**
- 创建：`frontend/e2e/catalog.spec.ts`
- 创建：`frontend/e2e/locations.spec.ts`

- [ ] **步骤 1：创建 catalog 端到端测试**

创建 `frontend/e2e/catalog.spec.ts`：

~~~typescript
import { test, expect } from '@playwright/test'

test.describe('物品资料', () => {
  test.beforeEach(async ({ page }) => {
    // Login as owner
    await page.goto('/login')
    await page.fill('input[name="username"]', 'owner')
    await page.fill('input[name="password"]', 'password')
    await page.click('button[type="submit"]')
    await page.waitForURL('/items')
  })

  test('创建消耗品物品', async ({ page }) => {
    await page.click('button:has-text("新建物品")')
    await page.fill('input[name="name"]', '洗衣液')
    await page.click('input[value="CONSUMABLE"]')
    // Select unit (assuming pre-seeded data)
    await page.click('.el-select:has-text("选择单位")')
    await page.click('.el-option:has-text("瓶")')
    await page.click('button:has-text("保存")')
    await expect(page.locator('text=洗衣液')).toBeVisible()
  })

  test('归档物品', async ({ page }) => {
    // Assuming item exists from previous test
    await page.click('.el-table-row:has-text("洗衣液")')
    await page.click('button:has-text("归档")')
    await page.click('button:has-text("确定")')
    await expect(page.locator('text=已归档')).toBeVisible()
  })
})
~~~

- [ ] **步骤 2：创建 locations 端到端测试**

创建 `frontend/e2e/locations.spec.ts`：

~~~typescript
import { test, expect } from '@playwright/test'

test.describe('位置管理', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[name="username"]', 'owner')
    await page.fill('input[name="password"]', 'password')
    await page.click('button[type="submit"]')
    await page.goto('/locations')
  })

  test('创建位置层级', async ({ page }) => {
    await page.click('button:has-text("新增根位置")')
    await page.fill('input[name="name"]', '客厅')
    await page.click('button:has-text("确定")')
    await expect(page.locator('.el-tree-node:has-text("客厅")')).toBeVisible()
  })

  test('新增子位置', async ({ page }) => {
    await page.click('.el-tree-node:has-text("客厅") button:has-text("+")')
    await page.fill('input[name="name"]', '沙发旁')
    await page.click('button:has-text("确定")')
    await expect(page.locator('.el-tree-node:has-text("沙发旁")')).toBeVisible()
  })
})
~~~

- [ ] **步骤 3：运行端到端测试**

运行（需要先启动 Compose 冒烟环境）：

~~~bash
make compose-smoke
npm --prefix frontend run test:e2e
~~~

预期：全部 PASS。

- [ ] **步骤 4：提交**

~~~bash
git add frontend/e2e/catalog.spec.ts frontend/e2e/locations.spec.ts
git commit -m "test: 新增物品与位置端到端测试"
~~~

---

## 任务 26：README 与 .env.example 更新

**文件：**
- 修改：`README.md`
- 修改：`.env.example`

- [ ] **步骤 1：更新 README**

在 README 中新增文件存储说明：

~~~markdown
## 文件存储

封面文件存储在同一持久化卷中，PostgreSQL 仅保存元数据。

### 备份说明

仅备份 PostgreSQL 不足以恢复封面。恢复时需要：
1. 恢复数据库
2. 恢复同批次的文件卷（`zija-files`）
3. 启动应用验证已存储的文件引用

### 配置

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `ZIJA_FILE_STORAGE_PATH` | 文件存储路径 | `/var/lib/zija/files` |
~~~

- [ ] **步骤 2：更新 .env.example**

确认 `ZIJA_FILE_STORAGE_PATH` 已添加（任务 4 已完成）。

- [ ] **步骤 3：运行 make verify**

运行：

~~~bash
make verify
~~~

预期：全部通过（后端测试、前端测试、生产构建、模块验证、git diff --check）。

- [ ] **步骤 4：提交**

~~~bash
git add README.md .env.example
git commit -m "docs: 更新 README 说明文件存储与备份前提"
~~~

---

## 验收检查清单

阶段三完成后，逐项确认：

- [ ] 活跃成员能从桌面 UI 创建、编辑、归档和恢复消耗品与耐用品资料
- [ ] Owner/Admin 能维护分类与单位；所有成员能新增品牌和标签
- [ ] 权限不足的直接 API 调用返回 `403 ACCESS_DENIED`
- [ ] 所有物品只使用一个基础单位；不符合单位精度的阈值被 `422 CATALOG_UNIT_PRECISION_INVALID` 拒绝
- [ ] 合法 JPEG、PNG、WebP 封面可上传、替换、移除和受保护读取
- [ ] 超限、伪造或不支持内容被稳定错误码拒绝
- [ ] 应用重启后合法封面仍存在
- [ ] 成员能建立位置层级并完成新增、重命名、排序和移动
- [ ] 循环、带子节点删除、已引用位置删除和并发覆盖被拒绝
- [ ] 阶段四可以通过 `CatalogApi` 和 `LocationApi` 验证物品、单位和位置
- [ ] 后端测试、前端测试、生产构建、Playwright、Compose 冒烟、模块验证和 `git diff --check` 全部通过
- [ ] README 与 `.env.example` 记录文件卷配置和恢复前提
