# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Dromara **RuoYi-Vue-Plus** — a Spring Boot 3.5 / JDK 17+ backend rewrite of RuoYi-Vue targeting distributed, multi-tenant scenarios. This repo is backend-only; the frontend (`plus-ui`) lives in a separate repo. Current version is tracked in `pom.xml` via the `${revision}` property.

## Required Environment (from official quickstart doc)

| Component | Version | Notes |
|---|---|---|
| JDK | **17** or 21 | Use **OpenJDK**, not Oracle JDK (Spring packaging/runtime issues). Project default is 17. |
| Maven | ≥ 3.8 | |
| MySQL | 5.7 or 8.0 | Default DB; init scripts in `script/sql/`. Other DBs in `script/sql/{oracle,postgres,sqlserver}/`. |
| Redis | ≥ 6 | Redisson client; uses modern commands. |
| MinIO | latest | Needed only if using file upload / OSS features. |
| Node.js / npm | ≥ 20.15 / ≥ 8 | Only for the separate `plus-ui` frontend repo. npm 7.x is broken. |
| IDEA | 2024.3+ (JDK17) | Select the matching Maven profile in IDE run config. |

### SQL init order (MySQL)
Create DB `ry-vue` (utf8mb4), then import in order:
1. `script/sql/ry_vue_5.X.sql` — core schema + seed data
2. `script/sql/ry_job.sql` — SnailJob tables
3. `script/sql/ry_workflow.sql` — Flowable workflow tables
4. Any pending upgrade scripts under `script/sql/update/` if migrating.

### Startup order
1. MySQL + Redis (required)
2. `ruoyi-extend/ruoyi-monitor-admin` → `MonitorAdminApplication` (optional, Spring Boot Admin UI)
3. `ruoyi-extend/ruoyi-snailjob-server` → `SnailJobServerApplication` (required if `ruoyi-job` is enabled)
4. `ruoyi-admin` → `DromaraApplication` (main service)

For a minimal smoke test only MySQL + Redis + `DromaraApplication` are needed.

Edit `ruoyi-admin/src/main/resources/application-dev.yml` to point at your MySQL/Redis/MinIO before first run.

## Build / Run

Maven multi-module build (root `pom.xml` uses `flatten-maven-plugin` + `${revision}`).

```bash
# Build everything, skipping tests
mvn clean package -DskipTests

# Build a single module (and its dependencies)
mvn -pl ruoyi-modules/ruoyi-system -am clean install -DskipTests

# Run the application (dev profile by default via application.yml -> spring.profiles.active: @profiles.active@)
mvn -pl ruoyi-admin spring-boot:run

# Run built jar
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev

# Production start/stop helper
./script/bin/ry.sh {start|stop|restart|status}   # expects ruoyi-admin.jar in cwd

# Full local stack (MySQL, Redis, Minio, Nginx, SnailJob, Monitor, app)
docker compose -f script/docker/docker-compose.yml up -d
```

Profiles: `dev` / `prod` via `ruoyi-admin/src/main/resources/application-{dev,prod}.yml`. Shared infra config (datasource, redis, minio, sa-token, mybatis-plus, etc.) is in `application.yml`. SQL init scripts live under `script/sql/`.

Tests use JUnit 5 via `spring-boot-starter-test`; there is no repo-wide test suite — run `mvn test` within an individual module, or a single test via `mvn -pl <module> test -Dtest=ClassName#method`.

## Architecture

Three-layer Maven hierarchy — keep new code in the layer that matches its role:

