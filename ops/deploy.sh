#!/usr/bin/env bash
# ops/deploy.sh <staging|prod> <app|admin|trace> [--yes]
# 触发 GitHub Actions 部署工作流并跟踪。prod 强制人工闸（--yes + 已获 Kevin "go"）。
# 依赖 gh CLI 已登录（gh auth login）。
source "$(cd "$(dirname "$0")" && pwd)/lib.sh"
require_env "${1:-}"
TARGET="${2:-app}"; YES=false
for a in "${@:3}"; do [ "$a" = "--yes" ] && YES=true; done

case "$TARGET" in
  app)   REPO=RuoYi-Vue-Plus ;;
  admin) REPO=plus-ui ;;
  trace) REPO=trace-h5 ;;
  *) echo "target 取值: app | admin | trace" >&2; exit 2 ;;
esac
WF="deploy-$DJS_ENV.yml"
SLUG="dongjiaoshan/$REPO"

if [ "$DJS_ENV" = "prod" ] && [ "$YES" != true ]; then
  echo "⛔ prod 部署是人工闸操作。确认已获 Kevin \"go\" 后，加 --yes 重跑："
  echo "   bash ops/deploy.sh prod $TARGET --yes"
  exit 3
fi

echo "==> gh workflow run $WF (-R $SLUG --ref $GH_REF)"
gh workflow run "$WF" -R "$SLUG" --ref "$GH_REF" || { echo "触发失败（gh 是否已登录？）" >&2; exit 1; }
sleep 4
RUN_ID=$(gh run list -R "$SLUG" -w "$WF" -L 1 --json databaseId -q '.[0].databaseId' 2>/dev/null)
[ -n "$RUN_ID" ] && gh run watch "$RUN_ID" -R "$SLUG" --exit-status || echo "（无法跟踪 run，去 GitHub Actions 页面看）"
echo "==> 部署后自检："
bash "$OPS_DIR/status.sh" "$DJS_ENV" 2>/dev/null | sed -n '/机内/,/RDS/p'
