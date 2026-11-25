#!/usr/bin/env bash
# ops/sync-prod-to-staging.sh [--execute | --swap-only <上次运行的时间戳>]
#
# 把 prod 库全量覆盖到 staging 库，让 staging 测试数据与生产一致。prod 只读（mysqldump 一致性快照），
# 只写 staging。不带 --execute 时只做备份 + 打印计划，不改 staging。
# --swap-only <TS>：上次运行导入临时库后中断时，跳过备份和导入，直接从换表继续。
#
# 步骤：
#   1. 备份 staging 全库、导出 prod 全库（~/dongjiaoshan-backups/db/，仓库外，chmod 700）
#   2. 保存 staging 环境专属行（sys_oss_config：桶 / 密钥按环境不同）
#   3. [--execute] 把 prod 导入 staging RDS 上的临时库 dongjiaoshan_sync（后端照常运行），核对行数
#   4. [--execute] 停 staging 后端 → 一条 RENAME TABLE 换表：staging 原有的表（含领先迁移建的表）
#      整体挪进 dongjiaoshan_pre_<时间>，临时库的表挪进 dongjiaoshan → 恢复 sys_oss_config
#      → 清 staging Redis → 起 staging 后端（Flyway 补跑 staging 领先的迁移）。停机只有换表这一两分钟
#   5. 校验：sys_oss_config 桶名、后端健康、Flyway 日志。回滚 = 停后端后把表 RENAME 回去（脚本会打印语句）
#
# 依赖：本机 docker 容器 dev-mysql（mysql/mysqldump 客户端）、aliyun CLI profile djs-staging、jq。
# 凭据：prod 读 ~/.dongjiaoshan-secrets/rds-prod.env（MASTER_DB_HOST_PUBLIC 等），
#       staging 读本仓库 .env.staging（MASTER_DB_URL/USERNAME/PASSWORD，已 gitignore）。
# 详细说明与注意事项见 ops/README.md「prod → staging 数据同步」。
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
require_env staging            # 只允许以 staging 为目标；env.map 里的 ECS / 容器都是 staging 的

EXECUTE=false; SWAP_ONLY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --execute) EXECUTE=true ;;
    --swap-only) SWAP_ONLY="${2:?--swap-only 需要上次运行的时间戳，如 20261125-050336}"; EXECUTE=true; shift ;;
  esac
  shift
done
REPO_DIR="$(cd "$OPS_DIR/.." && pwd)"
BACKUP_DIR="$HOME/dongjiaoshan-backups/db"
TS=${SWAP_ONLY:-$(date +%Y%m%d-%H%M%S)}
DB=dongjiaoshan
# staging 独有、必须保留的表（不是业务迁移建的：SnailJob 调度库、warm-flow 工作流、RuoYi 示例）
KEEP_STAGING_ONLY_REGEX='^(sj_|flow_|test_leave$)'
APP_CONTAINER=djs-ruoyi-admin-staging
REDIS_CONTAINER=djs-redis-staging
CT_DIR=/tmp/djs-sync-$TS       # dev-mysql 容器内的临时目录

die() { echo "⛔ $*" >&2; exit 1; }
log() { echo "[$(date +%H:%M:%S)] $*"; }

# ---------- 凭据 ----------
[ -f "$SECRETS_DIR/rds-prod.env" ] || die "缺 $SECRETS_DIR/rds-prod.env"
[ -f "$REPO_DIR/.env.staging" ] || die "缺 $REPO_DIR/.env.staging"
kv() { grep "^$2=" "$1" | cut -d= -f2-; }
P_HOST=$(kv "$SECRETS_DIR/rds-prod.env" MASTER_DB_HOST_PUBLIC)
P_PORT=$(kv "$SECRETS_DIR/rds-prod.env" MASTER_DB_PORT); P_PORT=${P_PORT:-3306}
P_USER=$(kv "$SECRETS_DIR/rds-prod.env" MASTER_DB_USERNAME)
P_PASS=$(kv "$SECRETS_DIR/rds-prod.env" MASTER_DB_PASSWORD)
S_URL=$(kv "$REPO_DIR/.env.staging" MASTER_DB_URL)
S_HOST=$(printf '%s' "$S_URL" | sed -E 's#jdbc:mysql://([^:/]+).*#\1#')
S_PORT=$(printf '%s' "$S_URL" | sed -E 's#jdbc:mysql://[^:/]+:?([0-9]*)/.*#\1#'); S_PORT=${S_PORT:-3306}
S_USER=$(kv "$REPO_DIR/.env.staging" MASTER_DB_USERNAME)
S_PASS=$(kv "$REPO_DIR/.env.staging" MASTER_DB_PASSWORD)
[ -n "$P_HOST" ] && [ -n "$S_HOST" ] || die "解析库地址失败"
case "$S_HOST" in *rm-bp12xt6ims04n8y6e*) die "staging 地址指向了 prod RDS，终止";; esac
case "$S_HOST" in *"$RDS_ID"*) ;; *) die "staging 地址 $S_HOST 与 env.map 的 staging RDS $RDS_ID 不符，终止";; esac

