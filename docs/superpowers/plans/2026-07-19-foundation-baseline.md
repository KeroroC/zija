# Zija Engineering Foundation Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a clean, reproducible foundation for zija with a Java 25 modular Spring Boot backend, MyBatis-Plus and PostgreSQL persistence, a Vue 3 + Element Plus desktop shell, and verified local and container workflows.

**Architecture:** Keep backend and frontend as separate build units under one repository. The backend starts as a Spring Modulith modular monolith with one public system module and a real PostgreSQL-backed status API; the frontend consumes the versioned REST API through Nginx, while Docker Compose provides the deployment topology that later phases extend.

**Tech Stack:** Java 25, Maven 3.9.11 Wrapper, Spring Boot 4.1.0, Spring Modulith 2.0.5, MyBatis-Plus 3.5.16, Flyway, PostgreSQL 17, JUnit 5, AssertJ, Testcontainers, Vue 3.5, TypeScript 5.8, Vite 7, Element Plus 2.10, Vitest 3, Playwright 1.53, Node.js 24, Docker Compose, Nginx 1.28, GitHub Actions

---

## Plan Scope

This plan implements only delivery-roadmap Phase 1. It establishes infrastructure and one vertical system-status slice. It fixes the versioned URL, request ID, and Problem Details conventions; generated OpenAPI contracts begin in Phase 2 when authenticated business APIs exist. It does not implement household accounts, sessions, catalog data, locations, inventory movements, reminders, reports, CSV exchange, or business file uploads.

## Prerequisites

- JDK 25 is installed and selected by <code>java -version</code>.
- A bootstrap Maven 3.9 installation is available once to generate the Maven Wrapper.
- Node.js 24 and npm are installed.
- Docker Engine with Docker Compose v2 is running.
- <code>curl</code> is available for HTTP smoke checks.
- Commands are executed from the repository root unless a step says otherwise.

## Target File Map

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

## Task 1: Establish Repository Conventions

**Files:**
- Create: <code>.editorconfig</code>
- Create: <code>.gitattributes</code>
- Create: <code>.env.example</code>

- [ ] **Step 1: Add editor settings**

Create <code>.editorconfig</code>:

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

- [ ] **Step 2: Normalize Git text files**

Create <code>.gitattributes</code>:

~~~gitattributes
* text=auto eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
*.png binary
*.jpg binary
*.jpeg binary
*.webp binary
~~~

- [ ] **Step 3: Document local environment variables**

Create <code>.env.example</code>:

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

- [ ] **Step 4: Verify formatting**

Run:

~~~bash
git diff --check
~~~

Expected: no output and exit status <code>0</code>.

- [ ] **Step 5: Commit repository conventions**

~~~bash
git add .editorconfig .gitattributes .env.example
git commit -m "chore: establish repository conventions"
~~~

## Task 2: Bootstrap the Java 25 Spring Boot Build

**Files:**
- Create: <code>backend/pom.xml</code>
- Create: <code>backend/src/test/java/com/zija/ZijaApplicationTest.java</code>
- Create: <code>backend/src/main/java/com/zija/ZijaApplication.java</code>
- Generate: <code>backend/mvnw</code>
- Generate: <code>backend/mvnw.cmd</code>
- Generate: <code>backend/.mvn/wrapper/maven-wrapper.properties</code>

- [ ] **Step 1: Create the Maven build**

Create <code>backend/pom.xml</code>:

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

- [ ] **Step 2: Write the failing application test**

Create <code>backend/src/test/java/com/zija/ZijaApplicationTest.java</code>:

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

- [ ] **Step 3: Run the test to verify it fails**

Run:

~~~bash
cd backend
mvn -q -Dtest=ZijaApplicationTest test
~~~

Expected: compilation fails because <code>ZijaApplication</code> does not exist.

- [ ] **Step 4: Add the application entry point**

Create <code>backend/src/main/java/com/zija/ZijaApplication.java</code>:

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

- [ ] **Step 5: Generate the Maven Wrapper**

Run from <code>backend/</code>:

