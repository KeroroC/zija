# Zija 工程基础基线实施计划

> **面向智能体执行者：** 必须使用子 Skill：通过 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐项实施本计划。各步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 为 zija 建立整洁、可复现的工程基础，包括 Java 25 模块化 Spring Boot 后端、MyBatis-Plus 与 PostgreSQL 持久化、Vue 3 + Element Plus 桌面端外壳，以及经过验证的本地与容器工作流。

**架构：** 在同一仓库中将后端和前端保持为独立构建单元。后端以 Spring Modulith 模块化单体起步，包含一个公开的 system 模块和由真实 PostgreSQL 支撑的状态 API；前端通过 Nginx 调用带版本的 REST API，Docker Compose 则提供供后续阶段扩展的部署拓扑。

**技术栈：** Java 25、Maven 3.9.11 Wrapper、Spring Boot 4.1.0、Spring Modulith 2.0.5、MyBatis-Plus 3.5.16、Flyway、PostgreSQL 17、JUnit 5、AssertJ、Testcontainers、Vue 3.5、TypeScript 5.8、Vite 7、Element Plus 2.10、Vitest 3、Playwright 1.53、Node.js 24、Docker Compose、Nginx 1.28、GitHub Actions

---

## 计划范围

本计划仅实施 delivery-roadmap 的阶段 1。它建立基础设施和一个贯穿各层的系统状态切片，并确定带版本 URL、request ID 和 Problem Details 约定；生成 OpenAPI 契约的工作从阶段 2 开始，届时将存在需要认证的业务 API。本计划不实现家庭账户、会话、目录数据、位置、库存流转、提醒、报表、CSV 交换或业务文件上传。

## 前置条件

- 已安装 JDK 25，且 <code>java -version</code> 显示正在使用该版本。
- 可临时使用一次 Maven 3.9 来生成 Maven Wrapper。
- 已安装 Node.js 24 和 npm。
- Docker Engine 与 Docker Compose v2 正在运行。
- 可使用 <code>curl</code> 执行 HTTP 冒烟检查。
- 除非步骤另有说明，否则所有命令均从仓库根目录执行。

## 目标文件清单

~~~text
.
├── .dockerignore
├── .editorconfig
├── .env.example
├── .gitattributes
├── .gitignore
├── .github/workflows/ci.yml
├── Makefile
├── README.md
├── compose.yaml
├── backend/
│   ├── .mvn/wrapper/maven-wrapper.properties
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/zija/
│       │   │   ├── ZijaApplication.java
│       │   │   ├── ZijaMybatisConfiguration.java
│       │   │   ├── ZijaRequestIdFilter.java
│       │   │   ├── ZijaSecurityConfiguration.java
│       │   │   └── system/
│       │   │       ├── SystemApi.java
│       │   │       ├── package-info.java
│       │   │       └── internal/
│       │   │           ├── SystemController.java
│       │   │           ├── SystemExceptionHandler.java
│       │   │           ├── SystemInfoResponse.java
│       │   │           ├── SystemInfoService.java
│       │   │           ├── SystemStateUnavailableException.java
│       │   │           └── persistence/
│       │   │               ├── SystemInstallationEntity.java
│       │   │               └── SystemInstallationMapper.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── db/migration/V1__create_system_installation.sql
│       │       └── mapper/system/SystemInstallationMapper.xml
│       └── test/java/com/zija/
│           ├── DocumentationTests.java
│           ├── ModularityTests.java
│           ├── ZijaApplicationTest.java
│           └── system/
│               ├── SystemApplicationModuleTest.java
│               ├── internal/SystemControllerTest.java
│               └── internal/persistence/SystemInstallationMapperIntegrationTest.java
├── frontend/
│   ├── e2e/system-status.spec.ts
│   ├── index.html
│   ├── package.json
│   ├── package-lock.json
│   ├── playwright.config.ts
│   ├── tsconfig.app.json
│   ├── tsconfig.json
│   ├── tsconfig.node.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.vue
│       ├── api/http.ts
│       ├── api/system.ts
│       ├── components/AppShell.test.ts
│       ├── components/AppShell.vue
│       ├── env.d.ts
│       ├── main.ts
│       ├── router/index.ts
│       ├── styles/index.css
│       ├── test/setup.ts
│       ├── types/system.ts
│       └── views/
│           ├── SystemStatusView.test.ts
│           └── SystemStatusView.vue
├── deploy/
│   ├── app/Dockerfile
│   ├── nginx/default.conf
│   └── web/Dockerfile
└── scripts/
    ├── compose-smoke.sh
    ├── e2e-smoke.sh
    └── verify-layout.sh
~~~

## 任务 1：建立仓库约定

**文件：**
- 创建：<code>.editorconfig</code>
- 创建：<code>.gitattributes</code>
- 创建：<code>.env.example</code>

- [ ] **步骤 1：添加编辑器设置**

创建 <code>.editorconfig</code>：

~~~ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
indent_style = space
indent_size = 2
trim_trailing_whitespace = true

[*.java]
indent_size = 4

[Makefile]
indent_style = tab

[*.md]
trim_trailing_whitespace = false
~~~

- [ ] **步骤 2：统一 Git 文本文件规则**

创建 <code>.gitattributes</code>：

~~~gitattributes
* text=auto eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
*.png binary
*.jpg binary
*.jpeg binary
*.webp binary
~~~

- [ ] **步骤 3：记录本地环境变量**

创建 <code>.env.example</code>：

~~~dotenv
ZIJA_POSTGRES_DB=zija
ZIJA_POSTGRES_USER=zija
ZIJA_POSTGRES_PASSWORD=change-this-password
ZIJA_DB_URL=jdbc:postgresql://localhost:5432/zija
ZIJA_DB_USERNAME=zija
ZIJA_DB_PASSWORD=change-this-password
ZIJA_VERSION=dev
ZIJA_HTTP_PORT=8088
ZIJA_POSTGRES_PORT=5432
~~~

- [ ] **步骤 4：验证格式**

运行：

~~~bash
git diff --check
~~~

预期结果：无输出，退出状态为 <code>0</code>。

- [ ] **步骤 5：提交仓库约定**

~~~bash
git add .editorconfig .gitattributes .env.example
git commit -m "chore: establish repository conventions"
~~~

## 任务 2：初始化 Java 25 Spring Boot 构建

**文件：**
- 创建：<code>backend/pom.xml</code>
- 创建：<code>backend/src/test/java/com/zija/ZijaApplicationTest.java</code>
- 创建：<code>backend/src/main/java/com/zija/ZijaApplication.java</code>
- 生成：<code>backend/mvnw</code>
- 生成：<code>backend/mvnw.cmd</code>
- 生成：<code>backend/.mvn/wrapper/maven-wrapper.properties</code>

- [ ] **步骤 1：创建 Maven 构建**

创建 <code>backend/pom.xml</code>：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.zija</groupId>
    <artifactId>zija-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>zija-backend</name>
    <description>知家家庭物品管理系统后端</description>

    <properties>
        <java.version>25</java.version>
        <maven.compiler.release>25</maven.compiler.release>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.modulith</groupId>
                <artifactId>spring-modulith-bom</artifactId>
                <version>2.0.5</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-bom</artifactId>
                <version>3.5.16</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>build-info</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