# 密码只经环境变量传进容器（`-e MYSQL_PWD` 不带值），不出现在任何进程的命令行里。
pq() { MYSQL_PWD="$P_PASS" docker exec -i -e MYSQL_PWD dev-mysql mysql --default-character-set=utf8mb4 -h "$P_HOST" -P "$P_PORT" -u "$P_USER" -N -B "$DB" -e "$1"; }
sq() { MYSQL_PWD="$S_PASS" docker exec -i -e MYSQL_PWD dev-mysql mysql --default-character-set=utf8mb4 -h "$S_HOST" -P "$S_PORT" -u "$S_USER" -N -B "$DB" -e "$1"; }

# mysqldump 先写进容器内文件再拷出：经 docker exec 标准输出流式传输大结果会卡死。
DUMP_OPTS="--default-character-set=utf8mb4 --single-transaction --quick --compression-algorithms=zlib --set-gtid-purged=OFF --no-tablespaces --routines --triggers --hex-blob"
dump_to() {  # dump_to <prod|staging> <本机输出 .sql.gz> [额外 mysqldump 参数...]（表名放最后）
  local which=$1 out=$2; shift 2
  local h p u pw
  if [ "$which" = prod ]; then h=$P_HOST; p=$P_PORT; u=$P_USER; pw=$P_PASS; else h=$S_HOST; p=$S_PORT; u=$S_USER; pw=$S_PASS; fi
  docker exec dev-mysql mkdir -p "$CT_DIR" || return 1
  MYSQL_PWD="$pw" docker exec -e MYSQL_PWD dev-mysql sh -c \
    "mysqldump $DUMP_OPTS -h '$h' -P '$p' -u '$u' --result-file='$CT_DIR/out.sql' '$DB' $* && gzip -f '$CT_DIR/out.sql'" || return 1
  docker cp "dev-mysql:$CT_DIR/out.sql.gz" "$out" >/dev/null || return 1
  docker exec dev-mysql rm -f "$CT_DIR/out.sql.gz"
}
import_to_staging() {  # import_to_staging <本机 .sql.gz>
  local f=$1 base rc; base=$(basename "$f")
  docker exec dev-mysql mkdir -p "$CT_DIR" || return 1
  docker cp "$f" "dev-mysql:$CT_DIR/$base" >/dev/null || return 1
  MYSQL_PWD="$S_PASS" docker exec -e MYSQL_PWD dev-mysql sh -c \
    "{ echo 'SET FOREIGN_KEY_CHECKS=0;'; gzip -dc '$CT_DIR/$base'; echo 'SET FOREIGN_KEY_CHECKS=1;'; } | mysql --default-character-set=utf8mb4 --compression-algorithms=zlib -h '$S_HOST' -P '$S_PORT' -u '$S_USER' '${IMPORT_DB:-$DB}'"
  rc=$?; docker exec dev-mysql rm -f "$CT_DIR/$base"; return $rc
}
# 逐表行数：一条 UNION ALL 查询，避免逐表连库
count_sql() { awk '{printf "%sselect \"%s\", count(*) from `%s`", (NR>1?" union all ":""), $1, $1}' "$1"; }

mkdir -p "$BACKUP_DIR"; chmod 700 "$HOME/dongjiaoshan-backups" "$BACKUP_DIR"

# ---------- 预检 ----------
log "预检：连通性与版本"
P_FW=$(pq "select max(version) from flyway_schema_history where success=1") || die "连不上 prod"
S_FW=$(sq "select max(version) from flyway_schema_history where success=1") || die "连不上 staging"
log "prod Flyway=$P_FW  staging Flyway=$S_FW"
log "staging 领先 prod、导入后会由后端补跑的迁移："
sq "select version, script from flyway_schema_history where success=1 and version > '$P_FW' order by installed_rank" | sed 's/^/    /'

