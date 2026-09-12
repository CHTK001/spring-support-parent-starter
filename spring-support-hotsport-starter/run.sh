#!/usr/bin/env bash
# ============================================================
# Hotsport 前后端一键启动脚本
#
# 后端: spring-support-hotsport-starter jar → http://127.0.0.1:8086
#       (REST /api/apm/* + 内置 SPA 静态托管 + Agent 自注入)
# 前端: apps/vue-support-hotspot-starter vite → http://127.0.0.1:8890
#       (dev 热重载, /api/apm 代理到 8086)
#
# 用法:
#   bash run.sh            # 启动前后端
#   bash run.sh stop       # 停止前后端
#   bash run.sh status     # 查看状态
# ============================================================
set -uo pipefail

ROOT="/d/ch/project"
BE_JAR="$ROOT/spring-support-parent-starter/spring-support-hotsport-starter/target/spring-support-hotsport-starter-4.0.0.42.jar"
FE_DIR="$ROOT/vue-support-parent-starter/apps/vue-support-hotspot-starter"
BE_LOG="/tmp/hotsport-be.log"
FE_LOG="/tmp/hotsport-fe.log"
BE_PORT=8086
FE_PORT=8890

port_in_use() {
  cmd //c "netstat -ano | findstr :$1" 2>/dev/null | grep -q LISTENING
}

start_backend() {
  if port_in_use "$BE_PORT"; then
    echo "[skip] 后端已运行 (:$BE_PORT)"
    return
  fi
  [ -f "$BE_JAR" ] || { echo "[warn] 后端 jar 不存在, 请先 mvn package"; return; }
  nohup java -jar "$BE_JAR" > "$BE_LOG" 2>&1 &
  echo "[ok] 后端启动中 (PID $!), 等待 :$BE_PORT ..."
  for i in $(seq 1 30); do
    sleep 1; port_in_use "$BE_PORT" && break
  done
  port_in_use "$BE_PORT" && echo "[ok] 后端就绪 http://127.0.0.1:$BE_PORT/" || echo "[err] 后端未就绪, 查 $BE_LOG"
}

stop_backend() {
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Select-Object -ExpandProperty ProcessId | ForEach-Object { \$c = (Get-CimInstance Win32_Process -Filter \"ProcessId=\$_\").CommandLine; if(\$c -match 'hotsport') { Stop-Process -Id \$_ -Force } }" 2>/dev/null
  echo "[ok] 后端已停止"
}

start_frontend() {
  if port_in_use "$FE_PORT"; then
    echo "[skip] 前端已运行 (:$FE_PORT)"
    return
  fi
  cd "$FE_DIR"
  nohup node ./node_modules/vite/bin/vite.js --port $FE_PORT --strictPort > "$FE_LOG" 2>&1 &
  echo "[ok] 前端启动中 (PID $!), 等待 :$FE_PORT ..."
  for i in $(seq 1 20); do
    sleep 1; port_in_use "$FE_PORT" && break
  done
  port_in_use "$FE_PORT" && echo "[ok] 前端就绪 http://127.0.0.1:$FE_PORT/" || echo "[err] 前端未就绪, 查 $FE_LOG"
}

stop_frontend() {
  cmd //c "powershell -NoProfile -Command \"Get-NetTCPConnection -LocalPort $FE_PORT -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id \$_ -Force -ErrorAction SilentlyContinue }\"" 2>/dev/null
  echo "[ok] 前端已停止"
}

status() {
  port_in_use "$BE_PORT" && echo "[ok] 后端 :$BE_PORT 运行中" || echo "[--] 后端 :$BE_PORT 未运行"
  port_in_use "$FE_PORT" && echo "[ok] 前端 :$FE_PORT 运行中" || echo "[--] 前端 :$FE_PORT 未运行"
}

case "${1:-start}" in
  start)  start_backend; start_frontend; status ;;
  stop)   stop_backend; stop_frontend ;;
  status) status ;;
  restart) stop_backend; stop_frontend; sleep 2; start_backend; start_frontend; status ;;
  *) echo "用法: $0 {start|stop|status|restart}"; exit 1 ;;
esac