~~~

- [ ] **步骤 2：编写失败的应用测试**

创建 <code>backend/src/test/java/com/zija/ZijaApplicationTest.java</code>：

~~~java
package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class ZijaApplicationTest {

    @Test
    void applicationDeclaresSpringBootEntryPoint() {
        assertThat(ZijaApplication.class)
                .hasAnnotation(SpringBootApplication.class);
    }
}
~~~

- [ ] **步骤 3：运行测试并确认其失败**

运行：

~~~bash
cd backend
mvn -q -Dtest=ZijaApplicationTest test
~~~

预期结果：由于 <code>ZijaApplication</code> 不存在，编译失败。

- [ ] **步骤 4：添加应用入口点**

创建 <code>backend/src/main/java/com/zija/ZijaApplication.java</code>：

~~~java
package com.zija;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ZijaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZijaApplication.class, args);
    }
}
~~~

- [ ] **步骤 5：生成 Maven Wrapper**

在 <code>backend/</code> 中运行：

~~~bash
mvn -N wrapper:wrapper -Dmaven=3.9.11
~~~

预期结果：Maven 创建 <code>mvnw</code>、<code>mvnw.cmd</code> 和 <code>.mvn/wrapper/maven-wrapper.properties</code>，并将其配置为使用 Maven 3.9.11。

- [ ] **步骤 6：通过 Wrapper 运行测试**

~~~bash
./mvnw -q -Dtest=ZijaApplicationTest test
~~~

预期结果：一个测试通过。

- [ ] **步骤 7：提交后端初始化内容**

~~~bash
git add backend
git commit -m "build: bootstrap spring boot backend"
~~~

## 任务 3：锁定 Spring Modulith 边界

**文件：**
- 创建：<code>backend/src/main/java/com/zija/system/package-info.java</code>
- 创建：<code>backend/src/main/java/com/zija/system/SystemApi.java</code>
- 创建：<code>backend/src/test/java/com/zija/ModularityTests.java</code>
- 创建：<code>backend/src/test/java/com/zija/DocumentationTests.java</code>

- [ ] **步骤 1：编写失败的模块化测试**

创建 <code>backend/src/test/java/com/zija/ModularityTests.java</code>：

~~~java
package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTests {

    private final ApplicationModules modules =
            ApplicationModules.of(ZijaApplication.class);

    @Test
    void systemModuleExistsAndDependenciesAreValid() {
        assertThat(modules.getModuleByName("system")).isPresent();
        modules.verify();
    }
}
~~~

- [ ] **步骤 2：运行模块化测试并确认其失败**

~~~bash
cd backend
./mvnw -q -Dtest=ModularityTests test
~~~

预期结果：由于不存在 <code>system</code> 应用模块，测试失败。

- [ ] **步骤 3：定义 system 模块和公开 API**

创建 <code>backend/src/main/java/com/zija/system/package-info.java</code>：

~~~java
@org.springframework.modulith.ApplicationModule(
        displayName = "System"
)
package com.zija.system;
~~~

创建 <code>backend/src/main/java/com/zija/system/SystemApi.java</code>：

~~~java
package com.zija.system;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SystemApi {

    SystemSnapshot current();

    record SystemSnapshot(
            String application,
            String version,
            String status,
            UUID installationId,
            OffsetDateTime databaseTime
    ) {
    }
}
~~~

- [ ] **步骤 4：添加模块文档生成**

创建 <code>backend/src/test/java/com/zija/DocumentationTests.java</code>：

~~~java
package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class DocumentationTests {

    private final ApplicationModules modules =
            ApplicationModules.of(ZijaApplication.class);

    @Test
    void writesModuleCanvases() {
        new Documenter(modules).writeModuleCanvases();
    }
}
~~~

- [ ] **步骤 5：验证模块边界和文档**

~~~bash
./mvnw -q -Dtest=ModularityTests,DocumentationTests test
test -d target/spring-modulith-docs
~~~

预期结果：两个测试均通过，且 <code>target/spring-modulith-docs</code> 存在。

- [ ] **步骤 6：提交模块基线**

~~~bash
git add backend/src/main/java/com/zija/system backend/src/test/java/com/zija/ModularityTests.java backend/src/test/java/com/zija/DocumentationTests.java
git commit -m "test: enforce backend module boundaries"
~~~

## 任务 4：添加 PostgreSQL、Flyway 和 MyBatis-Plus

**文件：**
- 创建：<code>backend/src/test/java/com/zija/system/internal/persistence/SystemInstallationMapperIntegrationTest.java</code>
- 创建：<code>backend/src/main/resources/db/migration/V1__create_system_installation.sql</code>
- 创建：<code>backend/src/main/resources/application.yml</code>
- 创建：<code>backend/src/main/java/com/zija/ZijaMybatisConfiguration.java</code>
- 创建：<code>backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationEntity.java</code>
- 创建：<code>backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationMapper.java</code>
- 创建：<code>backend/src/main/resources/mapper/system/SystemInstallationMapper.xml</code>

- [ ] **步骤 1：编写失败的 PostgreSQL 集成测试**

创建 <code>backend/src/test/java/com/zija/system/internal/persistence/SystemInstallationMapperIntegrationTest.java</code>：

~~~java
package com.zija.system.internal.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SystemInstallationMapperIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SystemInstallationMapper mapper;

    @Test
    void loadsSingletonInstallationAndDatabaseTime() {
        var installation = mapper.selectById((short) 1);

        assertThat(installation).isNotNull();
        assertThat(installation.getInstallationId()).isNotNull();
        assertThat(installation.getCreatedAt()).isNotNull();
        assertThat(mapper.selectDatabaseTime()).isNotNull();
    }
}
~~~

- [ ] **步骤 2：运行集成测试并确认其失败**

~~~bash
cd backend
./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期结果：由于 Mapper 和实体不存在，编译失败。

- [ ] **步骤 3：创建 Flyway 基线**

创建 <code>backend/src/main/resources/db/migration/V1__create_system_installation.sql</code>：

~~~sql
CREATE TABLE system_installation (
    singleton_key SMALLINT PRIMARY KEY,
    installation_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_installation_singleton CHECK (singleton_key = 1)
);

INSERT INTO system_installation (singleton_key, installation_id)
VALUES (1, gen_random_uuid());
~~~

- [ ] **步骤 4：配置 Spring、Flyway 和 MyBatis-Plus**

创建 <code>backend/src/main/resources/application.yml</code>：