pq "select table_name from information_schema.tables where table_schema='$DB' order by 1" > "$BACKUP_DIR/.p_tables"
sq "select table_name from information_schema.tables where table_schema='$DB' order by 1" > "$BACKUP_DIR/.s_tables"
DROP_TABLES=$(comm -13 "$BACKUP_DIR/.p_tables" "$BACKUP_DIR/.s_tables" | grep -Ev "$KEEP_STAGING_ONLY_REGEX" || true)
KEEP_TABLES=$(comm -13 "$BACKUP_DIR/.p_tables" "$BACKUP_DIR/.s_tables" | grep -E "$KEEP_STAGING_ONLY_REGEX" || true)
log "staging 独有、保留不动：$(echo $KEEP_TABLES | wc -w | tr -d ' ') 张（${KEEP_STAGING_ONLY_REGEX}）"
log "staging 独有、导入前删除（领先迁移会重建）："; for t in $DROP_TABLES; do echo "    $t"; done
ONLY_PROD=$(comm -23 "$BACKUP_DIR/.p_tables" "$BACKUP_DIR/.s_tables" || true)
[ -n "$ONLY_PROD" ] && log "prod 独有的表（会新建到 staging）：$ONLY_PROD"

# ---------- 1. 备份 ----------
S_BAK="$BACKUP_DIR/staging-$TS.sql.gz"; P_DUMP="$BACKUP_DIR/prod-$TS.sql.gz"; OSS_BAK="$BACKUP_DIR/staging-sys_oss_config-$TS.sql.gz"
if [ -z "$SWAP_ONLY" ]; then
log "备份 staging 全库 → $S_BAK"
dump_to staging "$S_BAK" || die "staging 备份失败"
log "导出 prod 全库（一致性快照，只读）→ $P_DUMP"
dump_to prod "$P_DUMP" || die "prod 导出失败"
for f in "$S_BAK" "$P_DUMP"; do
  gzip -t "$f" || die "$f 损坏"
  gzip -dc "$f" | tail -1 | grep -q "Dump completed" || die "$f 不完整"
done
log "保存 staging 环境专属行 sys_oss_config → $OSS_BAK"
dump_to staging "$OSS_BAK" --no-create-info --replace --skip-extended-insert sys_oss_config || die "sys_oss_config 导出失败"
chmod 600 "$S_BAK" "$P_DUMP" "$OSS_BAK"
# prod 逐表行数（与导出不在同一快照：prod 正在写入的表可能差几行）
pq "select table_name from information_schema.tables where table_schema='$DB' and table_type='BASE TABLE' order by 1" > "$BACKUP_DIR/.p_base"
pq "$(count_sql "$BACKUP_DIR/.p_base")" > "$BACKUP_DIR/prod-$TS.counts" || die "prod 行数统计失败"
ls -lh "$S_BAK" "$P_DUMP" | awk '{print "    "$5, $NF}'
log "prod 共 $(wc -l < "$BACKUP_DIR/prod-$TS.counts" | tr -d ' ') 张表、$(awk -F'\t' '{s+=$2} END{print s}' "$BACKUP_DIR/prod-$TS.counts") 行"

if [ "$EXECUTE" != true ]; then
  log "未带 --execute：只做了备份和计划，staging 未改动。"
  exit 0
fi
fi  # 备份段结束（--swap-only 跳过）

# ---------- 2. 导入临时库（后端照常运行） ----------
SYNC_DB=dongjiaoshan_sync; PRE_DB="dongjiaoshan_pre_$(echo "$TS" | tr -d '-')"
sqa() { MYSQL_PWD="$S_PASS" docker exec -i -e MYSQL_PWD dev-mysql mysql --default-character-set=utf8mb4 -h "$S_HOST" -P "$S_PORT" -u "$S_USER" -N -B -e "$1"; }
if [ -n "$SWAP_ONLY" ]; then
  # 接着上次中断的运行做换表：临时库须已导入完成
  [ -f "$OSS_BAK" ] || die "缺 ${OSS_BAK}（--swap-only 只能接 $TS 那次运行）"
  n=$(sqa "select count(*) from information_schema.tables where table_schema='$SYNC_DB'")
  log "--swap-only：沿用 $TS 导入的临时库 ${SYNC_DB}（$n 张表），跳过备份和导入"
  [ "$n" -gt 0 ] || die "临时库 $SYNC_DB 不存在或为空，不能只做换表"
