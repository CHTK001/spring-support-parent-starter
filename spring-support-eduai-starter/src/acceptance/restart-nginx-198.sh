#!/usr/bin/env bash
set -euo pipefail
CONF=/etc/nginx/eduai-web.conf
PIDFILE=/run/eduai-web.pid

echo "==> current listener/pid"
ss -ltnp 2>/dev/null | grep ':19093' || true
cat "$PIDFILE" 2>/dev/null || true

echo "==> config test"
nginx -t -c "$CONF"

echo "==> stop old master"
OLD_PID=$(cat "$PIDFILE" 2>/dev/null || true)
if [ -n "${OLD_PID}" ] && kill -0 "$OLD_PID" 2>/dev/null; then
  kill "$OLD_PID"
  for _ in $(seq 1 20); do
    kill -0 "$OLD_PID" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "OLD_PID_STILL_ALIVE $OLD_PID" >&2
    exit 1
  fi
  echo "stopped old pid=$OLD_PID"
else
  echo "no old pid on file"
fi

# 确保旧 master 不以 pidfile 之外的进程残留
if pgrep -f "nginx -c /etc/nginx/eduai-web.conf" >/dev/null 2>&1; then
  pkill -TERM -f "nginx -c /etc/nginx/eduai-web.conf" || true
  sleep 2
fi

echo "==> start new master"
nginx -c "$CONF"
sleep 1
echo "==> new pidfile/status"
cat "$PIDFILE" 2>/dev/null || true
ps -ef | grep 'eduai-web.conf' | grep -v grep | head -5

echo "==> health"
for i in $(seq 1 10); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:19093/healthz 2>/dev/null || true)
  if [ "$code" = "200" ]; then echo "HTTP=$code"; break; fi
  sleep 1
done
[ "$code" = "200" ] || { echo "HEALTH_FAIL code=$code" >&2; exit 1; }
curl -s -o /dev/null -w 'ROOT=%{http_code}\n' --max-time 5 http://127.0.0.1:19093/
echo "RESTART_OK"