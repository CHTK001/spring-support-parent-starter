#!/usr/bin/env bash
# 端到端完整链路：ANY 无 paperId + 真实已作答卷面图（含整卷二维码 PAPER:9001）
# 应归为 SELF+9001，识别题目与作答后按数据库题号判分
set -u
BASE=http://127.0.0.1:19095
IMG=/tmp/combined-9001.png

echo "--- 1. admin 登录 ---"
TOK=$(curl -s --max-time 20 -X POST "$BASE/v2/user/login" \
  -H 'Content-Type: application/json' \
  --data-binary '{"username":"admin","password":"admin123"}')
T=$(echo "$TOK" | python3 -c "import sys,json
try:
  print(((json.load(sys.stdin).get('data') or {}).get('accessToken') or ''))
except Exception:
  print('')" 2>/dev/null)
echo "TOKEN_LEN=${#T}"
if [ -z "$T" ]; then echo "E2E_RESULT=TOKEN_FAIL"; exit 2; fi

echo "--- 2. 扫真实卷面（mode=ANY，不传 paperId）---"
RESP=$(curl -s --max-time 90 -X POST "$BASE/v2/edu/marking/scan" \
  -H "Authorization: Bearer $T" \
  -F "file=@$IMG;type=image/png" \
  -F "mode=ANY" 2>&1)
echo "SCAN_RESP=$(echo "$RESP" | head -c 600)"

TASK=$(echo "$RESP" | python3 -c "import sys,json
try:
  print(json.load(sys.stdin).get('data') or '')
except Exception:
  print('')" 2>/dev/null)
echo "TASK_ID=$TASK"
if [ -z "$TASK" ]; then echo "E2E_RESULT=NO_TASK"; exit 3; fi

echo "--- 3. 任务状态轮询（mode/paperId/status/stage）---"
for i in $(seq 1 20); do
  sleep 6
  ROW=$(mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N -e "select marking_task_mode, ifnull(marking_task_paper_id,'NULL'), marking_task_status, marking_task_stage, marking_task_total, marking_task_done_count from eduai.edu_marking_task where marking_task_task_id='$TASK';" 2>&1)
  echo "ROW[ $i]=$ROW"
  ST=$(echo "$ROW" | awk '{print $3}')
  [ "$ST" = "2" ] || [ "$ST" = "3" ] && break
done

echo "--- 4. 判分明细 ---"
curl -s --max-time 30 "$BASE/v2/edu/marking/result/$TASK" -H "Authorization: Bearer $T" | python3 -c "import sys,json
try:
  d=json.load(sys.stdin).get('data') or {}
  print('RESULT', json.dumps({k:d.get(k) for k in ('taskId','studentName','studentNo','totalScore','grandScore','answerSheetId')}, ensure_ascii=False)[:500])
  qs=d.get('questions') or []
  print('QUESTION_COUNT', len(qs))
  for q in qs[:3]:
    print(' Q', json.dumps({k:q.get(k) for k in ('questionNo','score','aiScore','teacherScore','correct','answer','written')}, ensure_ascii=False)[:300])
except Exception as e:
  print('RESULT_PARSE_FAIL', e)" 2>&1

echo "E2E_RESULT=DONE task=$TASK"