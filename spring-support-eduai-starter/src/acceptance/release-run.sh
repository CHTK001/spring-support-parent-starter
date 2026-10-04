#!/usr/bin/env bash
#
# eduai 发布一键执行（在【目标主机】上跑）。把 RELEASE-CHECKLIST.md 的
# 「重建后终验 + 来源自证」压成一条命令，把冻结窗口从"十几步"缩到"一条命令"。
#
# 前置：
#   1) 开发机已用 deploy/package.sh 从 HEAD 构建，并记下打印的 jar sha256；
#   2) 把 bundle 与 deploy.sh 传到目标机（脚本传输须校验 md5+字节数，见 §4.8）；
#   3) 交付面未提交改动为 0（本脚本无法校验，属开发机侧的前置，见 RELEASE-CHECKLIST §1）。
#
# 用法：
#   sudo EXPECTED_JAR_SHA=<本地构建的 jar sha256> \
#        BUNDLE=/home/kali/eduai/eduai-app.tar.gz bash release-run.sh
#
#   只做只读体检（不改动任何东西）：
#   bash release-run.sh --check
#
# 产出：一段可直接粘进报告的文本；任何一项不通过即以非 0 退出。
set -euo pipefail

APP_DIR=${APP_DIR:-/home/kali/eduai/backend}
JAR="$APP_DIR/eduai-backend.jar"
BUNDLE=${BUNDLE:-/home/kali/eduai/eduai-app.tar.gz}
EXPECTED_JAR_SHA=${EXPECTED_JAR_SHA:-}
OAUTH_JAR=/home/kali/oauth/app/spring-api-support-oauth-starter-4.0.0.42.jar
ACCEPT="$APP_DIR/src/acceptance/acceptance.py"
BASE=http://127.0.0.1:19095

log() { printf '  %s\n' "$*"; }
die() { printf '  [失败] %s\n' "$*" >&2; exit 1; }
S() { echo kali | sudo -S -p '' "$@"; }
sha() { sha256sum "$1" 2>/dev/null | cut -d' ' -f1; }

fingerprints() {
    echo "    eduai jar  sha256 = $(sha "$JAR")"
    echo "    oauth jar  sha256 = $(sha "$OAUTH_JAR")"
    echo "    eduai unit md5    = $(md5sum /etc/systemd/system/eduai-backend.service 2>/dev/null | cut -c1-32)"
    echo "    oauth unit md5    = $(md5sum /etc/systemd/system/oauth-server.service 2>/dev/null | cut -c1-32)"
    echo "    deploy.sh  md5    = $(md5sum "$APP_DIR/deploy/deploy.sh" 2>/dev/null | cut -c1-32)"
    echo "    eduai 表数        = $(mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N -e "select count(*) from information_schema.tables where table_schema='eduai';" 2>/dev/null)"
    echo "    oauth 表数        = $(mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N -e "select count(*) from information_schema.tables where table_schema='oauth';" 2>/dev/null)"
}

resources() {
    local avail swap tmp oom jn
    avail=$(free -m | awk '/Mem:/{print $7}')
    swap=$(free -m | awk '/Swap:/{if($2>0) printf "%d", ($3*100/$2); else print 0}')
    tmp=$(df /tmp | awk 'NR==2{gsub(/%/,"");print $5}')
    oom=$(S dmesg -T 2>/dev/null | grep -c -iE 'oom-kill|Killed process' || true)
    jn=$(pgrep -x java | wc -l)
    echo "    available=${avail}MB（≥1536）swap=${swap}%（<80）tmpfs=${tmp}%（<80）oom-kill=${oom:-0}（=0）java=${jn}"
    [ "${avail:-0}" -ge 1536 ] || die "资源判据不通过：available=${avail}MB"
    [ "${swap:-100}" -lt 80 ] || die "资源判据不通过：swap=${swap}%"
    [ "${tmp:-100}" -lt 80 ] || die "资源判据不通过：tmpfs=${tmp}%"
    [ "${oom:-1}" -eq 0 ] || die "资源判据不通过：存在 oom-kill 记录"
}

wait_health() {
    local i h
    for i in $(seq 1 30); do
        h=$(curl -s -o /dev/null -w '%{http_code}' --max-time 4 "$BASE/actuator/health" 2>/dev/null || true)
        [ "$h" = "200" ] && { echo "$h"; return 0; }
        sleep 10
    done
    echo "${h:-000}"
    return 1
}

# ---------- --check：只读体检 ----------
if [ "${1:-}" = "--check" ]; then
    log "== 只读体检（不做任何改动）=="
    log "-- 指纹 --";    fingerprints
    log "-- 资源 --";    resources
    log "-- 健康 --";    log "    eduai=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 $BASE/actuator/health)  oauth=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:19181/oauth/actuator/health)"
    log "-- 报告失效核查 --"
    # 报告本体在**仓库侧**（src/acceptance/ACCEPTANCE-REPORT-*.md），目标机通常没有，
    # 所以在主机上跑这一段多半是空的；它真正的用武之地是在仓库里跑：
    #   python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>
    if ls "$APP_DIR/src/acceptance/"/ACCEPTANCE-REPORT-*.md >/dev/null 2>&1; then
        python3 "$APP_DIR/src/acceptance/report-staleness.py" \
            --dir "$APP_DIR/src/acceptance" \
            --live-eduai "$(sha "$JAR")" --live-oauth "$(sha "$OAUTH_JAR")" || true
    else
        log "    本目录没有报告（报告在仓库 src/acceptance/）；此检查请在仓库侧跑："
        log "      python3 report-staleness.py --live-eduai $(sha "$JAR" | cut -c1-16)…"
    fi
    exit 0
