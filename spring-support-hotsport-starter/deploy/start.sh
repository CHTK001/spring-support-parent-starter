#!/bin/bash
# =============================================================================
# start.sh - hotsport 服务生命周期管理（lib 与业务 jar 分离部署）
#
# 用法:
#   ./start.sh start     启动（日志追加到 logs/app.log）
#   ./start.sh stop      停止（先 TERM，超时再 KILL）
#   ./start.sh restart   重启
#   ./start.sh status    查看状态
#   ./start.sh health    探活
#   ./start.sh log [n]   查看日志尾部（默认 80 行）
#
# 目录约定（默认 APP_DIR=/data/hotsport）:
#   app.jar              业务 jar，约 3MB，每次升级只替换它
#   lib/*.jar            第三方依赖，约 107MB / 125 个，只在依赖变化时增量更新
#   config/app.env       部署参数（KEY=VALUE），不入仓库；见 app.env.example
#   logs/app.log         运行日志
#   app.pid              pid 文件
#
# 为什么用 -cp 而不是 java -jar:
#   java -jar 只认 jar 内 BOOT-INF/lib，无法外置依赖；PropertiesLauncher +
#   -Dloader.path 实测启动即 NoClassDefFoundError（loader.path 未把 lib 加进
#   classloader）。因此用显式 classpath：-cp "app.jar:lib/*" + 主类全名。
#   本项目 ai-starter 模块的 deploy/ 已用同一方案长期验证。
# =============================================================================
set -uo pipefail

APP_DIR="${APP_DIR:-/data/hotsport}"
APP_JAR="$APP_DIR/app.jar"
LIB_DIR="$APP_DIR/lib"
ENV_FILE="$APP_DIR/config/app.env"
PID_FILE="$APP_DIR/app.pid"
LOG_DIR="$APP_DIR/logs"
LOG_FILE="$LOG_DIR/app.log"
PORT="${PORT:-18086}"
START_TIMEOUT="${START_TIMEOUT:-120}"

MAIN_CLASS="com.chua.starter.hotsport.support.HotsportApplication"

# java 解析顺序：显式 JAVA_BIN > APP_DIR/jdk/bin/java（自带 JDK）> PATH 里的 java
resolve_java() {
  if [ -n "${JAVA_BIN:-}" ]; then echo "$JAVA_BIN"; return; fi
  if [ -x "$APP_DIR/jdk/bin/java" ]; then echo "$APP_DIR/jdk/bin/java"; return; fi
  command -v java 2>/dev/null
}

# 默认 JVM 参数。堆按小机器给：APM 观测端常与宿主同机，别把宿主挤掉。
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx1024m -Dfile.encoding=UTF-8}"
APP_ARGS="${APP_ARGS:-}"

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
  # pid 文件丢失时的兜底：按主类找
  local p
  p="$(pgrep -f "$MAIN_CLASS" 2>/dev/null | head -1)"
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
  local java_bin
  java_bin="$(resolve_java)"
  [ -n "$java_bin" ] && [ -x "$java_bin" ] || { log "缺少 java：请设 JAVA_BIN 或在 $APP_DIR/jdk 放 JDK"; return 1; }
  [ -f "$APP_JAR" ] || { log "缺少业务 jar: $APP_JAR"; return 1; }
  [ -d "$LIB_DIR" ] || { log "缺少依赖目录: $LIB_DIR"; return 1; }

  mkdir -p "$LOG_DIR"
  log "启动 app.jar（lib $(ls "$LIB_DIR"/*.jar 2>/dev/null | wc -l) 个 jar，java $("$java_bin" -version 2>&1 | head -1)）"
  # 先切到 APP_DIR：应用里有相对路径配置，进程 CWD 会决定它们的解析结果
  cd "$APP_DIR" || { log "无法进入 $APP_DIR"; return 1; }
  # shellcheck disable=SC2086
  nohup "$java_bin" $JAVA_OPTS \
    -cp "$APP_JAR:$LIB_DIR/*" "$MAIN_CLASS" \
    --server.port="$PORT" $APP_ARGS >> "$LOG_FILE" 2>&1 &
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

# 探活端点：本应用没有 actuator，用 APM 自身的统计接口。
# 它不依赖外部数据库（H2 兜底），能证明 Tomcat + Spring MVC + APM 服务层已就绪。
HEALTH_PATH="${HEALTH_PATH:-/api/apm/stats}"

do_health() {
  local code body
  body="$(curl -s --max-time 5 "http://127.0.0.1:$PORT$HEALTH_PATH" 2>/dev/null)"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$PORT$HEALTH_PATH" 2>/dev/null)"
  log "GET $HEALTH_PATH -> ${code:-无响应}"
  [ "$code" = "200" ] || return 1
  printf '%s' "$body" | grep -q '"ok" *: *true'
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
