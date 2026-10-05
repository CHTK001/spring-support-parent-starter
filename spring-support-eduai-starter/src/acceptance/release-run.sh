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
# 非空即视为"人工放行资源判据"，必须写清理由（会被打印并进入指纹）。
BREAKGLASS=${EDUAI_RELEASE_BREAKGLASS:-}
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
    echo "    JVM 崩溃转储      = $(crash_state)"
    echo "    人工放行          = ${BREAKGLASS:-（无）}"
}

# JVM 崩溃转储状态：<份数>:<最新一份的 epoch 秒或 0>
#
# 为什么它必须进指纹：原生库（如 onnxruntime）在 JVM **关闭过程中**崩溃时，
# systemd 只看到一个非零退出码，日志尾部也只有一段 hs_err 文本，很容易被当成
# 「正常的重启噪声」放过 —— 10-04 的 4 次原生 SIGSEGV 就是这样被漏掉的，
# 甚至一度被误判成「打穿在服 JVM 的 P0」。把「转储份数 + 最新时间」做成指纹，
# 就能在**发布期间**直接判定：这次发布有没有把 JVM 打断。
crash_state() {
    local n newest
    n=$(ls -1 "$APP_DIR"/hs_err_pid*.log 2>/dev/null | wc -l | tr -d ' ')
    newest=$(ls -1t "$APP_DIR"/hs_err_pid*.log 2>/dev/null | head -1)
    if [ -n "$newest" ]; then
        echo "$n:$(date -r "$newest" '+%s')"
    else
        echo "$n:0"
    fi
}

