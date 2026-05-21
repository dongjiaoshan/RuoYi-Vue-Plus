#!/usr/bin/env bash
# ============================================================================
# djs 初始化后置脚本 —— 在跑完 script/sql/djs/V*.sql 之后、首次启动 admin 之前执行
#
# 为什么需要：
#   D01 SYS-CLEANUP 阶段（V202605201500~V202605201700）会 DELETE / TRUNCATE
#   多张 sys_* 表（含 sys_dict_data / sys_dict_type / sys_user / sys_notice 等）。
#   如果 admin 在这之前已经启过（哪怕 1 次），ruoyi 自带 Redisson 字典缓存
#   会把"查空"的结果按 NullValue 写到 redis（hash key 形如 `1001:sys_dict`），
#   TTL 1h。即使 SQL 把数据重新灌回去，缓存仍返 NullValue 直到自然过期，
#   表现为 admin 表单字典下拉空（典型：性别 / 启用停用 / 显示隐藏）。
#
# 解决：清掉 djs 默认字典 hash，让下次访问重读 DB。
#
# 用法：
#   bash script/sql/djs/_post-init.sh
#
# 环境变量（可覆盖）：
#   REDIS_CONTAINER  默认 dev-redis
#   REDIS_PASSWORD   默认 ruoyi123（与 script/docker/redis/conf/redis.conf 对齐）
# ============================================================================

set -e

REDIS_CONTAINER="${REDIS_CONTAINER:-dev-redis}"
REDIS_PASSWORD="${REDIS_PASSWORD:-ruoyi123}"

echo "[djs-post-init] 清 ruoyi 字典缓存（避免 NullValue 粘滞）..."
docker exec "$REDIS_CONTAINER" redis-cli -a "$REDIS_PASSWORD" --no-auth-warning \
  --scan --pattern '*:sys_dict' \
  | xargs -I {} docker exec "$REDIS_CONTAINER" redis-cli -a "$REDIS_PASSWORD" --no-auth-warning DEL {} \
  || true

echo "[djs-post-init] 完成。"
