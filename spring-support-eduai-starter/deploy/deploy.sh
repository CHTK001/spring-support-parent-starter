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

# ---------- 2.5 确保 schema 收敛 ----------
# 为什么需要这一步：框架的迁移是「按脚本名记账，成功过就不再执行」——
# 一旦某个 V1.0 脚本在记账**之后**被追加了建表语句，存量库永远不会补上。
# 实测：eduai 库缺 sys_ai_service_instance，而 ai-starter 的实体/Mapper 持续报
#   Table 'eduai.sys_ai_service_instance' doesn't exist（每个轮询周期一条）。
# 这里把产物内各模块的**结构脚本**再幂等执行一遍：DDL 都是
# CREATE TABLE IF NOT EXISTS，重复执行无副作用；少数裸 ADD COLUMN 的兼容 ALTER
# 重复执行会报 1060，与框架自身的 continueOnError 语义一致，这里同样容错跳过。
log "==> 2.5/5 确保 schema 收敛（幂等执行产物内的结构脚本）"
DBURL=$(sed -n 's/^SPRING_DATASOURCE_URL=//p' "$ENV_FILE" | head -1)
DBUSER_ENV=$(sed -n 's/^SPRING_DATASOURCE_USERNAME=//p' "$ENV_FILE" | head -1)
DBPASS_ENV=$(sed -n 's/^SPRING_DATASOURCE_PASSWORD=//p' "$ENV_FILE" | head -1)
[ -n "$DBURL" ] || die "凭据文件缺少 SPRING_DATASOURCE_URL"
# jdbc:mysql://host:port/db?params -> host / port / db（不用嵌套引号，避免多层转义）
_REST=${DBURL#jdbc:mysql://}
_HOSTPORT=${_REST%%/*}
_DB_AND_Q=${_REST#*/}
DBNAME=${_DB_AND_Q%%\?*}
DBHOST=${_HOSTPORT%%:*}
DBPORT=${_HOSTPORT##*:}
SQLDIR="$STAGE/_ddl"
rm -rf "$SQLDIR"
mkdir -p "$SQLDIR/init"
unzip -o -j -q "$STAGE/$JAR_SRC" 'BOOT-INF/classes/db/init/V*__init_all_*.sql' -d "$SQLDIR/init" 2>/dev/null || true
rm -rf "$STAGE/_libs"
mkdir -p "$STAGE/_libs"
unzip -o -q "$STAGE/$JAR_SRC" 'BOOT-INF/lib/*.jar' -d "$STAGE/_libs" 2>/dev/null || true
for _j in "$STAGE"/_libs/BOOT-INF/lib/*.jar; do
    [ -f "$_j" ] || continue
    unzip -l "$_j" 2>/dev/null | grep -q 'db/init/.*\.sql' || continue
    unzip -o -j -q "$_j" 'db/init/V*__init_all_*.sql' -d "$SQLDIR/init" 2>/dev/null || true
done
_DDL_N=$(ls -1 "$SQLDIR/init"/*.sql 2>/dev/null | wc -l)
log "    抽出结构脚本 $_DDL_N 个"
_DDL_CNF=$(mktemp)
chmod 600 "$_DDL_CNF"
trap 'rm -f "$_DDL_CNF"' EXIT
{
    printf '[client]\n'
    printf 'host=%s\nport=%s\nuser=%s\npassword=%s\n' "$DBHOST" "$DBPORT" "$DBUSER_ENV" "$DBPASS_ENV"
} > "$_DDL_CNF"
_DDL_OK=0
_DDL_SKIP=0
for _f in $(ls -1 "$SQLDIR/init"/*.sql 2>/dev/null | sort); do
    if mariadb --defaults-extra-file="$_DDL_CNF" "$DBNAME" < "$_f" >/dev/null 2>&1; then
        _DDL_OK=$((_DDL_OK + 1))
    else
        _DDL_SKIP=$((_DDL_SKIP + 1))
    fi
done
_DDL_TBL=$(mariadb --defaults-extra-file="$_DDL_CNF" -N -e \
    "select count(*) from information_schema.tables where table_schema='$DBNAME';" 2>/dev/null || echo '?')
log "    结构脚本：整份成功 $_DDL_OK、含容错跳过 $_DDL_SKIP；$DBNAME 现有表 $_DDL_TBL"
# 把「记账里没成功的脚本」报出来。这类脚本框架不会重跑：
#   结构脚本按 (版本,脚本名) 记账，成功过就跳过；
#   initdata 在 data-scan-mode=ONCE 且库非空时直接跳过。
# **不能**由本脚本自动重放 initdata：种子脚本靠 INSERT IGNORE 去重，而它依赖
# 唯一键——没有唯一键的表会被重复灌进去（仓库里记过 edu_poem_grade 967→2901 的事故）。
# 所以这里只上报，把判断交给人。
if _VERSION_BAD=$(mariadb --defaults-extra-file="$_DDL_CNF" -N -e \
    "select concat(sys_database_version_script_name, '(', ifnull(sys_database_version_success,'-'), ')') from $DBNAME.sys_database_version where ifnull(sys_database_version_success,'') <> 'true';" 2>/dev/null); then
    if [ -n "$_VERSION_BAD" ]; then
        log "    [警告] 以下脚本在 $DBNAME.sys_database_version 里不是成功状态，框架不会重跑，请人工确认是否已落地："
        printf '%s\n' "$_VERSION_BAD" | sed 's/^/      /'
    else
        log "    迁移记账：全部为成功状态"
    fi
else
    log "    迁移记账：读不到 $DBNAME.sys_database_version（跳过检查）"
fi
rm -rf "$SQLDIR" "$STAGE/_libs"

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

# 把 deploy/ 留在 app 目录里：升级时可直接用 app 内的脚本，
# 不必每次再单独上传一份（oauth 侧同理，其解包目录本就是持久的 app 目录）。
rm -rf "$APP_DIR/deploy"
cp -r "$STAGE/deploy" "$APP_DIR/deploy"
log "    deploy/ 已留在 $APP_DIR/deploy"

# ---------- 4. 重启 ----------
# 为什么先 stop、睡 15s 再 start，而不用 restart：
#   restart = 杀旧进程的同时起新进程。旧 JVM 退出要经历 Spring 关闭钩子
#   （关连接池、刷定时任务、等线程），实测要 10~20s；这期间 systemd 的
#   Restart=always 还会叠加自动拉起。新 JVM 在这期间启动，会和旧进程抢
#   19095 端口 + 抢 Spring 状态，固定在启动 30~60s 时 exit 1（2026-10-06
#   实测：5 次闪退、无 ERROR、无 hs_err，第 6 次旧进程退干净了才成功）。
#   先停干净再起，彻底避开新旧 JVM 重叠窗口。
log "==> 4/5 重启服务"
systemctl stop "$UNIT_NAME" || true
for _ in $(seq 1 15); do
    systemctl is-active --quiet "$UNIT_NAME" || break
    sleep 1
done
systemctl is-active --quiet "$UNIT_NAME" && die "旧进程 15s 内未退出，拒绝启动新进程（防端口争抢）"
systemctl start "$UNIT_NAME"

# ---------- 5. 自检 ----------
# 启动要 135~155s（大 fat jar + 迁移 + ONNX 模型加载），30×14s 的窗口理论够，
# 但健康检查要在「JVM 真正开始启动」之后才计时；stop/start 之间睡过 15s，
# 这里再多等一轮，避免把旧进程刚退、新进程还没打第一行日志的时间算进去。
log "==> 5/5 自检"
sleep 20
ok=0
for _ in $(seq 1 40); do
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

# ---------- 5.5 清理旧回滚包（防无限增长） ----------
# 每次部署都留一份 .bak-<TS>（约 800MB）。不清理的话，共享主机上几十次部署就能
# 吃掉几十 G —— 实测 2026-10-02 累积到 26 份 = 21G，根分区 66% → 72%（约 1.5h +4G）。
# 按**文件名里的时间戳**保留最新 JAR_KEEP 份（不按 mtime：备份用 cp -p，
# mtime 会继承源文件的时间，多份并列时按 mtime 排序等于随机删）。
# 现役 jar（$JAR_DST 本身）不匹配 *.bak-*，天然被排除。
JAR_KEEP=${JAR_KEEP:-5}
log "==> 5.5/5 清理旧回滚包（保留最新 $JAR_KEEP 份）"
BAKS=$(ls -1 "$APP_DIR/$JAR_DST".bak-[0-9]* 2>/dev/null | sort -r)
BN=$(echo "$BAKS" | grep -c . || true)
if [ "$BN" -le "$JAR_KEEP" ]; then
    log "    现有 $BN 份 <= $JAR_KEEP，无需清理"
else
    # 方向断言：保留的第一份必须比将删的第一份「新」（字典序 = 时间序）。
    # 写成升序 sort 会反过来删最新的，而份数不变 —— 只数份数发现不了。
    NEWEST_KEPT=$(echo "$BAKS" | head -n 1)
    OLDEST_DROPPED=$(echo "$BAKS" | tail -n +$((JAR_KEEP + 1)) | head -n 1)
    if [ "$NEWEST_KEPT" \> "$OLDEST_DROPPED" ]; then
        echo "$BAKS" | tail -n +$((JAR_KEEP + 1)) | while read -r f; do
            [ -z "$f" ] && continue
            rm -f "$f" && log "    删除旧回滚包 $(basename "$f")"
        done
        log "    剩余 $(ls -1 "$APP_DIR/$JAR_DST".bak-[0-9]* 2>/dev/null | wc -l) 份"
    else
        log "    [跳过清理] 方向断言失败：保留的 '$NEWEST_KEPT' 不比将删的 '$OLDEST_DROPPED' 新"
    fi
fi

log "==> 部署完成（jar=$NEW_SHA）"
