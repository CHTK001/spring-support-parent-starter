#!/bin/sh
# ============================================================================
# job 模块冒烟验收（只读，秒级，可重复执行）
#
# 用途：把本会话做过的关键验收固化成一条可重放的命令，避免"验收只存在于聊天记录里"。
# 设计：全部为只读检查（不改数据、不启停服务），因此可以随时跑、可做回归。
#
# 用法（在 192.168.50.198 上执行）：
#   export JOB_DB_USER=...        # 必填：数据库账号
#   export JOB_DB_PASS=...        # 必填：数据库口令
#   sh smoke.sh
# 可选环境变量（含默认值）：
#   JOB_API=http://127.0.0.1:18170/system/api     服务基址
#   JOB_DB_HOST=127.0.0.1  JOB_DB_PORT=3306  JOB_DB_NAME=serverdb
#   JOB_UA='Mozilla/5.0 Chrome/140'              必须用浏览器 UA（服务端按 UA 拦自动化客户端）
#
# 需要 Redis 相关的验收（选主切换/唯一性/优雅释放）见文件末尾的"手工步骤"，
# 那些要起停临时实例，不适合放进秒级只读套件。
# ============================================================================
set -u
API="${JOB_API:-http://127.0.0.1:18170/system/api}"
UA="${JOB_UA:-Mozilla/5.0 Chrome/140}"
DBH="${JOB_DB_HOST:-127.0.0.1}"
DBP="${JOB_DB_PORT:-3306}"
DBN="${JOB_DB_NAME:-serverdb}"
DBU="${JOB_DB_USER:?请设置 JOB_DB_USER}"
DBPW="${JOB_DB_PASS:?请设置 JOB_DB_PASS}"
M="/usr/bin/mysql -h $DBH -P $DBP -u$DBU -p$DBPW $DBN -N -B"
PASS=0
FAIL=0
ok()   { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
note() { echo "  ---- $1"; }

echo "===== 1. 服务形态：单实例 / 活跃 / 健康 ====="
N=$(ps -eo args | grep -c '[s]pring-api-support-system')
[ "$N" = "1" ] && ok "实例数=1" || bad "实例数=$N（应为 1）"
A=$(systemctl is-active system-api 2>/dev/null)
[ "$A" = "active" ] && ok "systemd is-active=active" || bad "is-active=$A"
H=$(curl -s -o /dev/null -w '%{http_code}' -A "$UA" "$API/actuator/health")
[ "$H" = "200" ] && ok "health=200" || bad "health=$H"

echo "===== 2. 紧凑对象头（减少内存） ====="
PID=$(ps -eo pid,args | grep '[s]pring-api-support-system' | awk '{print $1}' | head -n1)
if [ -n "$PID" ]; then
  J=$(echo kali | sudo -S jinfo -flag UseCompactObjectHeaders "$PID" 2>/dev/null | tail -n1)
  case "$J" in *UseCompactObjectHeaders*) ok "紧凑头已生效：$J" ;; *) bad "紧凑头未生效：$J" ;; esac
else
  bad "取不到进程 pid"
fi

echo "===== 3. 数据库不是 H2 ====="
DB=$(curl -s -A "$UA" "$API/actuator/health" | tr ',' '\n' | grep -o '"database" *: *"[^"]*"' | head -n1)
case "$DB" in *MySQL*) ok "database=MySQL（非 H2）：$DB" ;; *) bad "database 非 MySQL：$DB" ;; esac

echo "===== 4. 选主租约新鲜度（库时钟判定） ====="
LEAD=$($M -e "SELECT IFNULL(sys_job_cluster_leader_node,'') FROM sys_job_cluster WHERE sys_job_cluster_id='system'" 2>/dev/null)
[ -n "$LEAD" ] && ok "leader=$LEAD" || bad "leader 为空"
LAG=$($M -e "SELECT CAST(sys_job_cluster_lease_until_ms AS SIGNED) - FLOOR(UNIX_TIMESTAMP(NOW(3))*1000) FROM sys_job_cluster WHERE sys_job_cluster_id='system'" 2>/dev/null | tail -n1)
if [ -n "$LAG" ] && [ "$LAG" -gt 0 ] && [ "$LAG" -le 20000 ]; then
  ok "lease−库时钟=${LAG}ms（应在 0 与租约时长之间）"
else
  bad "lease−库时钟=${LAG}ms（异常）"
fi