~~~yaml
spring:
  application:
    name: zija
  datasource:
    url: ${ZIJA_DB_URL:jdbc:postgresql://localhost:5432/zija}
    username: ${ZIJA_DB_USERNAME:zija}
    password: ${ZIJA_DB_PASSWORD:zija}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 1
  flyway:
    enabled: true
    locations: classpath:db/migration
  mvc:
    problemdetails:
      enabled: true
  lifecycle:
    timeout-per-shutdown-phase: 20s

server:
  shutdown: graceful

info:
  app:
    version: ${ZIJA_VERSION:dev}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    default-statement-timeout: 10
  global-config:
    banner: false
    db-config:
      id-type: assign_uuid
~~~

创建 <code>backend/src/main/java/com/zija/ZijaMybatisConfiguration.java</code>：

~~~java
package com.zija;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ZijaMybatisConfiguration {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
~~~

- [ ] **步骤 5：添加安装实体和 Mapper**

创建 <code>backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationEntity.java</code>：

~~~java
package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("system_installation")
public class SystemInstallationEntity {

    @TableId(value = "singleton_key", type = IdType.INPUT)
    private Short singletonKey;

    private UUID installationId;

    private OffsetDateTime createdAt;

    public Short getSingletonKey() {
        return singletonKey;
    }

    public void setSingletonKey(Short singletonKey) {
        this.singletonKey = singletonKey;
    }

    public UUID getInstallationId() {
        return installationId;
    }

    public void setInstallationId(UUID installationId) {
        this.installationId = installationId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
~~~

创建 <code>backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationMapper.java</code>：

~~~java
package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;

@Mapper
public interface SystemInstallationMapper
        extends BaseMapper<SystemInstallationEntity> {

    OffsetDateTime selectDatabaseTime();
}
~~~

创建 <code>backend/src/main/resources/mapper/system/SystemInstallationMapper.xml</code>：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.system.internal.persistence.SystemInstallationMapper">
    <select id="selectDatabaseTime" resultType="java.time.OffsetDateTime">
        SELECT CURRENT_TIMESTAMP
    </select>
</mapper>
~~~

- [ ] **步骤 6：运行真实 PostgreSQL 集成测试**

~~~bash
./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期结果：Testcontainers 启动 PostgreSQL 17，Flyway 应用版本 1，测试通过。

- [ ] **步骤 7：提交持久化基线**

~~~bash
git add backend/src/main backend/src/test/java/com/zija/system/internal/persistence
git commit -m "feat: add postgresql mybatis foundation"
~~~

## 任务 5：提供受保护的系统信息 API

**文件：**
- 创建：<code>backend/src/test/java/com/zija/system/internal/SystemControllerTest.java</code>
- 创建：<code>backend/src/test/java/com/zija/system/SystemApplicationModuleTest.java</code>
- 创建：<code>backend/src/main/java/com/zija/ZijaRequestIdFilter.java</code>
- 创建：<code>backend/src/main/java/com/zija/ZijaSecurityConfiguration.java</code>
- 创建：<code>backend/src/main/java/com/zija/system/internal/SystemInfoService.java</code>
- 创建：<code>backend/src/main/java/com/zija/system/internal/SystemInfoResponse.java</code>
- 创建：<code>backend/src/main/java/com/zija/system/internal/SystemController.java</code>
- 创建：<code>backend/src/main/java/com/zija/system/internal/SystemStateUnavailableException.java</code>
- 创建：<code>backend/src/main/java/com/zija/system/internal/SystemExceptionHandler.java</code>

- [ ] **步骤 1：编写失败的 MVC 切片测试**

创建 <code>backend/src/test/java/com/zija/system/internal/SystemControllerTest.java</code>：

~~~java
package com.zija.system.internal;

import com.zija.ZijaRequestIdFilter;
import com.zija.ZijaSecurityConfiguration;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import({
        ZijaSecurityConfiguration.class,
        ZijaRequestIdFilter.class,
        SystemExceptionHandler.class
})
class SystemControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SystemApi systemApi;

    @Test
    void returnsPublicSystemInformation() throws Exception {
        var installationId =
                UUID.fromString("34bf30dd-d082-4e26-9dfe-8f30421f4772");
        var databaseTime =
                OffsetDateTime.parse("2026-07-19T12:00:00Z");
        given(systemApi.current()).willReturn(new SystemApi.SystemSnapshot(
                "zija",
                "dev",
                "UP",
                installationId,
                databaseTime
        ));

        mvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.application").value("zija"))
                .andExpect(jsonPath("$.version").value("dev"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.installationId")
                        .value(installationId.toString()))
                .andExpect(jsonPath("$.databaseTime")
                        .value("2026-07-19T12:00:00Z"));
    }

    @Test
    void returnsProblemDetailsWithStableCodeAndRequestId() throws Exception {
        given(systemApi.current())
                .willThrow(new SystemStateUnavailableException(
                        "installation missing"
                ));

        mvc.perform(get("/api/v1/system/info")
                        .header("X-Request-Id", "request-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("System state unavailable"))
                .andExpect(jsonPath("$.errorCode")
                        .value("system_state_unavailable"))
                .andExpect(jsonPath("$.requestId").value("request-123"));
    }

    @Test
    void replacesUnsafeRequestIdBeforeWritingResponseHeaders() throws Exception {
        given(systemApi.current()).willReturn(new SystemApi.SystemSnapshot(
                "zija",
                "dev",
                "UP",
                UUID.fromString("34bf30dd-d082-4e26-9dfe-8f30421f4772"),
                OffsetDateTime.parse("2026-07-19T12:00:00Z")
        ));

        mvc.perform(get("/api/v1/system/info")
                        .header("X-Request-Id", "unsafe request id"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "X-Request-Id",
                        matchesPattern(
                                "[0-9a-f]{8}-[0-9a-f]{4}-"
                                        + "[0-9a-f]{4}-[0-9a-f]{4}-"
                                        + "[0-9a-f]{12}"
                        )
                ));
    }
}
~~~

- [ ] **步骤 2：运行 MVC 测试并确认其失败**

~~~bash
cd backend
./mvnw -q -Dtest=SystemControllerTest test
~~~

预期结果：由于 controller 和 Web 支持类不存在，编译失败。

- [ ] **步骤 3：添加请求关联与基础安全配置**

创建 <code>backend/src/main/java/com/zija/ZijaRequestIdFilter.java</code>：

~~~java
package com.zija;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ZijaRequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "zija.request-id";
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var supplied = request.getHeader(HEADER);
        var requestId = supplied != null
                && SAFE_REQUEST_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();

        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
~~~

创建 <code>backend/src/main/java/com/zija/ZijaSecurityConfiguration.java</code>：

~~~java
package com.zija;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class ZijaSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/system/info",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().denyAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }
}
~~~

- [ ] **步骤 4：实现系统服务和响应**

创建 <code>backend/src/main/java/com/zija/system/internal/SystemInfoService.java</code>：

~~~java
package com.zija.system.internal;

import com.zija.system.SystemApi;
import com.zija.system.internal.persistence.SystemInstallationMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SystemInfoService implements SystemApi {

    private final SystemInstallationMapper installationMapper;
    private final Environment environment;

    SystemInfoService(
            SystemInstallationMapper installationMapper,
            Environment environment
    ) {
        this.installationMapper = installationMapper;
        this.environment = environment;
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSnapshot current() {
        var installation = installationMapper.selectById((short) 1);
        if (installation == null) {
            throw new SystemStateUnavailableException(
                    "installation missing"
            );
        }

        return new SystemSnapshot(
                environment.getProperty("spring.application.name", "zija"),
                environment.getProperty("info.app.version", "dev"),
                "UP",
                installation.getInstallationId(),
                installationMapper.selectDatabaseTime()
        );
    }
}
~~~

创建 <code>backend/src/main/java/com/zija/system/internal/SystemInfoResponse.java</code>：

~~~java
package com.zija.system.internal;

import com.zija.system.SystemApi;

import java.time.OffsetDateTime;
import java.util.UUID;

record SystemInfoResponse(
        String application,
        String version,
        String status,
        UUID installationId,
        OffsetDateTime databaseTime
) {
    static SystemInfoResponse from(SystemApi.SystemSnapshot snapshot) {
        return new SystemInfoResponse(
                snapshot.application(),
                snapshot.version(),
                snapshot.status(),
                snapshot.installationId(),
                snapshot.databaseTime()
        );
    }
}
~~~

创建 <code>backend/src/main/java/com/zija/system/internal/SystemController.java</code>：

~~~java
package com.zija.system.internal;

import com.zija.system.SystemApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    private final SystemApi systemApi;

    SystemController(SystemApi systemApi) {
        this.systemApi = systemApi;
    }

    @GetMapping("/info")
    SystemInfoResponse info() {
        return SystemInfoResponse.from(systemApi.current());
    }
}
~~~

- [ ] **步骤 5：实现 Problem Details 处理器**

创建 <code>backend/src/main/java/com/zija/system/internal/SystemStateUnavailableException.java</code>：

~~~java
package com.zija.system.internal;

class SystemStateUnavailableException extends RuntimeException {

    SystemStateUnavailableException(String message) {
        super(message);
    }
}
~~~

创建 <code>backend/src/main/java/com/zija/system/internal/SystemExceptionHandler.java</code>：

~~~java
package com.zija.system.internal;

import com.zija.ZijaRequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class SystemExceptionHandler {

    @ExceptionHandler(SystemStateUnavailableException.class)
    ProblemDetail handleUnavailableState(
            SystemStateUnavailableException exception,
            HttpServletRequest request
    ) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The system installation state could not be loaded."
        );
        problem.setTitle("System state unavailable");
        problem.setProperty(
                "errorCode",
                "system_state_unavailable"
        );
        problem.setProperty(
                "requestId",
                request.getAttribute(ZijaRequestIdFilter.ATTRIBUTE)
        );
        return problem;
    }
}
~~~

- [ ] **步骤 6：运行 MVC 切片测试**

~~~bash
./mvnw -q -Dtest=SystemControllerTest test
~~~

预期结果：三个 MVC 切片测试全部通过。

- [ ] **步骤 7：添加真实的 system 模块集成测试**

创建 <code>backend/src/test/java/com/zija/system/SystemApplicationModuleTest.java</code>：

~~~java
package com.zija.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ApplicationModuleTest
class SystemApplicationModuleTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SystemApi systemApi;

    @Test
    void exposesPostgresqlBackedSystemSnapshot() {
        var snapshot = systemApi.current();

        assertThat(snapshot.application()).isEqualTo("zija");
        assertThat(snapshot.status()).isEqualTo("UP");
        assertThat(snapshot.installationId()).isNotNull();
        assertThat(snapshot.databaseTime()).isNotNull();
    }
}
~~~

- [ ] **步骤 8：运行全部后端测试**

~~~bash
./mvnw -q test
~~~

预期结果：单元测试、MVC 测试、模块化测试、文档测试、模块集成测试和 PostgreSQL Mapper 测试全部通过。

- [ ] **步骤 9：提交 system API 切片**

~~~bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat: expose postgresql backed system status"
~~~

## 任务 6：建立 Vue 3 和 Element Plus 工具链

**文件：**
- 修改：<code>.gitignore</code>
- 创建：<code>frontend/package.json</code>
- 生成：<code>frontend/package-lock.json</code>
- 创建：<code>frontend/index.html</code>
- 创建：<code>frontend/tsconfig.json</code>
- 创建：<code>frontend/tsconfig.app.json</code>
- 创建：<code>frontend/tsconfig.node.json</code>
- 创建：<code>frontend/vite.config.ts</code>
- 创建：<code>frontend/src/env.d.ts</code>
- 创建：<code>frontend/src/test/setup.ts</code>

- [ ] **步骤 1：定义前端依赖和命令**

创建 <code>frontend/package.json</code>：

~~~json
{
  "name": "zija-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "engines": {
    "node": ">=24.0.0"
  },
  "scripts": {
    "dev": "vite --host 0.0.0.0",
    "build": "npm run typecheck && vite build",
    "typecheck": "vue-tsc -b",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:e2e": "playwright test"
  },
  "dependencies": {
    "element-plus": "^2.10.7",
    "pinia": "^3.0.3",
    "vue": "^3.5.17",
    "vue-router": "^4.5.1"
  },
  "devDependencies": {
    "@playwright/test": "^1.53.2",
    "@types/node": "^24.0.0",
    "@vitejs/plugin-vue": "^6.0.0",
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^26.1.0",
    "typescript": "^5.8.3",
    "vite": "^7.0.4",
    "vitest": "^3.2.4",
    "vue-tsc": "^2.2.12"
  }
}
~~~

- [ ] **步骤 2：添加 TypeScript 项目配置**

创建 <code>frontend/tsconfig.json</code>：

~~~json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
~~~

创建 <code>frontend/tsconfig.app.json</code>：

~~~json
{
  "compilerOptions": {
    "composite": true,
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "ES2022",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "Bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "strict": true,
    "jsx": "preserve",
    "types": ["element-plus/global"]
  },
  "include": [
    "src/**/*.ts",
    "src/**/*.tsx",
    "src/**/*.vue"
  ]
}
~~~

创建 <code>frontend/tsconfig.node.json</code>：

~~~json
{
  "compilerOptions": {
    "composite": true,
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
    "target": "ES2023",
    "lib": ["ES2023"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "Bundler",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "moduleDetection": "force",
    "noEmit": true,
    "strict": true,
    "types": ["node"]
  },
  "include": [
    "vite.config.ts",
    "playwright.config.ts"
  ]
}
~~~

- [ ] **步骤 3：添加 Vite 和 Vitest 配置**

创建 <code>frontend/vite.config.ts</code>：

~~~typescript
import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    clearMocks: true
  }
});
~~~

