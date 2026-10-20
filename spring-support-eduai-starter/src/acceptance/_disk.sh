#!/bin/sh
df -h / | tail -1
ls -la /tmp/eduai-jar-deploy 2>/dev/null || echo "missing"
du -sh /tmp/* 2>/dev/null | sort -rh | head -6
ls -lt /home/kali/eduai/backend/eduai-backend.jar* 2>/dev/null | head -5
ls -lt /home/kali/eduai/backups/ 2>/dev/null | head -5