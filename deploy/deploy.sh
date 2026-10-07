#!/bin/bash
# =============================================================================
# 后端升级部署脚本 —— 运行在 192.168.50.198
#
# 功能:
#   1. 停止项目相关进程（8081 / 8112 / 18082 / 29181）
#   2. 启动新版本后端（紧凑头模式，MySQL，非 H2）
#
# 使用: bash deploy.sh
# =============================================================================
set -e

# ---- 1. 停止项目相关进程 ----
echo "=== 停止项目相关进程 ==="
for port in 8081 8112 18082 29181 8082 19090; do
    pid=$(ss -tlnp 2>/dev/null | grep ":$port " | grep -oP 'pid=\K[0-9]+' | head -1 || true)
    if [ -n "$pid" ]; then
        echo "  端口 $port → PID $pid → 停止"
        kill -9 "$pid" 2>/dev/null || true
    fi
done

# ---- 2. 启动新后端 (紧凑头 + MySQL) ----
JAR="spring-support-monitor-starter-4.0.0.42-exec.jar"
if [ ! -f "$JAR" ]; then
    echo "ERROR: 未找到 $JAR，请先上传到 /opt/monitor/"
    exit 1
fi

echo ""
echo "=== 启动后端 (紧凑头模式, MySQL) ==="
echo "  JAR: $JAR"
echo "  主端口: 8082 (server.port=8082)"
echo "  文件服务器端口: 18082 (CORS 直接访问)"
echo "  数据库: MySQL 172.16.0.40:3306/report (non-H2)"
echo "  内存优化: UseCompressedOops + UseCompressedClassPointers"
echo ""

nohup java \
    -server \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -XX:MaxRAMPercentage=75 \
    -XX:MaxMetaspaceSize=256m \
    -Xss256k \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Dfile.encoding=UTF-8 \
    -Dspring.datasource.url="jdbc:mysql://172.16.0.40:3306/report?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true" \
    -Dspring.datasource.username="root" \
    -Dspring.datasource.password="${MONITOR_DB_PASSWORD}" \
    -Dplugin.filesystem.storage-server.port=18082 \
    -Dserver.port=8082 \
    -jar "$JAR" \
    > monitor.log 2>&1 &

echo $! > monitor.pid
echo ">>> 后台 PID: $(cat monitor.pid)"
echo ">>> 日志: tail -f monitor.log"
echo ">>> 如需验收，请访问: http://192.168.50.198:8848/"
