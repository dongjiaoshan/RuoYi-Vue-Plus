#!/usr/bin/env bash
# ops/redis-flush-dict.sh <staging|prod> [--yes]
# 刷 redis 缓存（*:sys_dict + *djs:dict* + *sys_nickname* + *sys_client*）。改动操作，prod 需 --yes + Kevin "go"。
# 灌种子 / 用 SQL 改过 字典·用户昵称·客户端配置 之后都要跑一次，否则页面拿到旧快照（昵称与客户端配置 TTL 30 天）。
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
require_env "${1:-}"
YES=false; for a in "${@:2}"; do [ "$a" = "--yes" ] && YES=true; done
if [ "$DJS_ENV" = "prod" ] && [ "$YES" != true ]; then
  echo "⛔ prod 改动操作。确认已获 Kevin \"go\"，加 --yes 重跑。"; exit 3
fi
# 部署目录 cutover 前后可能不同，逐个候选试。
run_on_server '
  for d in '"$DEPLOY_DIR"' /www/wwwroot/api.dongjiaoshan.com /opt/dongjiaoshan-'"$DJS_ENV"'; do
    [ -f "$d/script/sql/djs/_post-init.sh" ] || continue
    cd "$d"
    PW=$(docker exec djs-redis-'"$DJS_ENV"' printenv REDIS_PASSWORD 2>/dev/null)
    REDIS_CONTAINER=djs-redis-'"$DJS_ENV"' REDIS_PASSWORD="$PW" bash script/sql/djs/_post-init.sh
    exit 0
  done
  echo "未找到 _post-init.sh，检查部署目录" >&2; exit 1
'
