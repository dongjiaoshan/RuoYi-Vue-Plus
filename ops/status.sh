#!/usr/bin/env bash
# ops/status.sh <staging|prod> — 一屏查全部（只读，随时安全）。
# 用法: bash ops/status.sh prod
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
require_env "${1:-}"

echo "════════ djs status: $DJS_ENV  ($ECS_IP)  $(date '+%F %T') ════════"

echo "── ECS $ECS_ID ──"
aliyun ecs DescribeInstances --profile "$PROFILE" --RegionId "$REGION" --InstanceIds "[\"$ECS_ID\"]" 2>&1 \
  | jq -r '.Instances.Instance[0] | "  status=\(.Status)  cpu=\(.Cpu)C mem=\(.Memory)MB  expire=\(.ExpiredTime)"' 2>&1 | head -2
aliyun cms DescribeMetricLast --profile "$PROFILE" --Namespace acs_ecs_dashboard --MetricName CPUUtilization \
  --Dimensions "[{\"instanceId\":\"$ECS_ID\"}]" 2>/dev/null \
  | jq -r '.Datapoints | fromjson? | .[0] | "  CPU≈\(.Average|floor)%"' 2>/dev/null | head -1

echo "── 机内: mem / disk / docker / 后端健康 / nginx / redis ──"
run_on_server '
  echo -n "  [mem] "; free -m | awk "NR==2{print \$3\"/\"\$2\" MB\"}"
  echo -n "  [disk] "; df -h / | awk "NR==2{print \$3\"/\"\$2\" (\"\$5\")\"}"
  echo "  [docker]"; docker ps --format "    {{.Names}}\t{{.Status}}" | grep djs- || echo "    (无 djs 容器)"
  echo -n "  [backend] "; curl -s --max-time 5 http://127.0.0.1:8080/actuator/health/liveness 2>/dev/null || echo "无响应"
  echo ""
  echo -n "  [nginx] "; (systemctl is-active nginx 2>/dev/null || echo "n/a")
  PW=$(docker exec djs-redis-'"$DJS_ENV"' printenv REDIS_PASSWORD 2>/dev/null)
  echo -n "  [redis] "; docker exec djs-redis-'"$DJS_ENV"' redis-cli -a "$PW" --no-auth-warning info memory 2>/dev/null | grep used_memory_human | tr -d "\r" || echo "n/a"
'

echo "── RDS $RDS_ID ──"
aliyun rds DescribeDBInstanceAttribute --profile "$PROFILE" --DBInstanceId "$RDS_ID" 2>&1 \
  | jq -r '.Items.DBInstanceAttribute[0] | "  status=\(.DBInstanceStatus) class=\(.DBInstanceClass) \(.DBInstanceCPU)C/\(.DBInstanceMemory)MB storage=\(.DBInstanceStorage)GB"' 2>&1 | head -2
aliyun cms DescribeMetricLast --profile "$PROFILE" --Namespace acs_rds_dashboard --MetricName ConnectionUsage \
  --Dimensions "[{\"instanceId\":\"$RDS_ID\"}]" 2>/dev/null \
  | jq -r '.Datapoints | fromjson? | .[0] | "  连接使用率≈\(.Average|floor)%"' 2>/dev/null | head -1

echo "── OSS 桶 ($OSS_PREFIX-*) ──"
CFG=$(ensure_ossutil_config)
if [ -n "$CFG" ]; then
  for b in "$OSS_PREFIX-private" "$OSS_PREFIX-public" "$OSS_PREFIX-trace"; do
    acl=$(aliyun oss stat "oss://$b" -c "$CFG" 2>/dev/null | awk -F': *' '/^ACL /{print $2}')
    echo "  $b: ${acl:-不存在/不可读}"
  done
else
  echo "  (ossutil 配置缺失，跳过)"
fi

echo "── 证书到期 ──"
run_on_server '
  for d in '"$DOMAINS"'; do
    echo -n "  $d: "
    echo | timeout 5 openssl s_client -servername "$d" -connect "$d":443 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2 || echo "无证书/未解析"
  done'
echo "════════ end ════════"