创建 <code>frontend/src/test/setup.ts</code>：

~~~typescript
import { afterEach } from "vitest";

afterEach(() => {
  document.body.innerHTML = "";
});
~~~

创建 <code>frontend/src/env.d.ts</code>：

~~~typescript
/// <reference types="vite/client" />
~~~

- [ ] **步骤 4：添加 HTML 入口文档**

创建 <code>frontend/index.html</code>：

~~~html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="description" content="知家家庭物品管理系统" />
    <title>知家 · zija</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
~~~

- [ ] **步骤 5：忽略生成的前端测试产物**

将以下内容追加到 <code>.gitignore</code>：

~~~gitignore
frontend/test-results/
frontend/playwright-report/
frontend/.vite/
~~~

- [ ] **步骤 6：安装并锁定前端依赖**

运行：

~~~bash
cd frontend
npm install
~~~

预期结果：已创建 <code>package-lock.json</code>，且 npm 未报告无法解析的依赖。

- [ ] **步骤 7：验证空的 TypeScript 项目**

~~~bash
npm run typecheck
~~~

预期结果：退出状态为 <code>0</code>。

- [ ] **步骤 8：提交前端工具链**

~~~bash
git add .gitignore frontend
git commit -m "build: establish vue frontend toolchain"
~~~

