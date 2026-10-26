#!/bin/bash
# =============================================================================
# start.sh - ai-starter 服务生命周期管理（lib 与业务 jar 分离部署）
#
# 用法:
#   ./start.sh start     启动（前台日志写 logs/app.log）
#   ./start.sh stop      停止（先 TERM，超时再 KILL）
#   ./start.sh restart   重启
#   ./start.sh status    查看状态
#   ./start.sh log [n]   查看日志尾部（默认 80 行）
#
# 目录约定（默认 APP_DIR=/data/ai-starter）:
#   app.jar              业务 jar，约 3MB，每次升级只替换它
#   lib/*.jar            第三方依赖，约 920MB，只在依赖变化时增量更新
#   config/app.env       部署参数（KEY=VALUE），不入仓库；见 app.env.example
#   logs/app.log         运行日志
#   app.pid              pid 文件
#
# 为什么用 -cp 而不是 java -jar:
#   java -jar 只认 jar 内 BOOT-INF/lib，无法外置依赖。曾尝试 PropertiesLauncher +
#   -Dloader.path，实测启动即 NoClassDefFoundError（loader.path 未把 lib 加进
#   classloader），故改为显式 classpath：-cp "app.jar:lib/*"。该方式实测可用。
# =============================================================================
set -uo pipefail

APP_DIR="${APP_DIR:-/data/ai-starter}"
APP_JAR="$APP_DIR/app.jar"
LIB_DIR="$APP_DIR/lib"
ENV_FILE="$APP_DIR/config/app.env"
PID_FILE="$APP_DIR/app.pid"
LOG_DIR="$APP_DIR/logs"
LOG_FILE="$LOG_DIR/app.log"
JAVA_BIN="${JAVA_BIN:-$APP_DIR/jdk/bin/java}"
PORT="${PORT:-8787}"
START_TIMEOUT="${START_TIMEOUT:-180}"

# 默认启动参数。演示账号刻意不写在这里：它必须由部署方通过 config/app.env 或
# 环境变量传入，仓库里不出现任何可用口令（见 ai-starter.yml 的 temp.user 说明）。
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2560m -Duser.timezone=Asia/Shanghai -Djava.net.preferIPv4Stack=true}"
APP_ARGS="${APP_ARGS:---spring.profiles.active=local}"

# 部署参数文件优先于内置默认值；里面可以覆盖 PORT / JAVA_OPTS / APP_ARGS
if [ -f "$ENV_FILE" ]; then
  # shellcheck disable=SC1090
  set -a; . "$ENV_FILE"; set +a
fi

log() { printf '%s %s\n' "$(date '+%F %T')" "$*"; }

running_pid() {
  if [ -f "$PID_FILE" ]; then
    local p
    p="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$p" ] && kill -0 "$p" 2>/dev/null; then
      echo "$p"; return 0
    fi
  fi
  # pid 文件丢失时的兜底：按 jar 路径找
  local p
  p="$(pgrep -f "app.jar" 2>/dev/null | head -1)"
  [ -n "$p" ] && { echo "$p"; return 0; }
  return 1
}

port_listening() {
  ss -ltn 2>/dev/null | grep -q ":$PORT "
}

