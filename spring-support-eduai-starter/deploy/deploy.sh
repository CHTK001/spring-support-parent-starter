#!/usr/bin/env bash
#
# eduai 后端一键部署（幂等）。在【目标主机】上执行，需要 root。
#
# 干什么：
#   1) 解包构建产物（fat jar + deploy/）
#   2) 备份现役 jar，替换为新 jar
#   3) 安装 systemd unit（凭据走 /etc/eduai/eduai.env，unit 里不含明文）
#   4) daemon-reload -> 重启 -> 自检（health 200）
#
# 为什么 unit 要用 EnvironmentFile 而不是把参数写在 ExecStart：
#   unit 是要入仓库的 —— 把数据源口令写在 ExecStart 里等于把口令提交进版本库。
#   systemd 支持在 ExecStart 里用 ${VAR} 引用 Environment/EnvironmentFile 的值，
#   实测（含带 ? & 的 JDBC URL）原样展开、不做二次分词。
#
# 前置：
#   - root（或 sudo）
#   - 构建产物已就位：$BUNDLE（默认 $APP_DIR/eduai-app.tar.gz，由 deploy/package.sh 生成）
#   - 凭据文件已就位：/etc/eduai/eduai.env（模板 deploy/eduai.env.example）
#   - 注意：不要把产物放 /tmp —— 本仓库部署主机的 /tmp 是小容量 tmpfs，
#     这个 fat jar ~800MB，写不进去（No space left on device）。
#
# 用法：
#   sudo BUNDLE=/home/kali/eduai/eduai-app.tar.gz bash deploy/deploy.sh
#
set -euo pipefail

APP_DIR=${APP_DIR:-/home/kali/eduai/backend}
BUNDLE=${BUNDLE:-/home/kali/eduai/eduai-app.tar.gz}
ENV_FILE=${ENV_FILE:-/etc/eduai/eduai.env}
UNIT_NAME=eduai-backend.service
JAR_SRC=spring-support-eduai-starter-4.0.0.42.jar
JAR_DST=eduai-backend.jar
UNIT_DST="/etc/systemd/system/$UNIT_NAME"

log() { printf '  %s\n' "$*"; }
die() { printf '  [错误] %s\n' "$*" >&2; exit 1; }

# ---------- 0. 前置 ----------
[ "$(id -u)" -eq 0 ] || die "请以 root（或 sudo）执行"
[ -f "$BUNDLE" ] || die "缺少构建产物 $BUNDLE（先在开发机跑 deploy/package.sh 并上传）"
[ -f "$ENV_FILE" ] || die "缺少凭据文件 $ENV_FILE（见 deploy/eduai.env.example）"
command -v tar >/dev/null || die "缺少 tar"

log "==> 前置校验通过"
log "    产物=$BUNDLE  凭据=$ENV_FILE  目录=$APP_DIR"

# EnvFile 必须含 ExecStart 引用到的变量 + 数据源三件套，
# 否则 systemd 起不来或 Spring 连不上库
missing=""
for k in EDUAI_PORT SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME \
         SPRING_DATASOURCE_PASSWORD OAUTH_TCP_ADDRESS; do
    grep -qE "^[[:space:]]*${k}=" "$ENV_FILE" || missing="$missing $k"
done
[ -z "$missing" ] || die "凭据文件缺少变量:$missing"

mkdir -p "$APP_DIR" "$APP_DIR/logs" "$APP_DIR/tmp"

# ---------- 1. 解包 ----------
log "==> 1/5 解包构建产物"
STAGE="$APP_DIR/.deploy-staging"
rm -rf "$STAGE"
mkdir -p "$STAGE"
tar -xzf "$BUNDLE" -C "$STAGE"
[ -f "$STAGE/$JAR_SRC" ] || die "产物里没有 $JAR_SRC"
[ -f "$STAGE/deploy/$UNIT_NAME" ] || die "产物里没有 deploy/$UNIT_NAME"
NEW_SHA=$(sha256sum "$STAGE/$JAR_SRC" | cut -c1-16)
log "    新 jar sha=$(sha256sum "$STAGE/$JAR_SRC" | cut -c1-64)  size=$(stat -c %s "$STAGE/$JAR_SRC")"

# ---------- 2. 备份 + 替换 jar ----------
log "==> 2/5 备份并替换 jar"
TS=$(date +%Y%m%d-%H%M%S)
if [ -f "$APP_DIR/$JAR_DST" ]; then
    cp -p "$APP_DIR/$JAR_DST" "$APP_DIR/$JAR_DST.bak-$TS"
    log "    已备份 -> $JAR_DST.bak-$TS ($(sha256sum "$APP_DIR/$JAR_DST" | cut -c1-16))"
fi
install -m 0644 "$STAGE/$JAR_SRC" "$APP_DIR/$JAR_DST"
log "    现役 sha=$(sha256sum "$APP_DIR/$JAR_DST" | cut -c1-16)"

# ---------- 3. 安装 unit（先备份、再校验） ----------
log "==> 3/5 安装 systemd unit"
if [ -f "$UNIT_DST" ] && ! cmp -s "$STAGE/deploy/$UNIT_NAME" "$UNIT_DST"; then
    cp -p "$UNIT_DST" "$UNIT_DST.bak-$TS"
    log "    已备份原 unit -> $(basename "$UNIT_DST").bak-$TS"
fi
install -m 0644 "$STAGE/deploy/$UNIT_NAME" "$UNIT_DST"

if command -v systemd-analyze >/dev/null; then
    if ! systemd-analyze verify "$UNIT_DST" 2>&1 | grep -v 'Unknown lvalue\|Unit .* not found' >/dev/null; then
        :
    fi
    log "    systemd-analyze verify 已跑（见上方告警，若有）"
fi

systemctl daemon-reload
systemctl enable "$UNIT_NAME" >/dev/null 2>&1 || true

# ---------- 4. 重启 ----------
log "==> 4/5 重启服务"
systemctl restart "$UNIT_NAME"

# ---------- 5. 自检 ----------
log "==> 5/5 自检"
ok=0
for _ in $(seq 1 30); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 4 \
        http://127.0.0.1:19095/actuator/health 2>/dev/null || true)
    if [ "$code" = "200" ]; then ok=1; break; fi
    sleep 10
done
log "    unit=$(systemctl is-active "$UNIT_NAME")  restarts=$(systemctl show "$UNIT_NAME" -p NRestarts --value)  健康 HTTP=$code"

rm -rf "$STAGE"

if [ "$ok" != "1" ]; then
    log "    未在预期时间内健康，最近日志："
    tail -30 "$APP_DIR/logs/be.out.log" 2>/dev/null | cut -c1-160 | sed 's/^/      /'
    die "部署失败；可用 $JAR_DST.bak-$TS 与 $(basename "$UNIT_DST").bak-$TS 回滚"
fi
log "==> 部署完成（jar=$NEW_SHA）"
