#!/usr/bin/env bash
# ops/lib.sh — djs 运维 runbook 共享库。被 status.sh / deploy.sh / logs.sh source。
# 认证：aliyun CLI profile djs-staging / djs-prod（RAM 用户 claude-deployer，最小权限）。
# 服务器操作：走 ECS Cloud Assistant RunCommand（零入站端口、全 ActionTrail 审计）。
set -uo pipefail

# 本机 VPN 代理（127.0.0.1:1081）会劫持 aliyun/oss API 调用致超时，必须绕过。
export no_proxy='*' NO_PROXY='*'

REGION=cn-hangzhou
OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRETS_DIR="$HOME/.dongjiaoshan-secrets"

require_env() {
  DJS_ENV="${1:-}"
  case "$DJS_ENV" in
    staging|prod) ;;
    *) echo "用法: $(basename "$0") {staging|prod}" >&2; exit 2 ;;
  esac
  # shellcheck source=/dev/null
  source "$OPS_DIR/env.map"
  PROFILE="djs-$DJS_ENV"
}

# run_on_server <shell命令字符串> — 在本环境 ECS 上执行，打印解码后的 stdout。
# 只读命令随时安全；写命令（尤其 prod）调用方需先取得 Kevin "go"。
run_on_server() {
  local cmd="$1" invoke st
  invoke=$(aliyun ecs RunCommand --profile "$PROFILE" --RegionId "$REGION" \
    --InstanceId.1 "$ECS_ID" --Type RunShellScript --Timeout 60 --ContentEncoding Base64 \
    --CommandContent "$(printf '%s' "$cmd" | base64)" 2>&1 | jq -r '.InvokeId // empty')
  if [ -z "$invoke" ]; then echo "[run_on_server] RunCommand 发起失败" >&2; return 1; fi
  for _ in $(seq 1 30); do
    st=$(aliyun ecs DescribeInvocationResults --profile "$PROFILE" --RegionId "$REGION" \
         --InstanceId "$ECS_ID" --InvokeId "$invoke" 2>&1 \
         | jq -r '.Invocation.InvocationResults.InvocationResult[0].InvocationStatus // "?"')
    case "$st" in Success|Finished|Failed|Timeout|Error|Cancelled) break ;; esac
    sleep 2
  done
  aliyun ecs DescribeInvocationResults --profile "$PROFILE" --RegionId "$REGION" \
    --InstanceId "$ECS_ID" --InvokeId "$invoke" 2>&1 \
    | jq -r '.Invocation.InvocationResults.InvocationResult[0].Output // ""' | base64 -d 2>/dev/null
}

# 按需从 secrets 生成 ossutil 配置（不回显密钥）。用于 OSS 桶容量统计。
ensure_ossutil_config() {
  local cfg="$HOME/.ossutilconfig-$PROFILE" src="$SECRETS_DIR/$PROFILE.env"
  if [ -f "$cfg" ]; then echo "$cfg"; return 0; fi
  [ -f "$src" ] || { echo "" ; return 1; }
  local id sec
  id=$(grep '^ALIBABA_CLOUD_ACCESS_KEY_ID='     "$src" | cut -d= -f2-)
  sec=$(grep '^ALIBABA_CLOUD_ACCESS_KEY_SECRET=' "$src" | cut -d= -f2-)
  printf '[Credentials]\nlanguage=EN\nendpoint=oss-%s.aliyuncs.com\naccessKeyID=%s\naccessKeySecret=%s\n' \
    "$REGION" "$id" "$sec" > "$cfg"
  chmod 600 "$cfg"
  echo "$cfg"
}
