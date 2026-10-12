#!/bin/bash
# 198 部署：monitor 单实例 + 紧凑头 + MariaDB + 文件网关 18082 校验。
# 形状沿用 .local-run/remote-deploy.sh（其踩坑注释要点全部保留），增补：
#   1) 同项目重复实例（8112）一并停止 —— 「自己项目只能启动一个」
#   2) 启动后额外校验文件存储网关 18082 的监听者 == 新进程
# 用法: bash remote-deploy-198.sh <期望的 /tmp/app-new.jar md5>
set -e
REMOTE_JAR=/opt/ngx-console/app/app.jar
EXPECT="$1"
PORT=8081
DUP_PORT=8112

GOT=$(md5sum /tmp/app-new.jar | cut -d' ' -f1)
if [ "$GOT" != "$EXPECT" ]; then
  echo "  !! 传输校验失败 期望=$EXPECT 实际=$GOT"
  exit 1
fi
echo "  TRANSFER_VERIFIED md5=$GOT"

# ---------------------------------------------------------------------------
# 停掉占着本项目端口的旧进程（8081 主实例 + 8112 重复实例 + 按 jar 路径兜底）。
# 判据用「谁占着端口」而不是「命令行有没有 app.jar」：并发会话可能用相对路径
# -jar app.jar 启动（cwd=/opt/ngx-console/app），按路径匹配抓不到。
# ---------------------------------------------------------------------------
port_pids() {
  ss -lntpH 2>/dev/null | awk -v p=":$PORT" '$4 ~ p {print}' \
    | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u
}
dup_port_pids() {
  ss -lntpH 2>/dev/null | awk -v p=":$DUP_PORT" '$4 ~ p {print}' \
    | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u
}
jar_pids() {
  local p pid cmd
  for p in /proc/[0-9]*; do
    pid=${p#/proc/}
    cmd=$(tr '\0' ' ' < "$p/cmdline" 2>/dev/null) || continue
    case "$cmd" in
      *"-jar ${REMOTE_JAR}"*|*"java"*"-jar app.jar --server.port=${PORT}"*) echo "$pid" ;;
    esac
  done
}

OLD=$( { port_pids; dup_port_pids; jar_pids; } | sort -u | tr '\n' ' ')
if [ -n "$(echo $OLD | tr -d ' ')" ]; then
  echo "  停本项目旧进程(${PORT}/${DUP_PORT}/jar): $OLD"
  for pid in $OLD; do
    kill "$pid" 2>/dev/null || true
  done
  for i in $(seq 1 40); do
    [ -z "$(port_pids)" ] && [ -z "$(dup_port_pids)" ] && [ -z "$(jar_pids)" ] && break
    sleep 1
  done
  for pid in $OLD; do
    if [ -d "/proc/$pid" ]; then kill -9 "$pid" 2>/dev/null || true; fi
  done
  sleep 2
  echo "  旧进程已退出 ${PORT}=[$(port_pids | tr '\n' ' ')] ${DUP_PORT}=[$(dup_port_pids | tr '\n' ' ')]"
else
  echo "  ${PORT}/${DUP_PORT} 上无旧进程"
fi

# 磁盘仅剩 ~1.7G：cp 覆盖前先确认可用（cp 对已存在目标 O_TRUNC，原地复用块）
AVAIL=$(df -m /opt | awk 'NR==2{print $4}')
echo "  /opt 可用=${AVAIL}MB"
if [ "$AVAIL" -lt 760 ]; then
  echo "  !! /opt 可用空间不足 760MB，拒绝覆盖（防止写一半 ENOSPC 损坏 jar）"
  exit 5
fi

cp /tmp/app-new.jar "$REMOTE_JAR"
cd /opt/ngx-console/app

