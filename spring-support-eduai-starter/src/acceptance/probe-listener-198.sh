#!/usr/bin/env bash
echo "=== listeners 19093 ==="
ss -ltnp 2>/dev/null | grep ':19093' || echo "no 19093 listen"
echo "=== nginx master/cmd ==="
ps -ef | grep -E 'nginx' | grep -v grep | head -10
echo "=== eduai-web service ==="
systemctl is-active eduai-web.service 2>/dev/null || echo "not-active"
systemctl show eduai-web.service -p ExecMainPID,ActiveState 2>/dev/null | head
echo "=== pidfile ==="
ls -la /run/eduai-web.pid 2>/dev/null || echo "no pidfile"