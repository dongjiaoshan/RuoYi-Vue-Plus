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


---

# djs 业务层规约（承接根 `.claude/CLAUDE.md`，只写增量、不重述根规则）

> 上面的内容是 RuoYi-Vue-Plus 上游框架说明。以下是**东角山 djs 业务模块**的规约。
> 根 CLAUDE.md 的强约束（不动 ruoyi 自带模块 / tenant_id / 包名 / DDL 命名 …）仍然生效，此处不重复。

## djs-menu：menu_id 分段占用表（建菜单前必读）

根 CLAUDE.md §6 #6 只写总段位，**具体谁占了哪一段在这里**。新建菜单前先查此表，避免撞号。

**总段位**：系统底座 5000-5999 / 养殖 7000-7999 / 种植 8000-8999 / 仓库 9000-9999 / 门店+追溯+DSH 10000-10999。养殖域 7000-7999 二级分段（D04 closing 固化）：

| 段 | 模块 | 当前占用 |
|---|---|---|
| 7000 | 养殖父菜单 | SYS-AUTH-001 |
| 7010-7019 | 育种配置（breed_info + breed_config 4 tab）| BRD-MD-001 用 7010-7015 |
| 7020-7029 | 农场（详情 panel）| BRD-MD-002 |
| 7030-7039 | 栋舍 | BRD-MD-002 |
| 7040-7049 | 栏位 | BRD-MD-002 |
| 7050-7059 | 生产周期配置（cycle days）| BRD-MD-003 用 7050-7055 |
| 7060-7069 | 药品库（menu 已迁仓库 9303 下 · ADR-0012）| BRD-MED-001 建；菜单 parent 改 9303，号段保留不复用 |
| 7070-7075 | 药品批次（menu 已迁仓库 9303 下 · ADR-0012）| BRD-MED-001 建；菜单 parent 改 9303 |
| 7076-7079 | 药品领用台账（领用 / 退回 / 损耗）| BRD-MED-002 用 7076-7079（D6） |
| 7080-7089 | 公猪/精液配置 + 治疗台账 | BRD-MD-003 用 7080-7084；**BRD-MED-003 用 7085-7089**（D7 已落） |
| 7090-7099 | 用药 schedule 扩展预留 | BRD-MD-003 用 7090-7094；7095-7099 预留 |
| 7100-7109 | BRD-EVENT-001 引种登记 | 7100-7104 已占 |
| 7110-7119 | BRD-EVENT-003 仔猪耳标 | 7110-7112 已占 |
| 7120-7129 | BRD-EVENT-002 配种（D6）| 已合并入 7145 事件台账（D6 hotfix）|
| 7130-7139 | BRD-EVENT-004 死淘出栏转移（D6）| 已合并入 7145 事件台账（D6 hotfix）|
| 7140-7144 | BRD-EVENT-005 生长记录 | D6 已占 |
| 7145-7149 | BRD-EVENT 事件台账透视（合并 002/004 多事件菜单）| D6 hotfix 已占 |
| 7150-7199 | 业务事件扩展预留 | — |
| 7200-7299 | BRD-CORE-001 状态机 | D5 |
| 7300-7399 | BRD-LIST-001 列表详情 | 后续（admin 端段） |
| 7360-7369 | mp 饲料权限子段（7360 picker / 7361 list / 7362-7364 pick/return/loss / 7365-7369 预留）| D9X MP-002 + D10 WMS-MAT-001-FEED-EXT 已占 7360-7364；7363 推 D11 hotfix-002 重 seed 到 7365 |
| 7400-7499 | BRD-DASH-001 dashboard | 后续 |

种植域 8000-8999 二级分段（D9 closing 固化）：

