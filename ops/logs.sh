#!/usr/bin/env bash
# ops/logs.sh <staging|prod> [行数] — 拉后端容器日志（只读）。
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
require_env "${1:-}"
N="${2:-100}"
run_on_server "docker logs --tail $N djs-ruoyi-admin-$DJS_ENV 2>&1 | tail -$N"
