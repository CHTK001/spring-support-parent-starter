#!/bin/bash
S() { echo kali | sudo -S "$@" 2>/dev/null; }
LOG=/opt/ai-starter/app-out.log
echo "=== 当前服务状态 ==="
echo "  is-active=$(systemctl is-active ai-starter)"
echo "  MainPID=$(systemctl show ai-starter -p MainPID --value)  NRestarts=$(systemctl show ai-starter -p NRestarts --value)"
echo "  ExecMainStartTimestamp=$(systemctl show ai-starter -p ExecMainStartTimestamp --value)"
echo "  8787 监听=$(ss -lntH 2>/dev/null | awk '{print $4}' | grep -c ':8787$')"
echo "  app.jar md5=$(md5sum /opt/ai-starter/app.jar 2>/dev/null | cut -c1-8)"
echo
echo "=== 谁在 8787（若有）==="
ss -lntp 2>/dev/null | grep ':8787' | sed 's/^/  /'
echo
echo "=== systemd 最近事件 ==="
S journalctl -u ai-starter -n 25 --no-pager | sed 's/^/  /'
echo
echo "=== 应用日志：最近是否崩溃/重启（grep -a）==="
S bash -c "tail -400 $LOG" 2>/dev/null | grep -aE "Started AiStarterApplication|Stopping|Shutdown|OutOfMemory|Killed|ERROR.*Application|Application run failed|鉴权服务器异常" | tail -12 | sed -E 's/\x1b\[[0-9;]*m//g' | cut -c1-150 | sed 's/^/  /'
echo
echo "=== 登录自检（现在能不能拿到令牌）==="
curl -s -m 25 -o /tmp/lg.json -w '  HTTP=%{http_code}\n' -H 'Content-Type: application/json' \
  -X POST -d '{"username":"admin","password":"admin123"}' http://127.0.0.1:8787/v2/user/login
head -c 300 /tmp/lg.json | sed 's/^/  /'; echo
echo DIAG_STATE_DONE