~~~bash
mvn -N wrapper:wrapper -Dmaven=3.9.11
~~~

Expected: Maven creates <code>mvnw</code>, <code>mvnw.cmd</code>, and <code>.mvn/wrapper/maven-wrapper.properties</code> configured for Maven 3.9.11.

- [ ] **Step 6: Run the test through the Wrapper**

~~~bash
./mvnw -q -Dtest=ZijaApplicationTest test
~~~

Expected: one passing test.

- [ ] **Step 7: Commit the backend bootstrap**

~~~bash
git add backend
git commit -m "build: bootstrap spring boot backend"
~~~

## Task 3: Lock the Spring Modulith Boundary

**Files:**
- Create: <code>backend/src/main/java/com/zija/system/package-info.java</code>
- Create: <code>backend/src/main/java/com/zija/system/SystemApi.java</code>
- Create: <code>backend/src/test/java/com/zija/ModularityTests.java</code>
- Create: <code>backend/src/test/java/com/zija/DocumentationTests.java</code>

- [ ] **Step 1: Write the failing modularity test**

Create <code>backend/src/test/java/com/zija/ModularityTests.java</code>:

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

- [ ] **Step 2: Run the modularity test to verify it fails**

~~~bash
cd backend
./mvnw -q -Dtest=ModularityTests test
~~~

Expected: failure because no <code>system</code> application module exists.

- [ ] **Step 3: Define the system module and public API**

Create <code>backend/src/main/java/com/zija/system/package-info.java</code>:

~~~java
@org.springframework.modulith.ApplicationModule(
        displayName = "System"
)
package com.zija.system;
~~~

Create <code>backend/src/main/java/com/zija/system/SystemApi.java</code>:

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

- [ ] **Step 4: Add module documentation generation**

Create <code>backend/src/test/java/com/zija/DocumentationTests.java</code>:

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

- [ ] **Step 5: Verify module boundaries and documentation**

~~~bash
./mvnw -q -Dtest=ModularityTests,DocumentationTests test
test -d target/spring-modulith-docs
~~~

Expected: both tests pass and <code>target/spring-modulith-docs</code> exists.

- [ ] **Step 6: Commit the module baseline**

~~~bash
git add backend/src/main/java/com/zija/system backend/src/test/java/com/zija/ModularityTests.java backend/src/test/java/com/zija/DocumentationTests.java
git commit -m "test: enforce backend module boundaries"
~~~

## Task 4: Add PostgreSQL, Flyway, and MyBatis-Plus

**Files:**
- Create: <code>backend/src/test/java/com/zija/system/internal/persistence/SystemInstallationMapperIntegrationTest.java</code>
- Create: <code>backend/src/main/resources/db/migration/V1__create_system_installation.sql</code>
- Create: <code>backend/src/main/resources/application.yml</code>
- Create: <code>backend/src/main/java/com/zija/ZijaMybatisConfiguration.java</code>
- Create: <code>backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationEntity.java</code>
- Create: <code>backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationMapper.java</code>
- Create: <code>backend/src/main/resources/mapper/system/SystemInstallationMapper.xml</code>

- [ ] **Step 1: Write the failing PostgreSQL integration test**

Create <code>backend/src/test/java/com/zija/system/internal/persistence/SystemInstallationMapperIntegrationTest.java</code>:

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

- [ ] **Step 2: Run the integration test to verify it fails**

~~~bash
cd backend
./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

Expected: compilation fails because the Mapper and entity do not exist.

- [ ] **Step 3: Create the Flyway baseline**

Create <code>backend/src/main/resources/db/migration/V1__create_system_installation.sql</code>:

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

- [ ] **Step 4: Configure Spring, Flyway, and MyBatis-Plus**

Create <code>backend/src/main/resources/application.yml</code>:

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

Create <code>backend/src/main/java/com/zija/ZijaMybatisConfiguration.java</code>:

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

- [ ] **Step 5: Add the installation entity and Mapper**

Create <code>backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationEntity.java</code>:

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

Create <code>backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationMapper.java</code>:

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