# 日志按时间戳分文件，避免与并发会话互相 truncate（实测两方都写 app.log 会得到 0 字节）
LOG=/opt/ngx-console/app/app-$(date +%Y%m%d-%H%M%S).log
# datasource 必须显式注入：本仓 application.yml 里的默认 datasource 指向
# 172.16.0.40/report（开发默认值），不是 198 上的 MariaDB。
# 不注入就直接死在 "Unable to determine Dialect without JDBC metadata"
# （实测 2026-10-05，我第一次起实例就是死在这）。
# 【踩坑，勿改】oauth.protocol 必须是 static。
#   写成 https 时 Protocol 解析为 null，登录接口直接
#   NullPointerException（DefaultProtocolExecutor:57）-> 500，
#   前端被重定向到 #/login，**所有浏览器验收都进不去应用**。
# 【踩坑，勿改】必须带 --plugin.api.cors.allowed-origins 包含前端地址。
#   vite 代理会把浏览器的 Origin 原样转发给后端，后端按 CORS 校验；
#   不配就直接回 "Invalid CORS request"，
#   浏览器里所有 /ms 请求失败 -> 页面空白、验收全红，但后端接口直连却是 200。
# 【踩坑，勿改】oauth.protocol=static 时必须配 plugin.oauth.temp.user（格式 用户名:口令;…），
#   否则登录接口返回「静态临时账号未配置」，前端被重定向到 #/login，
#   **浏览器验收一条都跑不了**。签发与鉴权共用这份解析（StaticTempLogin）。
export SPRING_APPLICATION_JSON='{"spring":{"datasource":{"url":"jdbc:mysql://127.0.0.1:3306/ngxacc?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","driver-class-name":"com.mysql.cj.jdbc.Driver","username":"ngx","password":"Ngx#2026db"},"jpa":{"database-platform":"org.hibernate.dialect.MariaDBDialect","properties":{"jakarta.persistence.jdbc.url":"jdbc:mysql://127.0.0.1:3306/ngxacc?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","jakarta.persistence.jdbc.user":"ngx","jakarta.persistence.jdbc.password":"Ngx#2026db"}}}}'
nohup java -XX:+UseCompactObjectHeaders -Xmx1500m -Dfile.encoding=UTF-8 -jar "$REMOTE_JAR" \
  --server.port=$PORT --plugin.oauth.enable=true --plugin.oauth.protocol=static \
  --plugin.api.cors.allowed-origins=http://localhost:8848,http://127.0.0.1:8848,http://192.168.50.101:8848,http://192.168.50.198 \
  --plugin.oauth.temp.user=admin:admin123 \
  > "$LOG" 2>&1 &
NEW_PID=$!
echo "  新进程 pid=$NEW_PID 日志=$LOG"

# 等端口真的起来，而不是死等固定秒数（实测冷启动 262s：AI/ONNX 预热+DB 列检查，
# 原 180s 窗口会在启动成功前误判失败，故放宽到 360s）
READY=0
for i in $(seq 1 180); do
  if ss -lnt 2>/dev/null | grep -q ":${PORT} "; then READY=$i; break; fi
  if ! kill -0 "$NEW_PID" 2>/dev/null; then
    echo "  !! 进程已退出，日志尾部："
    tail -25 "$LOG" | sed 's/^/     /'
    exit 2
  fi
  sleep 2
done

echo "  jar md5=$(md5sum $REMOTE_JAR | cut -d' ' -f1)"
echo "  紧凑头=$(tr '\0' ' ' < /proc/$NEW_PID/cmdline | grep -c UseCompactObjectHeaders)"
echo "  RSS=$(grep VmRSS /proc/$NEW_PID/status | awk '{print $2}') kB"
echo "  监听${PORT}进程数=$(ss -lntp 2>/dev/null | grep -c ":${PORT} ")"
if [ "$READY" = "0" ]; then
  echo "  !! 360s 内未监听 ${PORT}，日志尾部："
  tail -25 "$LOG" | sed 's/^/     /'
  exit 3
fi
echo "  端口就绪耗时=$((READY * 2))s"