else
log "建临时库 $SYNC_DB 并导入 prod（慢，staging 后端不停）"
sqa "DROP DATABASE IF EXISTS \`$SYNC_DB\`; CREATE DATABASE \`$SYNC_DB\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" || die "建临时库失败"
IMPORT_DB=$SYNC_DB import_to_staging "$P_DUMP" || die "导入临时库失败（staging 未受影响）"
log "核对临时库逐表行数（vs prod）"
sqa "$(count_sql "$BACKUP_DIR/.p_base" | sed "s/from \`/from \`$SYNC_DB\`.\`/g")" > "$BACKUP_DIR/staging-after-$TS.counts" || die "临时库行数统计失败"
BAD=$(awk -F'\t' 'NR==FNR{p[$1]=$2; next} {if (p[$1]!=$2) {print "    ✗ "$1" prod="p[$1]" sync="$2 > "/dev/stderr"; n++}} END{print n+0}' \
  "$BACKUP_DIR/prod-$TS.counts" "$BACKUP_DIR/staging-after-$TS.counts")
[ "$BAD" = 0 ] && log "    全部 $(wc -l < "$BACKUP_DIR/prod-$TS.counts" | tr -d ' ') 张表行数一致" || log "    $BAD 张表行数不一致（见上；prod 导出后仍有写入时属正常）"
fi  # 导入段结束

# ---------- 3. 换表（停机窗口） ----------
MOVE_OUT=$( { cat "$BACKUP_DIR/.p_tables"; echo "$DROP_TABLES"; } | grep -v '^$' | sort -u | grep -Fxf "$BACKUP_DIR/.s_tables" || true)
SYNC_TABLES=$(sqa "select table_name from information_schema.tables where table_schema='$SYNC_DB' order by 1")
RENAME="RENAME TABLE"; sep=" "; ROLLBACK="RENAME TABLE"; rsep=" "
for t in $MOVE_OUT; do RENAME="$RENAME$sep\`dongjiaoshan\`.\`$t\` TO \`$PRE_DB\`.\`$t\`"; sep=", "; done
for t in $SYNC_TABLES; do RENAME="$RENAME$sep\`$SYNC_DB\`.\`$t\` TO \`dongjiaoshan\`.\`$t\`"; sep=", "; done
for t in $SYNC_TABLES; do ROLLBACK="$ROLLBACK$rsep\`dongjiaoshan\`.\`$t\` TO \`$SYNC_DB\`.\`$t\`"; rsep=", "; done
for t in $MOVE_OUT; do ROLLBACK="$ROLLBACK$rsep\`$PRE_DB\`.\`$t\` TO \`dongjiaoshan\`.\`$t\`"; rsep=", "; done
printf '%s;\n' "$ROLLBACK" > "$BACKUP_DIR/rollback-$TS.sql"; chmod 600 "$BACKUP_DIR/rollback-$TS.sql"
log "回滚语句已写到 $BACKUP_DIR/rollback-$TS.sql（停后端后执行，再起后端）"

log "停 staging 后端容器 $APP_CONTAINER"
run_on_server "docker stop $APP_CONTAINER >/dev/null; docker ps -a --filter name=$APP_CONTAINER --format '{{.Status}}'"
log "换表：staging 原表 → ${PRE_DB}，临时库 → dongjiaoshan"
sqa "CREATE DATABASE \`$PRE_DB\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; SET FOREIGN_KEY_CHECKS=0; $RENAME;" \
  || { log "换表失败，重新起后端"; run_on_server "docker start $APP_CONTAINER >/dev/null"; die "换表失败（RENAME 是原子的，staging 未变）"; }
sqa "DROP DATABASE \`$SYNC_DB\`" || true

log "恢复 staging 的 sys_oss_config"
import_to_staging "$OSS_BAK" || die "恢复 sys_oss_config 失败（后端仍停着）"
B=$(sq "select bucket_name from sys_oss_config where config_key='aliyun'")
[ "$B" = "$OSS_BUCKET" ] || die "sys_oss_config 桶名=${B}，不是 ${OSS_BUCKET}；修正后再起后端"

log "清 staging Redis（全部 key：字典、配置、OSS 配置、登录态）"
run_on_server "PW=\$(docker exec $REDIS_CONTAINER printenv REDIS_PASSWORD 2>/dev/null); docker exec $REDIS_CONTAINER redis-cli \${PW:+-a \"\$PW\"} --no-auth-warning FLUSHALL"

log "起 staging 后端（Flyway 补跑领先迁移）"
run_on_server "docker start $APP_CONTAINER >/dev/null && echo started"
st=""
for _ in $(seq 1 40); do
  st=$(run_on_server "docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $APP_CONTAINER")
  case "$st" in *unhealthy*|*exited*) break ;; *healthy*) break ;; esac
  sleep 15
done
log "后端状态：$st"
run_on_server "docker logs --since 20m $APP_CONTAINER 2>&1 | grep -E 'Successfully applied|Migrating schema|Started .* in|FlywayException|APPLICATION FAILED' | tail -20"
log "staging Flyway 现在：$(sq "select max(version) from flyway_schema_history where success=1")（导入前 staging=${S_FW}，prod=${P_FW}）"
log "完成。staging 备份：$S_BAK  prod 导出：$P_DUMP"
log "staging 换下来的原表在库 ${PRE_DB}（确认无误后可 DROP DATABASE；回滚语句 $BACKUP_DIR/rollback-$TS.sql）"