| 段 | 模块 | 当前占用 |
|---|---|---|
| 8000 | 种植父菜单 | SYS-AUTH-001 |
| 8010-8024 | 地块（plot）+ 片区（zone）| PLT-MD-001 |
| 8020-8024 | 作物（crop）+ 品种 | PLT-MD-001 |
| 8030-8034 | 班组（work_team）| PLT-MD-002 |
| 8050-8059 | 地块有机认证（plot_organic）| PLT-MD-003 |
| 8060-8065 | 作物有机认证（crop_organic）| PLT-MD-003 |
| 8070-8080 | 种植计划（plant_plan）| PLT-PLAN-001 |
| 8081-8089 | 采摘计划（pick_plan）预留 | D10 PLT-PLAN-002 |
| 8090-8099 | 农事录入（plant_work）预留 | D10 PLT-WORK-001 |
| 8100-8109 | 种植灾害记录（disaster）| PLT-WORK-003 用 8100-8102 |
| 8110-8199 | 种植看板（dashboard）| PLT-DASH-001 用 8110-8111 |
| 8200-8299 | 班组绩效（work_performance）| PLT-PERF-001（D12）|
| 8500-8599 | mp 种植工操作（采收录入等）| PLT-PICK-001（D12）|

仓库域 9000-9999 二级分段（D11 closing 固化）：

| 段 | 模块 | 当前占用 |
|---|---|---|
| 9000 | 仓库父菜单 | SYS-AUTH-001 |
| 9010-9025 | 库位 + 库存查询（9026-9027 = 各库位情况聚合卡抽独立菜单）| WMS-MD-001/002 · FIX-WMS-LOC-OVERVIEW-001 |
| 9030-9035 | 商品主数据（旧单入口，FIX-WMS-PRODSPLIT-001 起默认隐藏 visible='1'）| WMS-MD-002 |
| 9036-9038 | 产品/商品/礼盒三独立入口（共表 query_param 注入 productType=1/2/3）| FIX-WMS-PRODSPLIT-001 |
| 9040-9054 | 需求管理（4 业态 + 状态机操作）| WMS-DEMAND-001 |
| 9060-9063 | 采购入库 | WMS-MAT-001 |
| 9070-9084 | 燎毛 / 分割 / 白条出库 | 养殖出栏后处理 |
| 9090-9098 | 毛菜处理 | WMS-VEG |
| 9100-9114 | 包材/物资领用 + 出入库流水 | WMS-MAT-001 / WMS-FLOW-001（D11）|
| 9116-9118 | 生产物资领用（admin 镜像 mp 物资领用，挂产品生产管理 9301）| WMS-MATPICK-ADMIN-001 |
| 9120-9125 | 库存总览（每日库存汇总，挂库存管理 9302）| WMS-STOCK-OVERVIEW-001 用 9123-9125 |
| 9126-9129 | 损耗总览（每日损耗汇总，挂库存管理 9302）| WMS-LOSS-OVERVIEW-001 用 9126-9128 |
| 9200-9219 | 发货月台 / 退货 / 发货流水 | WMS-SHIP-001 |
| 9220-9224 | 打包工序（mp 4 类）| WMS-PACK-001 |
| 9230-9248 | 生产记录 / 入出库记录 / 包材库 | WMS-PACK-001 |
| 9250-9265 | 库存盘点（admin + mp 录入）| WMS-STOCK-001（D11）|
| 9270-9275 | 需求调度（mp 调度员）| WMS-DEMAND-002（D11）|
| 9400-9499 | 仓库看板（dashboard）| W22-006（D11）|

门店+追溯+DSH 域 10000-10999 二级分段（D12 closing 固化）：

| 段 | 模块 | 当前占用 |
|---|---|---|
| 10000 | 门店父菜单 | SYS-AUTH-001 |
| 10000-10099 | 门店需求录入（demand）| STR-DEMAND-001（D12）|
| 10100-10199 | 门店经营（产品关联 + 销售明细）| STR-OP-001（D12）|
| 10200-10299 | 门店盘点（store_check）| STR-STOCK-001（D12）|
| 10300-10399 | 门店收尾（拆单/会员/退货）预留 | STR-SPLIT/MEMBER/RETURN-001（D13）|
| 10400-10499 | 门店 dashboard 预留 | STR-DASH-001（D13）|
| 10500-10699 | 追溯（TRC）预留 | TRC-CORE-001 / TRC-ADMIN-001（D14）|
| 10700-10799 | 门店盘点 mp 子段预留 | — |
| 10800-10999 | DSH 驾驶舱预留 | DSH-MGMT-001（D15）|


## djs-local-run：⚠️ 改动 ruoyi-djs-* 模块代码后必须 install + restart

