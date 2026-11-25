# ops/ — 东角山运维 runbook

Kevin 的 Mac 上跑，给 Claude / Kevin 查状态 + 触发部署用。认证走 aliyun CLI profile
`djs-staging` / `djs-prod`（RAM 用户 `claude-deployer`，最小权限：查状态 / RunCommand / 触发部署 /
读 RDS·OSS 指标；**不能**新建·删除资源·改计费·开安全组）。服务器操作走 ECS Cloud Assistant
RunCommand（零入站端口、全 ActionTrail 审计），不用 SSH。

## 命令

```bash
bash ops/status.sh prod            # 一屏查全部（只读，随时安全）
bash ops/status.sh staging
bash ops/logs.sh prod 200          # 后端容器最近 200 行日志（只读）
bash ops/deploy.sh staging app     # 触发 staging 后端部署（app|admin|trace）
bash ops/deploy.sh prod app --yes  # prod 部署：人工闸，需已获 Kevin "go" 才加 --yes
bash ops/redis-flush-dict.sh prod --yes   # 刷字典缓存（改动，prod 需 --yes）
```

- `app`=后端(RuoYi-Vue-Plus) · `admin`=plus-ui · `trace`=trace-h5。
- **prod 部署/重启/刷缓存是人工闸**：Kevin 说 "go" → 加 `--yes`。staging 全自动。
- `deploy.sh` 依赖 `gh` 已登录（`gh auth login`）；`gh workflow run` 会触发 GitHub Actions。

## 资源清单（`env.map`，只 ID 无密钥）

| | staging | prod |
|---|---|---|
| ECS | i-bp13ayi7hktg413axrpf · 47.110.224.199 | i-bp13m28pmbtmhw4s1bso · 47.97.99.58 |
| RDS | rm-bp11hpm242954e99s | rm-bp12xt6ims04n8y6e (HA 2C4G 50GB) |
| OSS 前缀 | djs-staging-{private,public,trace} | dongjiaoshan-{private,public,trace} |
| 分支 | staging | main |

## 密钥位置（都在 `$HOME`，仓库外，chmod 600）

- `~/.aliyun/config.json` — profile `dongjiaoshan`(admin，特权一次性用) / `djs-staging` / `djs-prod`
- `~/.dongjiaoshan-secrets/` — OSS app 子账号 key（`oss-{staging,prod}.env`，写入各环境 `sys_oss_config`）
  和 claude-deployer 两把 AK 备份（`djs-{staging,prod}.env`）

**铁律**：绝不把任何 AK/SK 或 `.env` 提交进仓库；展示命令脱敏；本机 VPN 代理会劫持 aliyun API，
`lib.sh` 已 `export no_proxy='*'` 绕过。

## prod → staging 数据同步

让 staging 的数据和生产一致，测试才有代表性。生产只读，只写 staging。脚本：`ops/sync-prod-to-staging.sh`。

```bash
bash ops/sync-prod-to-staging.sh            # 只备份 + 打印计划，staging 不动（先跑这个看计划）
bash ops/sync-prod-to-staging.sh --execute  # 真正覆盖 staging
```

**做了什么**
1. 备份 staging 全库、导出 prod 全库（`mysqldump --single-transaction` 一致性快照，只读），存在 `~/dongjiaoshan-backups/db/`（仓库外，chmod 700），另存 prod 快照时刻的逐表行数。
2. 保存 staging 的 `sys_oss_config`（桶名、密钥按环境不同；不恢复的话 staging 上传会写进生产桶）。
3. `--execute` 才做：先把 prod 导进 staging RDS 上的临时库 `dongjiaoshan_sync`（后端照常运行，本机经 VPN 连 RDS 很慢，这一步要几十分钟），核对逐表行数。
4. 然后停 staging 后端，用一条 `RENAME TABLE` 换表：staging 原来的表（含领先迁移建的表）整体挪进 `dongjiaoshan_pre_<时间>`，临时库的表挪进 `dongjiaoshan`；恢复 `sys_oss_config`；清 staging Redis（FLUSHALL：字典、配置、OSS 配置、登录态）；起 staging 后端。**停机只有换表这一两分钟。**
5. 校验 `sys_oss_config` 桶名是 `djs-staging`、后端 healthy、Flyway 日志。

**回滚**：脚本会把反向的 `RENAME TABLE` 语句写到 `~/dongjiaoshan-backups/db/rollback-<时间>.sql`；停后端 → 执行它 → 起后端，几秒钟恢复原样。确认无误后可以 `DROP DATABASE dongjiaoshan_pre_<时间>`。

**要知道的**
- **staging 上原有的测试数据会全部被生产数据替换**（测试人员录的单据、测试账号）。原表留在 `dongjiaoshan_pre_<时间>` 库里，另有全库备份 `staging-<时间>.sql.gz`。动手前通知测试人员。
- **Flyway**：生产的 `flyway_schema_history` 覆盖过来后，staging 后端启动时会把 staging 领先生产的迁移（`staging` 分支已有、`main` 还没有的）重新跑一遍。这正好是一次上生产的预演；脚本会提前列出这些迁移。这些迁移建的表在换表时一起挪走，否则启动会报「表已存在」。
- **保留不动的 staging 独有表**：`sj_*`（SnailJob 调度）、`flow_*`（工作流）、`test_leave`。如果以后 staging 又多出别的基础设施表，加到脚本里的 `KEEP_STAGING_ONLY_REGEX`。
- **环境专属配置**：目前只有 `sys_oss_config` 两边不同（`sys_config`、`sys_client`、`sys_tenant` 内容一致）。每次同步前比一下，新增了环境专属的表或行就加进脚本的恢复步骤。
- **外发消息**：生产数据里有真实用户的微信订阅记录，小程序两个环境共用同一个 appid。目前没有任何业务调用订阅消息推送（`IWechatSubscribeMessageService` 无调用方），所以不会误发。**以后接入推送、短信、邮件时，同步前必须先在 staging 关掉它们**。
- **图片**：业务表里的图片地址指向生产桶（公共读），staging 页面能正常显示；staging 新上传的走 staging 桶。
- **指标表**：导入后 staging 的指标表是生产代码算出来的。staging 代码口径不同的，要按需重跑（`POST /djs/job/rerun/run`）或本机回放后写回。
- 生产库约 160 MB。本机连 RDS 走 VPN 出口，实测约 30 KB/s：导出约 1 小时、导入临时库约 1 小时，期间 staging 不停机。
- 脚本里 mysqldump 先写到 dev-mysql 容器内的文件再拷出来；直接经 `docker exec` 标准输出流式传输会卡死。数据库密码只经环境变量传进容器，不出现在进程命令行里。