Create <code>backend/src/main/resources/mapper/system/SystemInstallationMapper.xml</code>:

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

- [ ] **Step 6: Run the real PostgreSQL integration test**

~~~bash
./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

Expected: Testcontainers starts PostgreSQL 17, Flyway applies version 1, and the test passes.

- [ ] **Step 7: Commit the persistence baseline**

~~~bash
git add backend/src/main backend/src/test/java/com/zija/system/internal/persistence
git commit -m "feat: add postgresql mybatis foundation"
~~~

## Task 5: Expose a Secured System Information API

**Files:**
- Create: <code>backend/src/test/java/com/zija/system/internal/SystemControllerTest.java</code>
- Create: <code>backend/src/test/java/com/zija/system/SystemApplicationModuleTest.java</code>
- Create: <code>backend/src/main/java/com/zija/ZijaRequestIdFilter.java</code>
- Create: <code>backend/src/main/java/com/zija/ZijaSecurityConfiguration.java</code>
- Create: <code>backend/src/main/java/com/zija/system/internal/SystemInfoService.java</code>
- Create: <code>backend/src/main/java/com/zija/system/internal/SystemInfoResponse.java</code>
- Create: <code>backend/src/main/java/com/zija/system/internal/SystemController.java</code>
- Create: <code>backend/src/main/java/com/zija/system/internal/SystemStateUnavailableException.java</code>
- Create: <code>backend/src/main/java/com/zija/system/internal/SystemExceptionHandler.java</code>

- [ ] **Step 1: Write the failing MVC slice tests**

Create <code>backend/src/test/java/com/zija/system/internal/SystemControllerTest.java</code>:

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

- [ ] **Step 2: Run the MVC tests to verify they fail**

~~~bash
cd backend
./mvnw -q -Dtest=SystemControllerTest test
~~~

Expected: compilation fails because the controller and Web support classes do not exist.

- [ ] **Step 3: Add request correlation and baseline security**

Create <code>backend/src/main/java/com/zija/ZijaRequestIdFilter.java</code>:

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

Create <code>backend/src/main/java/com/zija/ZijaSecurityConfiguration.java</code>:

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

- [ ] **Step 4: Implement the system service and response**

Create <code>backend/src/main/java/com/zija/system/internal/SystemInfoService.java</code>:

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

Create <code>backend/src/main/java/com/zija/system/internal/SystemInfoResponse.java</code>:

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

Create <code>backend/src/main/java/com/zija/system/internal/SystemController.java</code>:

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

- [ ] **Step 5: Implement the Problem Details handler**

Create <code>backend/src/main/java/com/zija/system/internal/SystemStateUnavailableException.java</code>:

~~~java
package com.zija.system.internal;

class SystemStateUnavailableException extends RuntimeException {

    SystemStateUnavailableException(String message) {
        super(message);
    }
}
~~~

Create <code>backend/src/main/java/com/zija/system/internal/SystemExceptionHandler.java</code>:

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

- [ ] **Step 6: Run the MVC slice tests**

~~~bash
./mvnw -q -Dtest=SystemControllerTest test
~~~

Expected: all three MVC slice tests pass.

- [ ] **Step 7: Add a real system-module integration test**

Create <code>backend/src/test/java/com/zija/system/SystemApplicationModuleTest.java</code>:

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

- [ ] **Step 8: Run all backend tests**

~~~bash
./mvnw -q test
~~~

Expected: unit, MVC, modularity, documentation, module integration, and PostgreSQL Mapper tests all pass.

- [ ] **Step 9: Commit the system API slice**

~~~bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat: expose postgresql backed system status"
~~~

## Task 6: Establish the Vue 3 and Element Plus Toolchain

**Files:**
- Modify: <code>.gitignore</code>
- Create: <code>frontend/package.json</code>
- Generate: <code>frontend/package-lock.json</code>
- Create: <code>frontend/index.html</code>
- Create: <code>frontend/tsconfig.json</code>
- Create: <code>frontend/tsconfig.app.json</code>
- Create: <code>frontend/tsconfig.node.json</code>
- Create: <code>frontend/vite.config.ts</code>
- Create: <code>frontend/src/env.d.ts</code>
- Create: <code>frontend/src/test/setup.ts</code>

