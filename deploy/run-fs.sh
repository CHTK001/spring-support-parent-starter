#!/bin/bash
# =============================================================================
# File Storage Server + Monitor Backend 启动脚本
# 文件存储服务器（FileStorageServerFilter）与监控后端的统一启动脚本
#
# 使用紧凑对象指针 + 压缩类指针减少内存：
#   -XX:+UseCompressedOops      ← 对象引用压缩 (32-bit 代替 64-bit 指针)
#   -XX:+UseCompressedClassPointers ← 类指针压缩 (节省 ~1GB 堆内存)
#   -XX:MaxRAMPercentage=75     ← 限制堆内存为可用 RAM 的 75%
#   -XX:MaxMetaspaceSize=256m  ← 限制元空间 (防止本地内存 OOM)
#   -Xss256k                  ← 缩小线程栈 (默认 1024k，节省线程内存)
#   -XX:+UseG1GC               ← G1 回收器 (低延迟+高吞吐)
# =============================================================================
set -e

# ---- 停止相同项目的旧进程 (仅本项目，仅限 8082/18082/29181) ----
for port in 8082 18082 29181; do
  pid=$(ss -tlnp 2>/dev/null | grep ":$port " | grep -oP 'pid=\K[0-9]+' || true)
  if [ -n "$pid" ]; then
    echo "停止占用端口 $port 的进程 (PID $pid)"
    kill -9 "$pid" 2>/dev/null || true
    sleep 1
  fi
done

# ---- JVM 参数 (紧凑头 + 内存优化) ----
JAVA_OPTS="-server"
JAVA_OPTS="$JAVA_OPTS -XX:+UseCompressedOops"
JAVA_OPTS="$JAVA_OPTS -XX:+UseCompressedClassPointers"
JAVA_OPTS="$JAVA_OPTS -XX:MaxRAMPercentage=75"
JAVA_OPTS="$JAVA_OPTS -XX:MaxMetaspaceSize=256m"
JAVA_OPTS="$JAVA_OPTS -Xss256k"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:+UnlockExperimentalVMOptions"
JAVA_OPTS="$JAVA_OPTS -XX:+UseStringDeduplication"
JAVA_OPTS="$JAVA_OPTS -XX:G1HeapRegionSize=16m"
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Dsun.jnu.encoding=UTF-8"

# ---- 数据库配置 (MySQL，非 H2) ----
# 覆盖 application.yml 中的 MySQL 配置 (默认已指向 172.16.0.40:3306/report)
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://172.16.0.40:3306/report?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true}"
export MONITOR_DB_PASSWORD="${MONITOR_DB_PASSWORD:-${MONITOR_DB_PASSWORD}}"

# 文件存储服务器端口 (默认 18082，与前端 DEFAULT_SERVER 对齐)
export FILESTORAGE_PORT="${FILESTORAGE_PORT:-18082}"

# ---- 启动 ----
JAR=$(ls spring-support-monitor-starter-*-exec.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "ERROR: 未找到可执行 JAR 文件 (spring-support-monitor-starter-*-exec.jar)"
  exit 1
fi

echo ">>> 启动监控后端 + 文件服务器 (紧凑头模式)"
echo "    JAR: $JAR"
echo "    主端口 (server.port): 8082"
echo "    文件服务器端口: ${FILESTORAGE_PORT} (CORS 直接访问)"
echo "    数据库: MySQL (non-H2)"
echo ""

nohup java $JAVA_OPTS \
  -Dspring-boot.run.profiles.active="${SPRING_PROFILES_ACTIVE:-default}" \
  -Dplugin.filesystem.storage-server.port="${FILESTORAGE_PORT}" \
  -Dspring.datasource.url="${SPRING_DATASOURCE_URL}" \
  -Dspring.datasource.password="${MONITOR_DB_PASSWORD}" \
  -jar "$JAR" > monitor.log 2>&1 &

echo $! > monitor.pid
echo ">>> 后台 PID: $(cat monitor.pid)"
echo ">>> 日志: tail -f monitor.log"
