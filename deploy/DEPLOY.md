# 部署指南 —— 后台升级发布到 192.168.50.198

## 前提

- **前端**: 本地 `pnpm dev` 运行在 `http://localhost:8848`
- **后台 JAR**: 已构建在 `deploy/monitor-starter-exec.jar` (706MB)
- **数据库**: MySQL (`172.16.0.40:3306/report`)，**非 H2**
- **SSH**: 当前本地无法直连 192.168.50.198，需要在远程服务器上执行以下步骤

## 步骤 1: 在远程服务器上停止旧进程

```bash
# 登录到 192.168.50.198 后执行：
# 停止项目相关进程 (8081 / 8112 / 18082 / 29181 / 8082)
for port in 8081 8112 18082 29181 8082 19090; do
    pid=$(ss -tlnp 2>/dev/null | grep ":$port " | grep -oP 'pid=\K[0-9]+' | head -1 || true)
    if [ -n "$pid" ]; then
        echo "端口 $port → PID $pid → 停止"
        kill -9 "$pid" 2>/dev/null || true
    fi
done
```

## 步骤 2: 上传新 JAR 到远程服务器

```bash
# 从本地拷贝 JAR 到远程 /opt/monitor/ 目录
# (由于 SSH 无法直连，请通过已有的部署渠道上传)
cp monitor-starter-exec.jar /opt/monitor/spring-support-monitor-starter-4.0.0.42-exec.jar
```

## 步骤 3: 启动后端 (紧凑头 + MySQL)

```bash
cd /opt/monitor

# 设置 MySQL 密码环境变量
export MONITOR_DB_PASSWORD="your_password_here"

# 启动 (紧凑头参数减少内存)
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
    -Dplugin.filesystem.storage-server.mode=standalone \
    -Dplugin.filesystem.storage-server.port=18082 \
    -Dserver.port=8082 \
    -jar spring-support-monitor-starter-4.0.0.42-exec.jar \
    > monitor.log 2>&1 &

echo $! > monitor.pid
echo "后台 PID: $(cat monitor.pid)"
```

## 步骤 4: 启动前端 (本地)

```bash
cd D:/ch/project/vue-support-parent-starter

# 指定 API 目标为远程服务器 (默认 8081，部署后改为 8082)
$env:MS_API_TARGET = "http://192.168.50.198:8082"
pnpm --filter vue-support-monitor-starter dev
```

## 参数说明

| 参数 | 说明 |
|------|------|
| `-XX:+UseCompressedOops` | 压缩对象指针 (32-bit 代替 64-bit，节省内存) |
| `-XX:+UseCompressedClassPointers` | 压缩类指针 (节省 ~1GB 堆内存) |
| `-XX:MaxRAMPercentage=75` | 堆内存最大为可用 RAM 的 75% |
| `-XX:MaxMetaspaceSize=256m` | 元空间限制 256MB |
| `-Xss256k` | 缩小线程栈 (默认 1MB) |
| `-XX:+UseG1GC` | G1 回收器 (低延迟) |
| `-XX:+UseStringDeduplication` | 字符串去重 (节省内存) |
| `-Dplugin.filesystem.storage-server.mode=standalone` | 文件服务器按独立端口启动（默认 embedded 走应用端口+path-prefix） |
| `-Dplugin.filesystem.storage-server.port=18082` | 文件服务器端口 (与前端 DEFAULT_SERVER 对齐) |
| `-Dserver.port=8082` | 主 HTTP 服务端口 |
| `-Dspring.datasource.url=jdbc:mysql://...` | **MySQL** 数据库 (非 H2) |

## 文件服务器 CORS 配置 (已生效)

```yaml
# FileStorageServerProperties.java (默认开启)
plugin.filesystem.storage-server.cors:
  enable: true              # 默认开启
  allowedOrigins: "*"       # 任意来源
  allowCredentials: true    # 允许携带凭据
  allowedHeaders: Content-Type,Authorization,x-oauth-token,X-Requested-With,X-Trace-Id,X-Req-Fingerprint
  exposeHeaders: Content-Disposition,Content-Range,Accept-Ranges,X-FileStorage-*
```

前端通过 CORS 直接访问 `http://192.168.50.198:18082/{bucket}/{key}`，**无需 Vite 代理**。

## 验收要点

1. 访问 `http://localhost:8848` → 登录页面出现
2. 登录后 → 左侧菜单出现 **「文件管理」** 顶级菜单
3. 点击「文件管理」 →「文件资产」 → 文件浏览器加载正常
4. 上传/下载/预览功能正常
5. 压缩包预览功能 (WASM + 后端 fallback)
6. 浏览器 Console 无 CORS 错误