- [ ] **Step 1: Define frontend dependencies and commands**

Create <code>frontend/package.json</code>:

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

- [ ] **Step 2: Add TypeScript project configuration**

Create <code>frontend/tsconfig.json</code>:

~~~json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
~~~

Create <code>frontend/tsconfig.app.json</code>:

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

Create <code>frontend/tsconfig.node.json</code>:

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

- [ ] **Step 3: Add the Vite and Vitest configuration**

Create <code>frontend/vite.config.ts</code>:

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

Create <code>frontend/src/test/setup.ts</code>:

~~~typescript
import { afterEach } from "vitest";

afterEach(() => {
  document.body.innerHTML = "";
});
~~~

Create <code>frontend/src/env.d.ts</code>:

~~~typescript
/// <reference types="vite/client" />
~~~

- [ ] **Step 4: Add the HTML entry document**

Create <code>frontend/index.html</code>:

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

- [ ] **Step 5: Ignore generated frontend test artifacts**

Append these lines to <code>.gitignore</code>:

~~~gitignore
frontend/test-results/
frontend/playwright-report/
frontend/.vite/
~~~

- [ ] **Step 6: Install and lock frontend dependencies**

Run:

~~~bash
cd frontend
npm install
~~~

Expected: <code>package-lock.json</code> is created and npm reports no unresolved dependency.

- [ ] **Step 7: Verify the empty TypeScript project**

~~~bash
npm run typecheck
~~~

Expected: exit status <code>0</code>.

- [ ] **Step 8: Commit the frontend toolchain**

~~~bash
git add .gitignore frontend
git commit -m "build: establish vue frontend toolchain"
~~~

## Task 7: Build the Desktop Application Shell

**Files:**
- Create: <code>frontend/src/components/AppShell.test.ts</code>
- Create: <code>frontend/src/components/AppShell.vue</code>
- Create: <code>frontend/src/views/SystemStatusView.vue</code>
- Create: <code>frontend/src/router/index.ts</code>
- Create: <code>frontend/src/App.vue</code>
- Create: <code>frontend/src/main.ts</code>
- Create: <code>frontend/src/styles/index.css</code>

- [ ] **Step 1: Write the failing application-shell test**

Create <code>frontend/src/components/AppShell.test.ts</code>:

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

- [ ] **Step 2: Run the test to verify it fails**

~~~bash
cd frontend
npm test -- AppShell.test.ts
~~~

Expected: failure because <code>AppShell.vue</code> does not exist.

- [ ] **Step 3: Create the Element Plus application shell**

Create <code>frontend/src/components/AppShell.vue</code>:

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

Create <code>frontend/src/views/SystemStatusView.vue</code>:

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

- [ ] **Step 4: Wire the router and application entry**

Create <code>frontend/src/router/index.ts</code>:

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

Create <code>frontend/src/App.vue</code>:

~~~vue
<script setup lang="ts">
import AppShell from "./components/AppShell.vue";
</script>

<template>
  <AppShell />
</template>
~~~

Create <code>frontend/src/main.ts</code>:

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

- [ ] **Step 5: Add desktop styling**

Create <code>frontend/src/styles/index.css</code>:

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

- [ ] **Step 6: Run the component test and production build**

~~~bash
npm test -- AppShell.test.ts
npm run build
~~~

Expected: the component test passes and Vite writes <code>frontend/dist</code>.

- [ ] **Step 7: Commit the desktop shell**

~~~bash
git add frontend/src
git commit -m "feat: add desktop administration shell"
~~~

## Task 8: Connect the Vue Shell to the System API

**Files:**
- Create: <code>frontend/src/types/system.ts</code>
- Create: <code>frontend/src/api/http.ts</code>
- Create: <code>frontend/src/api/system.ts</code>
- Create: <code>frontend/src/views/SystemStatusView.test.ts</code>
- Modify: <code>frontend/src/views/SystemStatusView.vue</code>