echo "===== 5. 无重复派发（按 任务+触发时刻 聚合） ====="
TOKEN=$(curl -s -A "$UA" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' "$API/v2/user/login" \
  | tr -d '\n' | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  bad "登录失败，跳过 5/6/7"
else
  ok "登录成功（token 长度 ${#TOKEN}）"
  DUP=$(curl -s -A "$UA" -H "Authorization: Bearer $TOKEN" "$API/v2/job/log/page?pageNum=1&pageSize=120" \
    | tr -d ' \n' | sed 's/},{/}\n{/g' \
    | while IFS= read -r r; do
        k=$(echo "$r" | sed -n 's/.*"jobKey":"\([^"]*\)".*/\1/p')
        t=$(echo "$r" | sed -n 's/.*"triggerTime":\([0-9]*\).*/\1/p')
        [ -n "$k" ] && [ -n "$t" ] && echo "$k $t"
      done | sort | uniq -c | awk '$1>1{n++} END{print n+0}')
  [ "$DUP" = "0" ] && ok "同一任务同一触发时刻重复组合=0" || bad "重复组合=$DUP（疑似重复派发）"

  echo "===== 6. 乐观锁：过期版本必须被拒 ====="
  echo "  ---- 注意：本项对 test 行发起一次【注定被拒】的保存，不改动任何数据 ----"
  KV=$($M -e "SELECT sys_job_execute_bean FROM sys_job WHERE sys_job_execute_bean='test' AND sys_job_is_deleted=0" 2>/dev/null)
  if [ -z "$KV" ]; then
    note "无 test 行，跳过本项"
  else
    V=$($M -e "SELECT sys_job_version FROM sys_job WHERE sys_job_execute_bean='test'" 2>/dev/null)
    STALE=$(( ${V:-0} + 999 ))
    CODE=$(curl -s -A "$UA" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -X POST \
      -d "{\"jobName\":\"smoke-stale\",\"jobKey\":\"test\",\"group\":\"default\",\"scheduleType\":\"NONE\",\"enabled\":false,\"glueType\":\"BEAN\",\"version\":$STALE}" \
      "$API/v2/job/save" | tr -d ' \n' | sed -n 's/.*"code":"\([^"]*\)".*/\1/p')
    V2=$($M -e "SELECT sys_job_version FROM sys_job WHERE sys_job_execute_bean='test'" 2>/dev/null)
    [ "$CODE" = "C0400S0000" ] && ok "过期版本被拒（code=$CODE）" || bad "过期版本未被拒（code=$CODE）"
    [ "$V" = "$V2" ] && ok "版本未被推进（$V）" || bad "版本被推进 $V→$V2（数据被改动！）"
  fi

  echo "===== 7. 选主 SPI：默认路径不切 Redis ====="
  C=$(journalctl -u system-api --since '-10min' --no-pager 2>/dev/null | grep -ac 'resolver=redis')
  [ "$C" = "0" ] && ok "近 10 分钟无 resolver=redis 切换（默认走 DB）" || bad "出现 $C 条 redis 切换日志"
fi

echo
echo "===== 结果：PASS=$PASS  FAIL=$FAIL ====="
[ "$FAIL" = "0" ] && echo "SMOKE_OK" || echo "SMOKE_FAILED"

cat <<'REDIS_DOC'

===== 附：Redis 选主的手工验收步骤（需起停临时实例，不放进只读套件）=====
前置：app 用的是 Redis DB 6（redis://localhost:6379/6），redis-cli 必须带 -n 6；
      两个临时实例会与现网抢同一批固定端口（18080 等），故测试期先停现网：
        echo <pw> | sudo -S systemctl stop system-api
      每步等启动要 60~100s（慢时更久）；SIGTERM 后至少等 45s 再取样，且不要用 kill -9。

R1 被选中：起一个实例带 --plugin.job.cluster.resolver=redis
   期望：日志出现「选主扩展已注册键 = [IN-MEMORY, REDIS]」且「经 SPI 切换 resolver=redis」、无「未注册该选主实现」
R2 唯一性：redis-cli -n 6 set job:leader:<group> foreign-node EX 300，再起实例
   期望：该实例 抢到=0、无「本地标记为 Leader」、无 Redis 异常；键仍为 foreign-node
R3 租约：不预置键，起实例
   期望：redis-cli -n 6 get job:leader:<group> = <nodeId>，TTL 在 (0, lease*1000]
R4 优雅释放：kill <pid>（SIGTERM）后等 45s
   期望：键为空；日志有「Leader 心跳已停止」且无「释放 Leader 异常」
R5 租约丢失接管：起 A 后删键，再起 B（或等 B 的心跳）
   期望：下一个心跳周期（lease/3）内键变为 B
坏 Redis：不配 resolver 也照样启动失败（redissonClient Bean 创建失败）——平台既有行为，
      与选主实现无关；resolver=redis 时不会误当选，但进程同样退出。
REDIS_DOC