## 任务 7：构建桌面端应用外壳

**文件：**
- 创建：<code>frontend/src/components/AppShell.test.ts</code>
- 创建：<code>frontend/src/components/AppShell.vue</code>
- 创建：<code>frontend/src/views/SystemStatusView.vue</code>
- 创建：<code>frontend/src/router/index.ts</code>
- 创建：<code>frontend/src/App.vue</code>
- 创建：<code>frontend/src/main.ts</code>
- 创建：<code>frontend/src/styles/index.css</code>

- [ ] **步骤 1：编写失败的应用外壳测试**

创建 <code>frontend/src/components/AppShell.test.ts</code>：

~~~typescript
import ElementPlus from "element-plus";
import { mount } from "@vue/test-utils";
import {
  createMemoryHistory,
  createRouter
} from "vue-router";
import { h } from "vue";
import { describe, expect, it } from "vitest";
import AppShell from "./AppShell.vue";

describe("AppShell", () => {
  it("renders the approved desktop navigation", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: "/",
          component: { render: () => h("div", "系统状态") }
        }
      ]
    });
    await router.push("/");
    await router.isReady();

    const wrapper = mount(AppShell, {
      global: {
        plugins: [router, ElementPlus]
      }
    });

    expect(wrapper.text()).toContain("知家");
    expect(wrapper.text()).toContain("首页");
    expect(wrapper.text()).toContain("物品资料");
    expect(wrapper.text()).toContain("库存管理");
    expect(wrapper.text()).toContain("位置管理");
    expect(wrapper.text()).toContain("提醒中心");
    expect(wrapper.text()).toContain("报表与导出");
    expect(wrapper.text()).toContain("家庭设置");
    expect(wrapper.text()).toContain("管理员");
    wrapper.unmount();
  });
});
~~~

- [ ] **步骤 2：运行测试并确认其失败**

~~~bash
cd frontend
npm test -- AppShell.test.ts
~~~

预期结果：由于 <code>AppShell.vue</code> 不存在，测试失败。

- [ ] **步骤 3：创建 Element Plus 应用外壳**

创建 <code>frontend/src/components/AppShell.vue</code>：

~~~vue
<template>
  <el-container class="app-shell">
    <el-aside class="app-sidebar" width="224px">
      <div class="brand">
        <span class="brand-cn">知家</span>
        <span class="brand-en">ZIJA</span>
      </div>
      <el-menu
        router
        class="app-menu"
        :default-active="$route.path"
      >
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/items" disabled>物品资料</el-menu-item>
        <el-menu-item index="/inventory" disabled>库存管理</el-menu-item>
        <el-menu-item index="/locations" disabled>位置管理</el-menu-item>
        <el-menu-item index="/reminders" disabled>提醒中心</el-menu-item>
        <el-menu-item index="/reports" disabled>报表与导出</el-menu-item>
        <el-menu-item index="/settings" disabled>家庭设置</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <span class="header-context">家庭：我的家</span>
        <el-tag effect="plain" type="success">管理员</el-tag>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
~~~

创建 <code>frontend/src/views/SystemStatusView.vue</code>：

~~~vue
<template>
  <section>
    <h1>系统状态</h1>
    <el-card>
      <el-skeleton :rows="3" animated />
      <p class="status-hint">正在连接系统…</p>
    </el-card>
  </section>
</template>
~~~

- [ ] **步骤 4：连接路由和应用入口**

创建 <code>frontend/src/router/index.ts</code>：

~~~typescript
import { createRouter, createWebHistory } from "vue-router";
import SystemStatusView from "../views/SystemStatusView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "system-status",
      component: SystemStatusView
    }
  ]
});
~~~

创建 <code>frontend/src/App.vue</code>：

~~~vue
<script setup lang="ts">
import AppShell from "./components/AppShell.vue";
</script>

<template>
  <AppShell />
</template>
~~~

创建 <code>frontend/src/main.ts</code>：

~~~typescript
import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import { router } from "./router";
import "./styles/index.css";

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElementPlus)
  .mount("#app");
~~~

- [ ] **步骤 5：添加桌面端样式**

创建 <code>frontend/src/styles/index.css</code>：

~~~css
:root {
  color: #20312c;
  background: #f4f7f6;
  font-family:
    Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  min-width: 1024px;
  min-height: 100%;
  margin: 0;
}

body {
  min-height: 100vh;
}

.app-shell {
  min-height: 100vh;
}

.app-sidebar {
  color: #edf7f3;
  background: #264f46;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
  height: 64px;
  padding: 18px 20px;
}

.brand-cn {
  font-size: 22px;
  font-weight: 700;
}

.brand-en {
  color: #b9d8cd;
  font-size: 12px;
  letter-spacing: 0.12em;
}

.app-menu {
  border-right: 0;
  background: transparent;
}

.app-menu .el-menu-item {
  color: #edf7f3;
}

.app-menu .el-menu-item.is-active {
  color: #ffffff;
  background: #397262;
}

.app-menu .el-menu-item.is-disabled {
  color: #9bb8ae;
  opacity: 1;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e1e8e5;
  background: #ffffff;
}

.header-context {
  color: #65756f;
}

.app-main {
  padding: 24px;
  background: #f4f7f6;
}

.app-main h1 {
  margin-top: 0;
}

.status-hint {
  color: #6d7e77;
}
~~~

- [ ] **步骤 6：运行组件测试和生产构建**

~~~bash
npm test -- AppShell.test.ts
npm run build
~~~

预期结果：组件测试通过，且 Vite 生成 <code>frontend/dist</code>。

- [ ] **步骤 7：提交桌面端外壳**

~~~bash
git add frontend/src
git commit -m "feat: add desktop administration shell"
~~~

## 任务 8：将 Vue 外壳连接到 system API

**文件：**
- 创建：<code>frontend/src/types/system.ts</code>
- 创建：<code>frontend/src/api/http.ts</code>
- 创建：<code>frontend/src/api/system.ts</code>
- 创建：<code>frontend/src/views/SystemStatusView.test.ts</code>
- 修改：<code>frontend/src/views/SystemStatusView.vue</code>

- [ ] **步骤 1：编写失败的系统状态视图测试**

创建 <code>frontend/src/views/SystemStatusView.test.ts</code>：