- [ ] **Step 1: Write failing system-status view tests**

Create <code>frontend/src/views/SystemStatusView.test.ts</code>:

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

- [ ] **Step 2: Run the view tests to verify they fail**

~~~bash
cd frontend
npm test -- SystemStatusView.test.ts
~~~

Expected: failure because the API module and completed view do not exist.

- [ ] **Step 3: Define the API types and HTTP error**

Create <code>frontend/src/types/system.ts</code>:

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

Create <code>frontend/src/api/http.ts</code>:

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

Create <code>frontend/src/api/system.ts</code>:

~~~typescript
import type { SystemInfo } from "../types/system";
import { getJson } from "./http";

export function fetchSystemInfo(): Promise<SystemInfo> {
  return getJson<SystemInfo>("/api/v1/system/info");
}
~~~

- [ ] **Step 4: Implement the live system-status view**

Replace <code>frontend/src/views/SystemStatusView.vue</code> with:

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

- [ ] **Step 5: Run frontend tests, type checking, and build**

~~~bash
npm test
npm run typecheck
npm run build
~~~

Expected: all component tests pass and the production build succeeds.

- [ ] **Step 6: Commit the API integration**

~~~bash
git add frontend/src
git commit -m "feat: display live system status"
~~~

## Task 9: Add Docker Compose and Browser Smoke Tests

**Files:**
- Create: <code>.dockerignore</code>
- Create: <code>deploy/app/Dockerfile</code>
- Create: <code>deploy/web/Dockerfile</code>
- Create: <code>deploy/nginx/default.conf</code>
- Create: <code>compose.yaml</code>
- Create: <code>scripts/compose-smoke.sh</code>
- Create: <code>scripts/e2e-smoke.sh</code>
- Create: <code>frontend/playwright.config.ts</code>
- Create: <code>frontend/e2e/system-status.spec.ts</code>

- [ ] **Step 1: Write the failing Compose smoke script**

Create <code>scripts/compose-smoke.sh</code>:

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

Run:

~~~bash
chmod +x scripts/compose-smoke.sh
./scripts/compose-smoke.sh
~~~

Expected: failure because <code>compose.yaml</code> does not exist.

- [ ] **Step 2: Control the Docker build context**

Create <code>.dockerignore</code>:

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

- [ ] **Step 3: Add the backend container image**

Create <code>deploy/app/Dockerfile</code>:

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

- [ ] **Step 4: Add the frontend and Nginx image**

Create <code>deploy/web/Dockerfile</code>:

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

Create <code>deploy/nginx/default.conf</code>:

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

- [ ] **Step 5: Define the Compose topology**

Create <code>compose.yaml</code>:

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

- [ ] **Step 6: Run the Compose smoke test**

~~~bash
./scripts/compose-smoke.sh
~~~

Expected: the script prints <code>compose smoke passed</code> and removes the test containers and volume.

- [ ] **Step 7: Add the Playwright browser contract**

Create <code>frontend/playwright.config.ts</code>:

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

Create <code>frontend/e2e/system-status.spec.ts</code>:

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

Create <code>scripts/e2e-smoke.sh</code>:

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

- [ ] **Step 8: Run the browser smoke test**

~~~bash
chmod +x scripts/e2e-smoke.sh
npm --prefix frontend exec -- playwright install chromium
./scripts/e2e-smoke.sh
~~~

Expected: Playwright passes the Chromium scenario and the script prints <code>e2e smoke passed</code>.

- [ ] **Step 9: Commit the container deployment**

~~~bash
git add .dockerignore compose.yaml deploy scripts frontend/playwright.config.ts frontend/e2e
git commit -m "build: add compose deployment smoke tests"
~~~

## Task 10: Add Stable Developer Commands and Documentation

**Files:**
- Create: <code>scripts/verify-layout.sh</code>
- Create: <code>Makefile</code>
- Create: <code>README.md</code>

- [ ] **Step 1: Add a repository-layout verification script**

