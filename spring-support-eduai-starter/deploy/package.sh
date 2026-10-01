#!/usr/bin/env bash
#
# eduai 部署产物打包（在【开发机】执行）。
#
# 产出 dist/eduai-app.tar.gz，内含：
#   spring-support-eduai-starter-<ver>.jar   # fat jar（已 repackage，含全部依赖）
#   deploy/                                  # 部署脚本与 systemd unit
#
# 上传并部署（示例）：
#   pscp -batch -pw <pass> dist/eduai-app.tar.gz <user>@<host>:/home/<user>/eduai/eduai-app.tar.gz
#   ssh <user>@<host> "sudo BUNDLE=/home/<user>/eduai/eduai-app.tar.gz bash /home/<user>/eduai/backend/deploy/deploy.sh"
#
# 首次部署时 app 目录里的 deploy.sh 还不存在，用上传的那份执行即可。
# 注意：产物 ~800MB，不要放 /tmp（本仓库部署主机的 /tmp 是小容量 tmpfs）。
#
set -euo pipefail

MODULE_DIR=$(cd "$(dirname "$0")/.." && pwd)
DIST="$MODULE_DIR/dist"
JAR=spring-support-eduai-starter-4.0.0.42.jar

echo "==> 构建 $JAR（fat jar，耗时较长）"
mvn -f "$MODULE_DIR/pom.xml" clean package -DskipTests

[ -f "$MODULE_DIR/target/$JAR" ] || { echo "[错误] 构建未产出 $JAR" >&2; exit 1; }

echo "==> 打包"
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
cp "$MODULE_DIR/target/$JAR" "$STAGE/"
cp -r "$MODULE_DIR/deploy" "$STAGE/"

mkdir -p "$DIST"
tar -czf "$DIST/eduai-app.tar.gz" -C "$STAGE" .

echo "==> 完成：$DIST/eduai-app.tar.gz"
if command -v sha256sum >/dev/null; then
  echo "    sha256=$(sha256sum "$DIST/eduai-app.tar.gz" | cut -c1-64)"
fi
echo "    大小=$(du -h "$DIST/eduai-app.tar.gz" | cut -f1)"