# 【关键校验，勿删】监听 ${PORT} 的进程，跑的必须**就是刚部署上去的这个 jar**。
#
#   为什么判据是「jar 的 md5」而不是「监听者 pid == 我起的 pid」：
#   后者在两种常见情况下会误报 —— (1) 启动脚本派生 java 子进程，监听者是孙进程；
#   (2) 并发会话同时部署，同一个 jar 被起了两个实例，晚绑上端口的反而是别人的 pid。
#   实测 2026-10-11 踩中 (2)：我起的 425648 抢不到端口退出，425685（同 jar、
#   并发会话起的）持有 8081，脚本判 exit=4，而线上跑的确实就是新包。
#   真正要防的是「验收打到了别人的实例上、数字全绿但测的不是新代码」，
#   它的充分条件是「监听者跑的 jar 不是这个 md5」，不是 pid 相等。
LISTENER=$(ss -lntpH 2>/dev/null | awk -v pat=":${PORT}\$" '$4 ~ pat {print}' \
  | grep -oE 'pid=[0-9]+' | head -1 | cut -d= -f2)
if [ -z "$LISTENER" ]; then
  echo "  !! 找不到 ${PORT} 的监听者"
  exit 4
fi
# 该进程**实际打开**的 jar 文件，不是磁盘上现在那个。
# cp 覆盖是「就地改写」，mv 替换是「新 inode」；若 jar 是被替换的，
# 旧进程仍持有旧 inode，此时 /proc/PID/fd 里的路径会带 " (deleted)"。
LISTENER_JAR=$(ls -l /proc/"$LISTENER"/fd/ 2>/dev/null | grep -oE '/[^ ]*app\.jar( \(deleted\))?' | head -1)
if [ -z "$LISTENER_JAR" ]; then
  echo "  !! 监听者 pid=$LISTENER 没打开任何 app.jar —— 它不是本应用在服务"
  exit 4
fi
case "$LISTENER_JAR" in
  *" (deleted)")
    echo "  !! 监听者 pid=$LISTENER 持有的是已被替换掉的旧 jar inode（$LISTENER_JAR）"
    echo "     端口上跑的是旧代码，必须杀掉它再重新部署"
    exit 4
    ;;
esac
LISTENER_MD5=$(md5sum "$LISTENER_JAR" | cut -d' ' -f1)
if [ "$LISTENER_MD5" != "$EXPECT" ]; then
  echo "  !! ${PORT} 的监听者 pid=$LISTENER 跑的 jar md5=$LISTENER_MD5"
  echo "     期望=$EXPECT —— 端口上跑的不是本次部署的包，验收会打到别人的代码"
  exit 4
fi
if [ "$LISTENER" = "$NEW_PID" ]; then
  echo "  LISTENER_RUNS_DEPLOYED_JAR_OK (pid=$LISTENER 即本进程)"
else
  # 同一个 jar 的重复实例：不是部署失败，但必须报出来 ——
  # 多一个 JVM 就是多一份 1.5G 堆和一份 CPU，且来源不明。
  echo "  LISTENER_RUNS_DEPLOYED_JAR_OK (pid=$LISTENER，非本进程 pid=$NEW_PID；"
  echo "     两者跑同一个 md5，属并发会话起的重复实例)"
  echo "  !! 注意：本进程 pid=$NEW_PID 的存活状态请自行确认，重复实例会白占一份内存"
fi

# --- 增补：文件存储网关 18082 必须由本进程持有（monitor 新配置随应用启动） ---
FS_PORT=18082
for i in $(seq 1 30); do
  if ss -lnt 2>/dev/null | grep -q ":${FS_PORT} "; then break; fi
  sleep 2
done
FS_LISTENER=$(ss -lntpH 2>/dev/null | awk -v pat=":${FS_PORT}\$" '$4 ~ pat {print}' \
  | grep -oE 'pid=[0-9]+' | head -1 | cut -d= -f2)
if [ "$FS_LISTENER" = "$NEW_PID" ]; then
  echo "  FILE_GATEWAY_OK (pid=$NEW_PID 监听 ${FS_PORT})"
elif [ -n "$FS_LISTENER" ]; then
  echo "  !! 文件网关 ${FS_PORT} 被其它进程占用: pid=$FS_LISTENER"
  exit 6
else
  echo "  !! 文件网关 ${FS_PORT} 未监听，日志中的文件服务器记录："
  grep -a '文件服务器' "$LOG" | tail -5 | sed 's/^/     /'
  exit 7
fi
grep -a '文件服务器已启动' "$LOG" | tail -2 | sed 's/^/     /'
echo "  DEPLOY_DONE"