do_start() {
  local pid
  if pid="$(running_pid)"; then
    log "已在运行 (pid=$pid)，无需启动"
    return 0
  fi
  [ -x "$JAVA_BIN" ] || { log "缺少 java: $JAVA_BIN"; return 1; }
  [ -f "$APP_JAR" ] || { log "缺少业务 jar: $APP_JAR"; return 1; }
  [ -d "$LIB_DIR" ] || { log "缺少依赖目录: $LIB_DIR"; return 1; }

  mkdir -p "$LOG_DIR"
  log "启动 app.jar（lib $(ls "$LIB_DIR"/*.jar 2>/dev/null | wc -l) 个 jar）"
  # shellcheck disable=SC2086
  nohup "$JAVA_BIN" $JAVA_OPTS \
    -cp "$APP_JAR:$LIB_DIR/*" \
    com.chua.starter.ai.support.AiStarterApplication \
    $APP_ARGS >> "$LOG_FILE" 2>&1 &
  local new_pid=$!
  echo "$new_pid" > "$PID_FILE"
  log "pid=$new_pid，等待端口 $PORT（最多 ${START_TIMEOUT}s）"

  local i=0
  while [ "$i" -lt "$START_TIMEOUT" ]; do
    if ! kill -0 "$new_pid" 2>/dev/null; then
      log "进程已退出，启动失败。日志尾部："
      tail -40 "$LOG_FILE"
      rm -f "$PID_FILE"
      return 1
    fi
    if port_listening; then
      log "端口 $PORT 已监听（耗时 ${i}s）"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  log "超时：${START_TIMEOUT}s 内端口未监听。日志尾部："
  tail -40 "$LOG_FILE"
  return 1
}

do_stop() {
  local pid
  if ! pid="$(running_pid)"; then
    log "未在运行"
    rm -f "$PID_FILE"
    return 0
  fi
  log "停止 pid=$pid"
  kill "$pid" 2>/dev/null
  local i=0
  while [ "$i" -lt 60 ]; do
    kill -0 "$pid" 2>/dev/null || { log "已停止"; rm -f "$PID_FILE"; return 0; }
    sleep 1
    i=$((i + 1))
  done
  log "60s 未退出，强制终止"
  kill -9 "$pid" 2>/dev/null
  sleep 2
  rm -f "$PID_FILE"
  return 0
}

do_status() {
  local pid
  if pid="$(running_pid)"; then
    log "运行中 pid=$pid"
    ps -o pid,etime,rss,cmd -p "$pid" 2>/dev/null | tail -n +2
    port_listening && log "端口 $PORT: LISTEN" || log "端口 $PORT: 未监听"
    return 0
  fi
  log "未运行"
  return 3
}

# 探活端点。本应用**没有** actuator：spring-boot-starter-actuator 不在依赖里，
# 请求 /actuator/health 会被当成静态资源处理并 404，日志是
#   「资源不存在（未映射路径）: No static resource actuator/health」
# （yml 里放行 /actuator/health 只是白名单条目，不代表该端点存在）。
# 因此改用登录兼容端点：它不依赖数据库也能证明 Tomcat + Spring MVC + 鉴权过滤器已就绪；
# 配了 HEALTH_USER / HEALTH_PASS 时还能顺带验证静态账号与登录链路是否真的可用。
HEALTH_PATH="${HEALTH_PATH:-/v2/auth/login}"

do_health() {
  local code body
  if [ -n "${HEALTH_USER:-}" ] && [ -n "${HEALTH_PASS:-}" ]; then
    body="$(curl -s --max-time 5 -X POST "http://127.0.0.1:$PORT$HEALTH_PATH" \
      -H 'Content-Type: application/json' \
      --data-binary "{\"username\":\"$HEALTH_USER\",\"password\":\"$HEALTH_PASS\"}" 2>/dev/null)"
    log "POST $HEALTH_PATH (账号 $HEALTH_USER) -> $(printf '%s' "$body" | tr -d '\n' | cut -c1-140)"
    printf '%s' "$body" | grep -q '"ok" *: *true'
    return $?
  fi
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X POST "http://127.0.0.1:$PORT$HEALTH_PATH" 2>/dev/null)"
  log "POST $HEALTH_PATH -> ${code:-无响应}"
  [ "$code" = "200" ]
}

case "${1:-start}" in
  start)   do_start ;;
  stop)    do_stop ;;
  restart) do_stop; do_start ;;
  status)  do_status ;;
  health)  do_health ;;
  log)     tail -n "${2:-80}" "$LOG_FILE" ;;
  *)       echo "用法: $0 {start|stop|restart|status|health|log [n]}" >&2; exit 2 ;;
esac
