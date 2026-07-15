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