Create <code>scripts/verify-layout.sh</code>:

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

- [ ] **Step 2: Run the layout check before adding Make commands**

~~~bash
chmod +x scripts/verify-layout.sh
./scripts/verify-layout.sh
~~~

Expected: failure that reports <code>Makefile</code> as the first missing file.

- [ ] **Step 3: Add stable root commands**

Create <code>Makefile</code>:

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

- [ ] **Step 4: Document setup and verification**

Create <code>README.md</code>:

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

- [ ] **Step 5: Run the stable verification entry point**

~~~bash
make verify
~~~

Expected: layout verification, all backend tests, all frontend tests, backend package, frontend build, and whitespace checks pass.

- [ ] **Step 6: Commit developer workflows**

~~~bash
git add Makefile README.md scripts/verify-layout.sh
git commit -m "docs: add stable development workflow"
~~~

## Task 11: Add Continuous Integration

**Files:**
- Create: <code>.github/workflows/ci.yml</code>

- [ ] **Step 1: Add backend, frontend, and deployment jobs**

Create <code>.github/workflows/ci.yml</code>:

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

- [ ] **Step 2: Validate the workflow and local command parity**

Run:

~~~bash
make verify
make compose-smoke
make e2e-smoke
git diff --check
~~~

Expected: every command exits with status <code>0</code>.

- [ ] **Step 3: Commit continuous integration**

~~~bash
git add .github/workflows/ci.yml
git commit -m "ci: verify backend frontend and deployment"
~~~

## Task 12: Run the Phase 1 Completion Gate

**Files:**
- Verify only; no source file changes are expected.

- [ ] **Step 1: Verify the repository layout**

~~~bash
./scripts/verify-layout.sh
~~~

Expected: <code>repository layout verified</code>.

- [ ] **Step 2: Run all non-container checks**

~~~bash
make verify
~~~

Expected: backend tests, frontend tests, type checking, backend package, frontend production build, module boundary verification, module documentation generation, Flyway migrations, and PostgreSQL integration tests pass.

- [ ] **Step 3: Verify the container deployment**

~~~bash
make compose-smoke
~~~

Expected: <code>compose smoke passed</code>; the temporary test volume is removed.

- [ ] **Step 4: Verify the browser workflow**

~~~bash
make e2e-smoke
~~~

Expected: Playwright confirms the page contains “系统状态”, “系统运行正常”, “PostgreSQL 已连接”, and the generic “管理员” placeholder.

- [ ] **Step 5: Verify Git hygiene**

~~~bash
git diff --check
git status --short
git log --oneline --decorate -12
~~~

Expected: <code>git diff --check</code> has no output, <code>git status --short</code> has no output, and the log shows the focused commits from Tasks 1–11.

- [ ] **Step 6: Record the phase result in the delivery roadmap**

Edit <code>docs/superpowers/plans/2026-07-19-delivery-roadmap.md</code> only after all preceding commands pass. Check the two Phase 1 boxes and append the verified commit IDs and command results directly beneath them. Commit that documentation-only change:

~~~bash
git add docs/superpowers/plans/2026-07-19-delivery-roadmap.md
git commit -m "docs: record foundation phase completion"
~~~

Expected: Phase 1 is marked complete only after the executable evidence exists.

## Plan Self-Review Checklist

- [ ] Phase 1 stays within engineering-foundation scope and does not implement identity, catalog, inventory, reminders, or reports.
- [ ] Every source change is preceded by a failing test or is an explicit build/configuration scaffold.
- [ ] MyBatis-Plus handles simple persistence while explicit XML SQL remains available for later stock locking and reports.
- [ ] PostgreSQL Testcontainers, not H2, verifies Flyway and Mapper behavior.
- [ ] Spring Modulith verifies module boundaries and generates module canvases.
- [ ] The frontend uses Element Plus and a generic “管理员” placeholder.
- [ ] Local, Compose, Playwright, and CI commands use the same scripts and expected ports.
- [ ] No task contains an unresolved placeholder, unspecified file, or unverified command.
