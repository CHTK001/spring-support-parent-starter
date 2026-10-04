#!/bin/sh
# 重启续跑验证：提交任务 -> 任务处理中重启进程 -> 确认任务被续跑并到达终态
B=http://127.0.0.1:19095
DB="mysql -h127.0.0.1 -P3306 -ueduai -peduai123 -N -B eduai"
S() { printf 'kali\n' | sudo -S "$@"; }

echo "MD5_BEFORE=$(md5sum /home/kali/eduai/backend/eduai-backend.jar | cut -c1-32)"
TOK=$(curl -s -m 20 -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' $B/v2/user/login | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
echo "token 长度=${#TOK}"

echo "== 1. 提交 3 页扫描（异步）=="
TID=$(curl -s -m 90 -H "Authorization: Bearer $TOK" \
  -F "file=@/home/kali/hard-c1.png" -F "file=@/home/kali/hard-c2.png" -F "file=@/home/kali/hard-c3.png" \
  -F "mode=SELF" -F "paperId=9003" $B/v2/edu/marking/scan | sed -n 's/.*"data"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
echo "  taskId=$TID"

echo "== 2. 等待进入处理中（最多 30s）=="
j=0
while [ $j -lt 10 ]; do
  sleep 3; j=$((j+1))
  st=$($DB -e "select marking_task_status from edu_marking_task where marking_task_task_id='$TID';" 2>/dev/null)
  echo "  t=$((j*3))s status=$st"
  [ "$st" = "1" ] && break
done

echo "== 3. 在处理中重启进程（模拟崩溃/升级）=="
S systemctl restart eduai-backend >/dev/null 2>&1
for i in $(seq 1 60); do
  c=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' $B/v2/user/login 2>/dev/null)
  [ "$c" = "200" ] && { echo "  重启后就绪（约 $((i*5))s）"; break; }
  sleep 5
done

echo "== 4. 轮询该任务是否被续跑至终态（最多 300s）=="
k=0
while [ $k -lt 100 ]; do
  sleep 3; k=$((k+1))
  st=$($DB -e "select marking_task_status from edu_marking_task where marking_task_task_id='$TID';" 2>/dev/null)
  if [ "$st" = "2" ] || [ "$st" = "3" ]; then break; fi
done
echo "  终态 status=$st（2=完成 3=失败），耗时约 $((k*3))s"
$DB -e "select concat('  total=',marking_task_total,' done=',marking_task_done_count,' stage=',marking_task_stage,' err=',ifnull(left(marking_task_error,60),'-')) from edu_marking_task where marking_task_task_id='$TID';" 2>&1
$DB -e "select concat('  结果行数=',count(*),' 满分项=',sum(marking_result_ai_score=(select max(r2.marking_result_ai_score) from edu_marking_result r2 where r2.marking_result_task_id='$TID'))) from edu_marking_result where marking_result_task_id='$TID';" 2>&1
echo "  续跑期间日志（最近 4 条与恢复相关）:"
grep -a -iE "resume|续跑|resumable|恢复" /home/kali/eduai/logs/be.out.log 2>/dev/null | tail -4 | sed 's/.*EduAi\]/    EduAi]/' | cut -c1-120
echo "MD5_AFTER=$(md5sum /home/kali/eduai/backend/eduai-backend.jar | cut -c1-32)"