`spring-boot:run -pl ruoyi-admin` 加载的是 `~/.m2/repository/.../ruoyi-djs-*-<ver>.jar`（不是 `target/classes`）。改动 `ruoyi-djs-common / breed / plant / warehouse / store` 任一模块的代码后，跑：

```bash
cd code/main/RuoYi-Vue-Plus
mvn install -DskipTests -pl ruoyi-modules/ruoyi-djs-common,ruoyi-modules/ruoyi-djs-breed,ruoyi-modules/ruoyi-djs-plant,ruoyi-modules/ruoyi-djs-warehouse,ruoyi-modules/ruoyi-djs-store -am
# Ctrl+C 当前 spring-boot:run 终端，重跑
mvn spring-boot:run -pl ruoyi-admin
```

**不跑 install 的表现**：新写的 Controller 路由全 404 `请求地址不存在`；新加的 Service Bean 不存在。
**例外**：改 `ruoyi-admin/` 自身代码不需要 install（admin 走 `target/classes`）。

### ⚠️ 跑完 SQL cleanup 后必须 flush redis 字典缓存

`V202605201500~V202605201700` 三个 cleanup SQL 会 DELETE 部分 ruoyi 默认字典数据。如果 admin 在 cleanup 前已起过哪怕 1 次，ruoyi 自带 Redisson 会把"查空"结果按 `NullValue` 写到 `1001:sys_dict` hash（TTL 1h）；之后即使 SQL 重新灌数据，缓存仍返 NullValue 直到自然过期。

每次初始化 / 重建数据库后，跑：

```bash
cd code/main/RuoYi-Vue-Plus
bash script/sql/djs/_post-init.sh
```

脚本会清掉所有 `*:sys_dict` hash，让下次访问重读 DB。**不跑的表现**：admin 表单字典下拉空（典型：性别 / 启用停用 / 显示隐藏 / 是否）。


## djs-test：单测命令

**注意 `-DskipTests=false`** —— 根 pom `<skipTests>true</skipTests>` 默认跳过单测，不加 flag 跑 `mvn test` 只会显示 "Tests are skipped"。

```bash
cd code/main/RuoYi-Vue-Plus && mvn test -DskipTests=false -pl ruoyi-modules/ruoyi-djs-<域> -am
cd code/main/plus-ui && pnpm test       # 尚未接入 vitest
cd code/main/miniapp && pnpm test       # D04 SYS-INFRA-009 起 vitest 已接
```

覆盖率目标：业务核心 ≥ 60%；状态机（`PigStateMachine`）≥ 80%。


## djs-env：staging / prod 环境全量对照（根 CLAUDE.md §5.1 的展开）

**两套环境已完整分离**（ECS / RDS / OSS / 域名 / 部署分支 全部独立，无任何共享）。权威清单在
[code/main/RuoYi-Vue-Plus/ops/env.map](ops/env.map)，下表是快照：

| | **staging**（测试人员在用） | **prod**（生产） |
|---|---|---|
| 域名 | `api-staging` / `admin-staging` / `trace-staging`**.dongjiaoshan.com** | `api` / `admin` / `trace`**.dongjiaoshan.com** |
| ECS | `i-bp13ayi7hktg413axrpf` · **47.110.224.199** | `i-bp13m28pmbtmhw4s1bso` · **47.97.99.58** |
| RDS | `rm-bp11hpm242954e99s` | `rm-bp12xt6ims04n8y6e` |
| OSS 桶（各一个·公共读直链）| `djs-staging` | `djs-main` |
| 部署分支 | **`staging`** → push 即自动部署（→ staging box）| **`main`** → push 即自动部署（→ prod box）|
| 容器名 | `djs-ruoyi-admin-staging` / `djs-redis-staging` | `djs-ruoyi-admin-prod` / `djs-redis-prod` |
| 后端部署目录 | `/www/wwwroot/api.dongjiaoshan.com`（两台同路径，宝塔管）| 同左 |

**必须记住的三条**：

1. **不带 `-staging` 的域名是生产**。`admin.dongjiaoshan.com` / `api.dongjiaoshan.com` = **prod**，
   查 staging 状态、验 staging 部署、打 staging 接口，一律用 `*-staging.dongjiaoshan.com`。
   （踩过：拿 prod 的域名验 staging 部署，看到一周前的产物，误报"部署没生效"。）