~~~typescript
import ElementPlus from "element-plus";
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchSystemInfo } from "../api/system";
import SystemStatusView from "./SystemStatusView.vue";

vi.mock("../api/system", () => ({
  fetchSystemInfo: vi.fn()
}));

const fetchSystemInfoMock = vi.mocked(fetchSystemInfo);

describe("SystemStatusView", () => {
  beforeEach(() => {
    fetchSystemInfoMock.mockReset();
  });

  it("shows live backend and database status", async () => {
    fetchSystemInfoMock.mockResolvedValue({
      application: "zija",
      version: "0.1.0",
      status: "UP",
      installationId: "34bf30dd-d082-4e26-9dfe-8f30421f4772",
      databaseTime: "2026-07-19T12:00:00Z"
    });

    const wrapper = mount(SystemStatusView, {
      global: {
        plugins: [ElementPlus]
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain("系统运行正常");
    expect(wrapper.text()).toContain("0.1.0");
    expect(wrapper.text()).toContain("PostgreSQL 已连接");
    wrapper.unmount();
  });

  it("shows a recoverable error when the API is unavailable", async () => {
    fetchSystemInfoMock.mockRejectedValue(
      new Error("System state unavailable")
    );

    const wrapper = mount(SystemStatusView, {
      global: {
        plugins: [ElementPlus]
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain("暂时无法读取系统状态");
    wrapper.unmount();
  });
});
~~~

- [ ] **步骤 2：运行视图测试并确认其失败**

~~~bash
cd frontend
npm test -- SystemStatusView.test.ts
~~~

预期结果：由于 API 模块和完整视图不存在，测试失败。

- [ ] **步骤 3：定义 API 类型和 HTTP 错误**

创建 <code>frontend/src/types/system.ts</code>：

~~~typescript
export interface SystemInfo {
  application: string;
  version: string;
  status: "UP";
  installationId: string;
  databaseTime: string;
}

export interface ApiProblem {
  title?: string;
  detail?: string;
  errorCode?: string;
  requestId?: string;
}
~~~

创建 <code>frontend/src/api/http.ts</code>：

~~~typescript
import type { ApiProblem } from "../types/system";

export class ApiError extends Error {
  readonly errorCode: string;
  readonly requestId?: string;

  constructor(
    message: string,
    errorCode: string,
    requestId?: string
  ) {
    super(message);
    this.name = "ApiError";
    this.errorCode = errorCode;
    this.requestId = requestId;
  }
}

export async function getJson<T>(path: string): Promise<T> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "";
  const response = await fetch(baseUrl + path, {
    credentials: "same-origin",
    headers: {
      Accept: "application/json"
    }
  });

  if (response.ok) {
    return response.json() as Promise<T>;
  }

  let problem: ApiProblem = {};
  try {
    problem = (await response.json()) as ApiProblem;
  } catch {
    problem = {};
  }

  throw new ApiError(
    problem.title ?? "Request failed",
    problem.errorCode ?? "http_error",
    problem.requestId ?? response.headers.get("X-Request-Id") ?? undefined
  );
}
~~~

创建 <code>frontend/src/api/system.ts</code>：

~~~typescript
import type { SystemInfo } from "../types/system";
import { getJson } from "./http";

export function fetchSystemInfo(): Promise<SystemInfo> {
  return getJson<SystemInfo>("/api/v1/system/info");
}
~~~

- [ ] **步骤 4：实现实时系统状态视图**

将 <code>frontend/src/views/SystemStatusView.vue</code> 替换为：

~~~vue
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { fetchSystemInfo } from "../api/system";
import type { SystemInfo } from "../types/system";

const loading = ref(true);
const error = ref("");
const info = ref<SystemInfo>();

const databaseTimeLabel = computed(() => {
  if (!info.value) {
    return "";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "medium"
  }).format(new Date(info.value.databaseTime));
});

onMounted(async () => {
  try {
    info.value = await fetchSystemInfo();
  } catch {
    error.value = "暂时无法读取系统状态，请检查后端与数据库。";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section>
    <h1>系统状态</h1>

    <el-card v-if="loading">
      <el-skeleton :rows="4" animated />
    </el-card>

    <el-alert
      v-else-if="error"
      title="暂时无法读取系统状态"
      :description="error"
      type="error"
      show-icon
      :closable="false"
    />

    <el-card v-else-if="info">
      <el-result
        icon="success"
        title="系统运行正常"
        sub-title="PostgreSQL 已连接"
      />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="应用">
          {{ info.application }}
        </el-descriptions-item>
        <el-descriptions-item label="版本">
          {{ info.version }}
        </el-descriptions-item>
        <el-descriptions-item label="数据库时间">
          {{ databaseTimeLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="安装标识">
          {{ info.installationId }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </section>
</template>
~~~

- [ ] **步骤 5：运行前端测试、类型检查和构建**

~~~bash
npm test
npm run typecheck
npm run build
~~~

预期结果：全部组件测试通过，生产构建成功。

- [ ] **步骤 6：提交 API 集成**

~~~bash
git add frontend/src
git commit -m "feat: display live system status"
~~~

## 任务 9：添加 Docker Compose 和浏览器冒烟测试

**文件：**
- 创建：<code>.dockerignore</code>
- 创建：<code>deploy/app/Dockerfile</code>
- 创建：<code>deploy/web/Dockerfile</code>
- 创建：<code>deploy/nginx/default.conf</code>
- 创建：<code>compose.yaml</code>
- 创建：<code>scripts/compose-smoke.sh</code>
- 创建：<code>scripts/e2e-smoke.sh</code>
- 创建：<code>frontend/playwright.config.ts</code>
- 创建：<code>frontend/e2e/system-status.spec.ts</code>

- [ ] **步骤 1：编写失败的 Compose 冒烟脚本**

创建 <code>scripts/compose-smoke.sh</code>：

~~~bash
#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

export ZIJA_HTTP_PORT=18088
export ZIJA_POSTGRES_PORT=15432

cleanup() {
  docker compose -p zija-smoke --env-file .env.example down -v
}
trap cleanup EXIT

docker compose -p zija-smoke --env-file .env.example up --build -d

for attempt in $(seq 1 30); do
  if response="$(curl -fsS "http://127.0.0.1:18088/api/v1/system/info")"; then
    printf '%s' "$response" | grep -q '"status":"UP"'
    printf '%s' "$response" | grep -q '"application":"zija"'
    echo "compose smoke passed"
    exit 0
  fi
  sleep 2
done

docker compose -p zija-smoke --env-file .env.example ps
docker compose -p zija-smoke --env-file .env.example logs
echo "compose smoke failed" >&2
exit 1
~~~

运行：

~~~bash
chmod +x scripts/compose-smoke.sh
./scripts/compose-smoke.sh
~~~

预期结果：由于 <code>compose.yaml</code> 不存在，执行失败。

- [ ] **步骤 2：控制 Docker 构建上下文**

创建 <code>.dockerignore</code>：

~~~dockerignore
.git
.github
.superpowers
.env
backups
data
backend/target
frontend/node_modules
frontend/dist
frontend/coverage
frontend/test-results
frontend/playwright-report
~~~

- [ ] **步骤 3：添加后端容器镜像**

创建 <code>deploy/app/Dockerfile</code>：

~~~dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
RUN chmod +x backend/mvnw
RUN cd backend && ./mvnw -q -DskipTests dependency:go-offline

COPY backend/src backend/src
RUN cd backend && ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && groupadd --system zija && useradd --system --gid zija --home-dir /app zija

WORKDIR /app
COPY --from=build --chown=zija:zija /workspace/backend/target/zija-backend-0.1.0-SNAPSHOT.jar /app/zija.jar

USER zija
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/zija.jar"]
~~~

- [ ] **步骤 4：添加前端与 Nginx 镜像**

创建 <code>deploy/web/Dockerfile</code>：

~~~dockerfile
FROM node:24-alpine AS build
WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM nginx:1.28-alpine
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
~~~

创建 <code>deploy/nginx/default.conf</code>：

~~~nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    location = /healthz {
        access_log off;
        add_header Content-Type text/plain;
        return 200 "ok";
    }

    location /api/ {
        proxy_pass http://app:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Request-Id $request_id;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
~~~

- [ ] **步骤 5：定义 Compose 拓扑**

创建 <code>compose.yaml</code>：

~~~yaml
name: zija

services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: ${ZIJA_POSTGRES_DB:-zija}
      POSTGRES_USER: ${ZIJA_POSTGRES_USER:-zija}
      POSTGRES_PASSWORD: ${ZIJA_POSTGRES_PASSWORD:?set ZIJA_POSTGRES_PASSWORD}
    ports:
      - "${ZIJA_POSTGRES_PORT:-5432}:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test:
        - CMD-SHELL
        - pg_isready -U ${ZIJA_POSTGRES_USER:-zija} -d ${ZIJA_POSTGRES_DB:-zija}
      interval: 5s
      timeout: 3s
      retries: 20

  app:
    build:
      context: .
      dockerfile: deploy/app/Dockerfile
    environment:
      ZIJA_DB_URL: jdbc:postgresql://postgres:5432/${ZIJA_POSTGRES_DB:-zija}
      ZIJA_DB_USERNAME: ${ZIJA_POSTGRES_USER:-zija}
      ZIJA_DB_PASSWORD: ${ZIJA_POSTGRES_PASSWORD:?set ZIJA_POSTGRES_PASSWORD}
      ZIJA_VERSION: ${ZIJA_VERSION:-dev}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test:
        - CMD
        - curl
        - -fsS
        - http://localhost:8080/actuator/health/readiness
      interval: 5s
      timeout: 3s
      retries: 20

  web:
    build:
      context: .
      dockerfile: deploy/web/Dockerfile
    ports:
      - "${ZIJA_HTTP_PORT:-8088}:80"
    depends_on:
      app:
        condition: service_healthy
    healthcheck:
      test:
        - CMD-SHELL
        - wget -q -O - http://localhost/healthz | grep -q ok
      interval: 5s
      timeout: 3s
      retries: 20

volumes:
  postgres-data:
~~~

- [ ] **步骤 6：运行 Compose 冒烟测试**

~~~bash
./scripts/compose-smoke.sh
~~~

预期结果：脚本输出 <code>compose smoke passed</code>，并删除测试容器和 volume。

- [ ] **步骤 7：添加 Playwright 浏览器契约**

创建 <code>frontend/playwright.config.ts</code>：

~~~typescript
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: process.env.ZIJA_WEB_URL ?? "http://127.0.0.1:8088",
    trace: "retain-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ]
});
~~~

创建 <code>frontend/e2e/system-status.spec.ts</code>：

~~~typescript
import { expect, test } from "@playwright/test";

test("shows the live backend and PostgreSQL status", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "系统状态" }))
    .toBeVisible();
  await expect(page.getByText("系统运行正常")).toBeVisible();
  await expect(page.getByText("PostgreSQL 已连接")).toBeVisible();
  await expect(page.getByText("管理员")).toBeVisible();
});
~~~

创建 <code>scripts/e2e-smoke.sh</code>：

~~~bash
#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

export ZIJA_HTTP_PORT=18089
export ZIJA_POSTGRES_PORT=15433

cleanup() {
  docker compose -p zija-e2e --env-file .env.example down -v
}
trap cleanup EXIT

docker compose -p zija-e2e --env-file .env.example up --build -d

for attempt in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:18089/api/v1/system/info" >/dev/null; then
    ZIJA_WEB_URL=http://127.0.0.1:18089 npm --prefix frontend run test:e2e
    echo "e2e smoke passed"
    exit 0
  fi
  sleep 2
done

docker compose -p zija-e2e --env-file .env.example ps
docker compose -p zija-e2e --env-file .env.example logs
echo "e2e smoke failed" >&2
exit 1
~~~

- [ ] **步骤 8：运行浏览器冒烟测试**

~~~bash
chmod +x scripts/e2e-smoke.sh
npm --prefix frontend exec -- playwright install chromium
./scripts/e2e-smoke.sh
~~~

预期结果：Playwright 的 Chromium 场景通过，脚本输出 <code>e2e smoke passed</code>。

- [ ] **步骤 9：提交容器部署内容**

~~~bash
git add .dockerignore compose.yaml deploy scripts frontend/playwright.config.ts frontend/e2e
git commit -m "build: add compose deployment smoke tests"
~~~

## 任务 10：添加稳定的开发命令和文档

**文件：**
- 创建：<code>scripts/verify-layout.sh</code>
- 创建：<code>Makefile</code>
- 创建：<code>README.md</code>

- [ ] **步骤 1：添加仓库布局验证脚本**

创建 <code>scripts/verify-layout.sh</code>：

~~~bash
#!/usr/bin/env bash
set -euo pipefail

required_files=(
  ".dockerignore"
  ".editorconfig"
  ".env.example"
  "Makefile"
  "README.md"
  "backend/pom.xml"
  "backend/mvnw"
  "backend/src/main/java/com/zija/ZijaApplication.java"
  "backend/src/main/resources/application.yml"
  "backend/src/main/resources/db/migration/V1__create_system_installation.sql"
  "frontend/package.json"
  "frontend/src/main.ts"
  "frontend/src/components/AppShell.vue"
  "frontend/src/views/SystemStatusView.vue"
  "compose.yaml"
  "deploy/app/Dockerfile"
  "deploy/web/Dockerfile"
  "deploy/nginx/default.conf"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "missing required file: $file" >&2
    exit 1
  fi
done

echo "repository layout verified"
~~~

- [ ] **步骤 2：在添加 Make 命令前运行布局检查**

~~~bash
chmod +x scripts/verify-layout.sh
./scripts/verify-layout.sh
~~~

预期结果：检查失败，并将 <code>Makefile</code> 报告为第一个缺失文件。

- [ ] **步骤 3：添加稳定的根目录命令**

创建 <code>Makefile</code>：

~~~makefile
ENV_FILE ?= .env

.PHONY: verify-layout backend-test backend-build frontend-test frontend-build verify dev-db dev-backend dev-frontend compose-smoke e2e-smoke clean

verify-layout:
	./scripts/verify-layout.sh

backend-test:
	cd backend && ./mvnw -q test

backend-build:
	cd backend && ./mvnw -q -DskipTests package

frontend-test:
	npm --prefix frontend test

frontend-build:
	npm --prefix frontend run build

verify: verify-layout backend-test frontend-test backend-build frontend-build
	git diff --check

dev-db:
	docker compose --env-file $(ENV_FILE) up -d postgres

dev-backend:
	set -a; . ./$(ENV_FILE); set +a; cd backend && ./mvnw spring-boot:run

dev-frontend:
	npm --prefix frontend run dev

compose-smoke:
	./scripts/compose-smoke.sh

e2e-smoke:
	./scripts/e2e-smoke.sh

clean:
	cd backend && ./mvnw -q clean
	rm -rf frontend/dist frontend/coverage frontend/test-results frontend/playwright-report
~~~

- [ ] **步骤 4：记录设置与验证方式**

创建 <code>README.md</code>：

~~~~markdown
# 知家 · zija

知家是面向单个家庭、多位成员的私有化物品与库存管理系统。首个交付阶段提供 Java 25 Spring Boot 模块化后端、MyBatis-Plus、PostgreSQL、Vue 3 + Element Plus 桌面壳层和 Docker Compose。

## 本地要求

- JDK 25
- Node.js 24
- Docker Engine 与 Docker Compose v2
- curl

## 首次准备

~~~bash
cp .env.example .env
npm --prefix frontend install
npm --prefix frontend exec -- playwright install chromium
~~~

将 <code>.env</code> 中的数据库密码改为仅用于本机开发的值。

## 本地开发

先启动数据库：

~~~bash
make dev-db
~~~

分别在两个终端启动后端和前端：

~~~bash
make dev-backend
make dev-frontend
~~~

浏览器访问 <http://localhost:5173>。

## 验证

~~~bash
make verify
make compose-smoke
make e2e-smoke
~~~

<code>make verify</code> 运行后端、前端、模块边界、PostgreSQL Testcontainers、类型检查和生产构建。两个 smoke 命令会创建临时 Compose 数据卷并在结束时删除。

## 方案与计划

- 设计方案：<code>docs/superpowers/specs/2026-07-18-zija-design.md</code>
- 交付路线：<code>docs/superpowers/plans/2026-07-19-delivery-roadmap.md</code>
- 工程基础计划：<code>docs/superpowers/plans/2026-07-19-foundation-baseline.md</code>
~~~~

- [ ] **步骤 5：运行稳定的验证入口**

~~~bash
make verify
~~~

预期结果：布局验证、全部后端测试、全部前端测试、后端打包、前端构建和空白字符检查全部通过。

- [ ] **步骤 6：提交开发者工作流**

~~~bash
git add Makefile README.md scripts/verify-layout.sh
git commit -m "docs: add stable development workflow"
~~~

## 任务 11：添加持续集成

**文件：**
- 创建：<code>.github/workflows/ci.yml</code>

- [ ] **步骤 1：添加后端、前端和部署 job**

创建 <code>.github/workflows/ci.yml</code>：

~~~yaml
name: ci

on:
  push:
    branches:
      - main
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "25"
          cache: maven
      - name: Verify backend
        working-directory: backend
        run: |
          chmod +x mvnw
          ./mvnw -B verify

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install frontend dependencies
        run: npm --prefix frontend ci
      - name: Test frontend
        run: npm --prefix frontend test
      - name: Build frontend
        run: npm --prefix frontend run build

  deployment-smoke:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install frontend dependencies
        run: npm --prefix frontend ci
      - name: Install Chromium
        run: npm --prefix frontend exec -- playwright install --with-deps chromium
      - name: Run Compose smoke
        run: ./scripts/compose-smoke.sh
      - name: Run browser smoke
        run: ./scripts/e2e-smoke.sh
~~~

- [ ] **步骤 2：验证工作流与本地命令的一致性**

运行：

~~~bash
make verify
make compose-smoke
make e2e-smoke
git diff --check
~~~

预期结果：每条命令均以状态 <code>0</code> 退出。

- [ ] **步骤 3：提交持续集成配置**

~~~bash
git add .github/workflows/ci.yml
git commit -m "ci: verify backend frontend and deployment"
~~~

## 任务 12：运行阶段 1 完成门禁

**文件：**
- 仅进行验证；预期不修改源文件。

- [ ] **步骤 1：验证仓库布局**

~~~bash
./scripts/verify-layout.sh
~~~

预期结果：输出 <code>repository layout verified</code>。

- [ ] **步骤 2：运行全部非容器检查**

~~~bash
make verify
~~~

预期结果：后端测试、前端测试、类型检查、后端打包、前端生产构建、模块边界验证、模块文档生成、Flyway 迁移和 PostgreSQL 集成测试全部通过。

- [ ] **步骤 3：验证容器部署**

~~~bash
make compose-smoke
~~~

预期结果：输出 <code>compose smoke passed</code>；临时测试 volume 已删除。

- [ ] **步骤 4：验证浏览器工作流**

~~~bash
make e2e-smoke
~~~

预期结果：Playwright 确认页面包含“系统状态”“系统运行正常”“PostgreSQL 已连接”和通用的“管理员”占位文本。

- [ ] **步骤 5：验证 Git 整洁性**

~~~bash
git diff --check
git status --short
git log --oneline --decorate -12
~~~

预期结果：<code>git diff --check</code> 无输出，<code>git status --short</code> 无输出，且日志显示任务 1–11 对应的聚焦提交。

- [ ] **步骤 6：在交付路线图中记录阶段结果**

仅在前述命令全部通过后，才编辑 <code>docs/superpowers/plans/2026-07-19-delivery-roadmap.md</code>。勾选阶段 1 的两个复选框，并在其下方直接追加已验证的 commit ID 和命令结果。提交这项仅涉及文档的变更：

~~~bash
git add docs/superpowers/plans/2026-07-19-delivery-roadmap.md
git commit -m "docs: record foundation phase completion"
~~~

预期结果：仅在具备可执行证据后，阶段 1 才被标记为完成。

## 计划自审清单

- [ ] 阶段 1 严格处于工程基础范围内，不实现身份、目录、库存、提醒或报表功能。
- [ ] 每项源代码变更之前都有失败测试，或该变更属于明确的构建/配置脚手架。
- [ ] MyBatis-Plus 处理简单持久化，同时保留显式 XML SQL，以支持后续库存锁定和报表功能。
- [ ] 使用 PostgreSQL Testcontainers 而非 H2 验证 Flyway 和 Mapper 行为。
- [ ] Spring Modulith 验证模块边界并生成模块画布。
- [ ] 前端使用 Element Plus 和通用的“管理员”占位文本。
- [ ] 本地、Compose、Playwright 和 CI 命令使用相同脚本和预期端口。
- [ ] 所有任务均不包含未解决的占位符、未指定的文件或未经验证的命令。