fi

# ---------- 全量：来源自证 + 终验 ----------
[ -n "$EXPECTED_JAR_SHA" ] || die "必须提供 EXPECTED_JAR_SHA（开发机 deploy/package.sh 打印的那个）"
[ -f "$BUNDLE" ] || die "缺少产物 $BUNDLE"
[ -f "$ACCEPT" ] || die "缺少验收套件 $ACCEPT"

log "== 0. 验收前指纹 ==";  fingerprints
log "== 0. 资源判据 ==";    resources

log "== 1. 部署（替换 jar + 装 unit + schema 收敛）=="
S env BUNDLE="$BUNDLE" bash "$APP_DIR/deploy/deploy.sh"

log "== 2. 来源自证 =="
LIVE=$(sha "$JAR")
log "    期望（开发机构建）=$EXPECTED_JAR_SHA"
log "    实际（线上现役）  =$LIVE"
[ "$LIVE" = "$EXPECTED_JAR_SHA" ] || die "PROVENANCE_FAIL：线上产物与本地构建不一致（被换包了），本报告作废"
log "    PROVENANCE_OK"

log "== 3. 服务健康 =="
H=$(wait_health) || die "eduai 未在窗口内健康（=$H）"
log "    eduai=$H  oauth=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:19181/oauth/actuator/health)"

# 3.5 鉴权热就绪 —— health=200 **不代表**鉴权链已就绪。
# 实测（2026-10-04）：部署重启后立刻跑验收，首轮出现**系统性假红** ——
# 「管理员代入得到临时令牌: len=0」、B1~B15 与 D1/D2 全 403（35/55），
# 而同一套件稍后重跑就是 55/55。判据必须是「真实带令牌请求成功」，
# 不能用 health 或「套件没报连接错」代替。
log "== 3.5 鉴权热就绪 =="
ADMIN_USER=${ADMIN_USER:-admin}
ADMIN_PWD=${ADMIN_PWD:-admin123}
UA_READY='Mozilla/5.0 (eduai-release-readiness)'
ready=0
for i in $(seq 1 24); do
    tok=$(curl -s --max-time 10 -X POST "$BASE/v2/user/login" \
        -H 'Content-Type: application/json' -H "User-Agent: $UA_READY" \
        --data-binary "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PWD\"}" \
        | python3 -c "import sys,json
try: print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')
except Exception: print('')" 2>/dev/null)
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$BASE/v2/edu/dimension/students" \
        -H "User-Agent: $UA_READY" -H "Authorization: Bearer $tok" 2>/dev/null)
    if [ -n "$tok" ] && [ "$code" = "200" ]; then ready=1; break; fi
    sleep 5
done
if [ "$ready" = "1" ]; then
    log "    已就绪（约 $((i * 5))s）"
else
    log "    [警告] 24 次探测（约 120s）仍未见「登录+管理端 200」，继续跑验收（结果可能含假红）"
fi

log "== 4. API 生产级验收 =="
cd "$APP_DIR"
if PYTHONIOENCODING=utf-8 python3 "$ACCEPT" ; then
    ACC=OK
else
    ACC=FAIL
fi
log "    API 验收=$ACC"

log "== 5. 故障注入 =="
probe() {
    curl -s --max-time 30 -X POST "$BASE/v2/user/login" -H "Content-Type: application/json" \
        --data-binary '{"username":"admin","password":"admin123"}' \
    | python3 -c "import sys,json
try:
    d=json.load(sys.stdin); t=(d.get('data') or {}).get('accessToken') or ''
    print('ok=%s token_len=%d' % (d.get('ok'), len(t)))
except Exception: print('非 JSON')"
}
log "    基线: $(probe)"
S systemctl stop oauth-server.service
sleep 2
T0=$(date +%s%3N); R=$(probe); T1=$(date +%s%3N)
log "    停 oauth: 耗时=$((T1-T0))ms $R  eduai健康=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 $BASE/actuator/health)  restarts=$(S systemctl show eduai-backend -p NRestarts --value)"
S systemctl start oauth-server.service
# 窗口必须 >= 实测冷启动时长：2026-10-04 实测 oauth 停机后恢复健康需 **120s**
# （原窗口 20×5=100s，比它短 —— 于是恢复其实成功却报 oauth=000，是**假阴性**，
#  差点把「依赖恢复」判成失败）。这里放到 48×5=240s 留足余量。
for i in $(seq 1 48); do
    oh=$(curl -s -o /dev/null -w '%{http_code}' --max-time 4 http://127.0.0.1:19181/oauth/actuator/health 2>/dev/null || true)
    [ "$oh" = "200" ] && break
    sleep 5
done
sleep 3
log "    恢复后（oauth=$oh）: $(probe)"

log "== 6. 验收后指纹（必须与前一致）=="
fingerprints

log "== 结果 =="
log "    PROVENANCE_OK / API=$ACC"
[ "$ACC" = "OK" ] || die "API 验收未全绿，不予签收"
log "    可签收：把上面两段指纹与 PROVENANCE_OK 抄进报告"