2. **`application-dev.yml` 的 `staging` 数据源块指的是 staging RDS**，不是生产库。
   `.mp-auto/db.py` 连的也是它。**生产库没有任何本地 helper 能连**，要动生产得走 `ops/` 工具。
3. **改生产是人工闸**：`ops/deploy.sh prod` / `ops/redis-flush-dict.sh prod` 都要 `--yes` + Kevin 明确说 go。
   **AI 不得自行推 `main` 分支、不得自行操作 prod 资源。**

查环境状态用只读工具：`bash code/main/RuoYi-Vue-Plus/ops/status.sh {staging|prod}`、
`ops/logs.sh {staging|prod}`（走 ECS RunCommand 非 SSH，全审计）。
本机 VPN 代理会劫持 aliyun API，**所有 ops / aliyun / RDS 调用前先 `export no_proxy='*'`**。

**前端按环境自适应（关键 —— 三前端都统一 `build:prod` 打包，不分环境 build；staging/prod 差异全靠"相对路径 + 按 host 派生"，不是分环境配置文件）**：

- **admin / trace 都用相对 `/prod-api`**：每台 box 的 nginx 把 `/prod-api/` 反代到本机后端 `127.0.0.1:8080`，所以 `admin-staging`→staging 后端、`admin`→prod 后端，自动隔离，前端零配置差异。trace 站另需 `location / { try_files $uri $uri/ /index.html; }`（vue-router history 的 SPA 回退，否则扫码 URL `/trace/{type}/{code}` 直接 404）。**这两段 nginx 在宝塔站点的「伪静态」里**（`/www/server/panel/vhost/rewrite/*.conf`），不在「配置文件」（主配置只 `include` 引用它）。
- **追溯二维码 URL 按 admin 域名派生 trace 域名**（`admin(-staging).dongjiaoshan.com` → `trace(-staging).dongjiaoshan.com`）：逻辑在 `plus-ui` 两处 `buildTraceUrl`（`src/views/trace/useTrace.ts` + `src/views/djs-store/trace/components/TraceLabelDialog.vue`）。改追溯码/二维码域名改这两处；`.env.production` 的 `VITE_APP_TRACE_BASE` 只是非 admin host 的兜底。
- **miniapp 硬编码 `api.dongjiaoshan.com`（永远 prod）**：一个 appid、一套发布生命周期，不分环境。staging 分支 → 体验版(robot1)；**prod 正式版 Kevin 手动在微信后台提审+发布，不自动部署**（`main` 分支不上传微信）。
- **OSS**：各一桶 `djs-staging` / `djs-main`（都公共读、直链、`sys_oss_config.access_policy=1`）。⚠️ 账号有**管控策略禁 `oss:PutBucket` / `ListBuckets` / 设公共读**——建桶、删桶、改公共读**只能 Kevin 控制台**做；AI 只能对已有桶读写对象、改 `sys_oss_config`(DB)、管 OSS 应用 RAM key。

**两台都装宝塔**（prod 面板 `https://47.97.99.58:32571/<入口>`，账号密码+API token 见 `~/.dongjiaoshan-secrets/bt-panel-prod.env`）。**所有密钥都在 `~/.dongjiaoshan-secrets/`（chmod 600，绝不进 git）**：`prod.env`(prod 后端 .env 全量) · `rds-prod.env`(prod DB) · `oss-prod.env`(prod OSS 应用 key) · `ci_prod_deploy`(=GitHub secret `SSH_PRIVATE_KEY_PROD`) · `djs-staging.env`/`djs-prod.env`(claude-deployer 运维 key) · `bt-panel-prod.env`。aliyun 运维走 CLI profile **`djs-staging` / `djs-prod`**（最小权限 RAM 用户 `claude-deployer`：查状态 / RunCommand / 触发部署 / 读 RDS·OSS，不能建删资源·改计费·开安全组）；全权 admin profile = `dongjiaoshan`（RAM `shuzihua_dongjiaoshan`，只一次性特权用）。prod 登录密码：admin/**djsadmin**（同 staging）。