- **`ruoyi-common/`** — reusable starter-style libraries, one Maven module per cross-cutting concern (`-core`, `-web`, `-mybatis`, `-redis`, `-satoken`, `-tenant`, `-security`, `-encrypt`, `-sensitive`, `-translation`, `-log`, `-idempotent`, `-ratelimiter`, `-excel`, `-oss`, `-sms`, `-mail`, `-job`, `-social`, `-sse`, `-websocket`, `-doc`, `-json`). `ruoyi-common-bom` centralizes versions. Business modules should depend on these rather than reimplementing infra.
- **`ruoyi-modules/`** — business domains: `ruoyi-system` (users/roles/depts/dict/tenant — the core), `ruoyi-generator` (code generator), `ruoyi-job` (SnailJob integration), `ruoyi-workflow` (Flowable-based approvals), `ruoyi-demo` (reference examples for framework features — consult before inventing patterns).
- **`ruoyi-admin/`** — the single deployable Spring Boot app. It is the *only* module with a `main` class and aggregates all `ruoyi-modules/*` as dependencies. Controllers for system features live here under `org.dromara.web` (login/captcha/SSO); domain controllers live inside their respective modules.
- **`ruoyi-extend/`** — optional side services (`ruoyi-monitor-admin` = Spring Boot Admin server, `ruoyi-snailjob-server` = job scheduling server). These are **separate deployables**, not dependencies of `ruoyi-admin`.

### Key framework choices (don't fight these)

- **Persistence**: MyBatis-Plus with custom `BaseMapperPlus` / `ServiceImpl` extensions in `ruoyi-common-mybatis`. Entities extend `TenantEntity` (defined in `ruoyi-common-tenant`) / `BaseEntity`. Data permission, tenant isolation, and encryption are implemented as MP interceptors — apply via annotations (`@DataPermission`, `@TenantIgnore`, `@EncryptField`) rather than writing raw SQL filters.
- **Multi-datasource**: `dynamic-datasource`; switch via `@DS("name")`. Master is `master`.
- **Auth**: Sa-Token (not Spring Security). Permission checks use `@SaCheckPermission` / `@SaCheckRole`. Login logic is in `ruoyi-admin` `org.dromara.web.service`.
- **Multi-tenancy**: enabled by default via `ruoyi-common-tenant`; most tables carry `tenant_id` and MP auto-injects the filter. Use `TenantHelper.ignore(...)` for cross-tenant operations.
- **Caching**: Redisson + Spring Cache with extensions (`@Cacheable` supports TTL/maxIdle/maxSize via custom cache names in `CacheNames`). Do not use raw `RedisTemplate`.
- **Excel**: FastExcel (not EasyExcel/POI) via `ruoyi-common-excel` helpers (`ExcelUtil`).
- **API docs**: SpringDoc + therapi-javadoc — Javadoc comments become OpenAPI descriptions, so write real comments instead of `@Operation`/`@Schema` annotations.
- **Serialization**: Jackson only. Do not introduce fastjson/gson.
- **IDs**: Snowflake, assigned by MP — never rely on DB auto-increment for new tables.
- **Code generator** (`ruoyi-generator`): for new CRUD tables, prefer generating via the admin UI over hand-writing boilerplate.

### Request flow (typical)

`ruoyi-admin` controller / module controller → `@SaCheckPermission` → Service (`IXxxService` + `XxxServiceImpl` in the module) → `BaseMapperPlus` (MyBatis-Plus) with interceptors applying tenant/data-permission/encryption → MySQL via dynamic-datasource → response passes through Jackson with `@Sensitive` / `@Translation` serializers.

## Conventions

- Package root is `org.dromara`. New business code follows `org.dromara.<module>.{controller,service,service.impl,mapper,domain,domain.vo,domain.bo}`.
- DTOs: `XxxBo` (inbound), `XxxVo` (outbound), `Xxx` (entity). Use MapStruct (`BeanCopyUtils` / explicit converters) rather than reflection-heavy copying in hot paths.
- i18n message keys live in `ruoyi-common-core` `i18n/messages_*.properties`; prefer `MessageUtils.message(key)` for user-facing strings.
- Look at `ruoyi-modules/ruoyi-demo` before building a new cross-cutting feature — it has working examples for encryption, translation, i18n, Excel, SSE, WebSocket, idempotency, rate limiting, etc.