resources() {
    local avail swap tmp oom jn
    avail=$(free -m | awk '/Mem:/{print $7}')
    swap=$(free -m | awk '/Swap:/{if($2>0) printf "%d", ($3*100/$2); else print 0}')
    tmp=$(df /tmp | awk 'NR==2{gsub(/%/,"");print $5}')
    oom=$(S dmesg -T 2>/dev/null | grep -c -iE 'oom-kill|Killed process' || true)
    jn=$(pgrep -x java | wc -l)
    echo "    available=${avail}MB（≥1536）swap=${swap}%（<80）tmpfs=${tmp}%（<80）oom-kill=${oom:-0}（=0）java=${jn}"
    # Break-glass。默认不放行；只有显式给出书面理由时才放宽 available/swap 两项，
    # 理由会原样进入指纹与报告，不会被悄悄吞掉。
    #
    # 为什么需要它：这台是**共享主机**，swap 占用由别的租户的长期 JVM 决定，
    # 与本次发布要替换的那个进程无关。实测 10 个 JVM 里有 ngx-console /
    # monitor-starter / 两个 collect-scheduler 都不属于本项目，swap 常年 83%~84%，
    # 于是「swap<80」对任何人都恒不成立 —— 一个永远过不去的关卡不是关卡，是拦路。
    # 真正决定"这台机器扛不扛得住这次发布"的是 available、tmpfs 和 oom-kill，
    # 这三项仍然一票否决；swap 在无 oom-kill 记录时只作告警。
    if [ -n "$BREAKGLASS" ]; then
        echo "    [人工放行] available/swap 两项已放宽；理由：$BREAKGLASS"
        [ "${tmp:-100}" -lt 80 ] || die "资源判据不通过：tmpfs=${tmp}%（放行不覆盖 tmpfs）"
        [ "${oom:-1}" -eq 0 ] || die "资源判据不通过：存在 oom-kill 记录（放行不覆盖 oom-kill）"
        return 0
    fi
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
# 崩溃转储基线：必须在部署**之前**取，因为要抓的正是「本次部署把 JVM 打断」这种情况
# （含上一个进程在 systemctl restart 时的关闭期原生崩溃）。
PRE_CRASH=$(crash_state)
echo "    崩溃转储基线      = $PRE_CRASH"
log "== 0. 资源判据 ==";    resources

log "== 1. 部署（替换 jar + 装 unit + schema 收敛）=="
S env BUNDLE="$BUNDLE" bash "$APP_DIR/deploy/deploy.sh"

log "== 2. 来源自证 =="
LIVE=$(sha "$JAR")
# 打印长度是刻意的：sha256 是 64 位十六进制，两边只要有一位抄错/漏掉就会不相等，
# 而「不相等」的两种原因（抄错了 vs 真被换包）必须能一眼分开。
# 实战踩过：手抄期望值多带了一个字符（65 位），日志看上去像「被换包」，白排查一轮。
log "    期望（开发机构建）=$EXPECTED_JAR_SHA  长度=${#EXPECTED_JAR_SHA}"
log "    实际（线上现役）  =$LIVE  长度=${#LIVE}"
if [ "${#LIVE}" -ne 64 ] || [ "${#EXPECTED_JAR_SHA}" -ne 64 ]; then
    die "PROVENANCE_FAIL：sha256 长度不是 64（期望 ${#EXPECTED_JAR_SHA} / 实际 ${#LIVE}）——先查 EXPECTED_JAR_SHA 是不是抄错了"
fi
[ "$LIVE" = "$EXPECTED_JAR_SHA" ] || die "PROVENANCE_FAIL：线上产物与本地构建不一致（被换包了），本报告作废"
log "    PROVENANCE_OK"

log "== 3. 服务健康 =="
H=$(wait_health) || die "eduai 未在窗口内健康（=$H）"
log "    eduai=$H  oauth=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:19181/oauth/actuator/health)"

# 3.5 鉴权热就绪 —— health=200 **不代表**鉴权链已就绪。
#
# 判据必须是「登录拿到令牌 + 带令牌请求 200」，不能用 health 或「套件没报连接错」代替。
# 实测两次踩坑（2026-10-04）：
#   ① 重启后立刻跑验收，首轮出现**系统性假红**（35/55），稍后重跑即 55/55；
#   ② 探针 5 秒就判「已就绪」，随后 7 个套件**全部**在「管理员登录」上失败 ——
#      鉴权链在重启后有几分钟才稳定（OAuth TCP 通道上的凭据/令牌状态要重新落定）。
# 所以这里既把窗口从 120s 放宽到 6 分钟，也把「未就绪」从**警告**升级为**中止**：
# 未就绪还继续跑，只会得到一片「管理员登录失败」的假红，看着像代码坏了。
log "== 3.5 鉴权热就绪（登录 + 带令牌请求，缺一不可）=="
ADMIN_USER=${ADMIN_USER:-admin}
ADMIN_PWD=${ADMIN_PWD:-admin123}
UA_READY='Mozilla/5.0 (eduai-release-readiness)'
ready=0
for i in $(seq 1 36); do   # 36 × 10s = 6 分钟
    tok=$(curl -s --max-time 10 -X POST "$BASE/v2/user/login" \
        -H 'Content-Type: application/json' -H "User-Agent: $UA_READY" \
        --data-binary "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PWD\"}" \
        | python3 -c "import sys,json
try: print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')
except Exception: print('')" 2>/dev/null)
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$BASE/v2/edu/dimension/students" \
        -H "User-Agent: $UA_READY" -H "Authorization: Bearer $tok" 2>/dev/null)
    if [ -n "$tok" ] && [ "$code" = "200" ]; then ready=1; break; fi
    sleep 10
done
if [ "$ready" = "1" ]; then
    log "    已就绪（约 $((i * 10))s）"
else
    die "鉴权链 6 分钟内未就绪（最后一次：token=${#tok} 管理端=$code）——中止，不跑套件（否则是一片假红）"
fi

log "== 4. API 生产级验收（主套件 + 各专项套件；任一失败即整体不通过）=="
cd "$APP_DIR"
# 顺序有依赖：student-password-acceptance 会临时改口令并在结束时改回，
# 排在 student-manage-acceptance 之前，保证后者依赖的 2026000001/stu123456 在它跑完后仍有效。
# 新增功能必须把自己的专项套件加进这个列表，否则「测过」只停留在开发机。
# hanzi-collect-acceptance 会真触发一次采集（下载 27MB 公开字典），耗时按分钟计，
# 且需要目标机能出网；出网不通时它会把采集段判失败（这是应有行为，不是环境问题）。
SUITES="acceptance.py student-password-acceptance.py student-manage-acceptance.py first-login-password-acceptance.py paper-assign-acceptance.py marking-acceptance.py hanzi-collect-acceptance.py"
ACC=OK
SUITE_RESULT=""
for s in $SUITES; do
    if [ ! -f "src/acceptance/$s" ]; then
        log "    [缺失] $s —— 套件没随发布上传（这类「静默跳过」会让报告假绿）"
        ACC=FAIL; SUITE_RESULT="$SUITE_RESULT ${s%%.py}=MISSING"; continue
    fi
    log "    --- $s ---"
    if PYTHONIOENCODING=utf-8 python3 "src/acceptance/$s"; then
        SUITE_RESULT="$SUITE_RESULT ${s%%.py}=OK"
    else
        ACC=FAIL; SUITE_RESULT="$SUITE_RESULT ${s%%.py}=FAIL"
    fi
done
log "    套件:$SUITE_RESULT"
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

# 部署/验收期间如果新冒出 JVM 崩溃转储，说明这一轮把 JVM 打断了（哪怕套件全绿、
# 服务也活着），不予签收：崩溃会掩盖真实退出原因，也说明有原生调用没收干净。
POST_CRASH=$(crash_state)
echo "    崩溃转储比对      = 发布前 $PRE_CRASH -> 发布后 $POST_CRASH"
if [ "$PRE_CRASH" != "$POST_CRASH" ]; then
    ls -1t "$APP_DIR"/hs_err_pid*.log 2>/dev/null | head -2 | while read -r f; do
        echo "    [失败] 新增崩溃转储: $f ($(date -r "$f" '+%F %T'))" >&2
    done
    die "本次发布期间出现新的 JVM 崩溃转储，不予签收"
fi

log "== 结果 =="
log "    PROVENANCE_OK / API=$ACC"
[ "$ACC" = "OK" ] || die "API 验收未全绿，不予签收"
log "    可签收：把上面两段指纹与 PROVENANCE_OK 抄进报告"