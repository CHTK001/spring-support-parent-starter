#!/usr/bin/env bash
# Identity QR production E2E:
#   PAPER:9001;STUDENT:20260001;EXAM:88 on a real answer sheet.
# Scope: ANY+no params should auto become SELF/9001, bind student/exam,
# reject duplicates and detect identity (cheating) conflicts.
set -u

BASE=http://127.0.0.1:19095
IMG=/tmp/combined-9001.png
DB_CMD="mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N"

fail() { echo "E2E_FAIL $*"; exit 1; }

echo "--- login ---"
TOK=$(curl -s --max-time 20 -X POST "$BASE/v2/user/login" \
  -H 'Content-Type: application/json' \
  --data-binary '{"username":"admin","password":"admin123"}')
T=$(printf '%s' "$TOK" | python3 -c "import sys,json
try: print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')
except Exception: print('')")
[ -n "$T" ] || fail TOKEN
echo "TOKEN_LEN=${#T}"

echo "--- scan ANY no params (identity QR) ---"
RESP=$(curl -s --max-time 90 -X POST "$BASE/v2/edu/marking/scan" \
  -H "Authorization: Bearer $T" \
  -F "file=@$IMG;type=image/png" \
  -F "mode=ANY")
echo "SCAN_RESP=$(printf '%s' "$RESP" | head -c 400)"
TASK=$(printf '%s' "$RESP" | python3 -c "import sys,json
try: print(json.load(sys.stdin).get('data') or '')
except Exception: print('')")
[ -n "$TASK" ] || fail NO_TASK

echo "--- task row: mode/paper/student/exam ---"
ROW=$($DB_CMD -e "select marking_task_mode, ifnull(marking_task_paper_id,'NULL'), ifnull(marking_task_student_id,'NULL'), ifnull(marking_task_exam_id,'NULL'), marking_task_status from eduai.edu_marking_task where marking_task_task_id='$TASK';")
echo "TASK_ROW=$ROW"
MODE=$(printf '%s' "$ROW" | awk '{print $1}')
PAPER=$(printf '%s' "$ROW" | awk '{print $2}')
STUDENT=$(printf '%s' "$ROW" | awk '{print $3}')
EXAM=$(printf '%s' "$ROW" | awk '{print $4}')
[ "$MODE" = "SELF" ] || fail "MODE=$MODE"
[ "$PAPER" = "9001" ] || fail "PAPER=$PAPER"
[ "$STUDENT" = "20260001" ] || fail "STUDENT=$STUDENT"
[ "$EXAM" = "88" ] || fail "EXAM=$EXAM"
echo "BIND_OK mode=$MODE paper=$PAPER student=$STUDENT exam=$EXAM"

echo "--- duplicate scan (expect rejected) ---"
DUP=$(curl -s --max-time 90 -X POST "$BASE/v2/edu/marking/scan" \
  -H "Authorization: Bearer $T" \
  -F "file=@$IMG;type=image/png" \
  -F "mode=ANY")
echo "DUP_RESP=$(printf '%s' "$DUP" | head -c 300)"
case "$DUP" in
  *'请勿重复扫描提交'*) echo "DUP_OK";;
  *) fail "DUP=$DUP";;
esac

echo "--- mismatch student (expect cheating alert) ---"
CHEAT=$(curl -s --max-time 90 -X POST "$BASE/v2/edu/marking/scan" \
  -H "Authorization: Bearer $T" \
  -F "file=@$IMG;type=image/png" \
  -F "mode=ANY" \
  -F "studentId=20260002")
echo "CHEAT_RESP=$(printf '%s' "$CHEAT" | head -c 300)"
case "$CHEAT" in
  *'替考告警'*) echo "CHEAT_OK";;
  *) fail "CHEAT=$CHEAT";;
esac

echo "--- wait marking done ---"
for i in $(seq 1 60); do
  sleep 10
  ROW=$($DB_CMD -e "select marking_task_status, marking_task_stage from eduai.edu_marking_task where marking_task_task_id='$TASK';")
  echo "PROGRESS[$i]=$ROW"
  ST=$(printf '%s' "$ROW" | awk '{print $1}')
  [ "$ST" = "2" ] && break
  [ "$ST" = "3" ] && fail "MARKING_FAILED row=$ROW"
done
[ "$ST" = "2" ] || fail "MARKING_TIMEOUT"

echo "--- result summary ---"
curl -s --max-time 30 "$BASE/v2/edu/marking/result/$TASK" -H "Authorization: Bearer $T" | python3 -c "import sys,json
try:
  d=json.load(sys.stdin).get('data') or {}
  rs=d.get('results') or []
  print('RESULT', json.dumps({k:d.get(k) for k in ('taskId','studentName','studentNo','totalScore','grandScore')}, ensure_ascii=False)[:500])
  print('QUESTION_COUNT', len(rs))
  print('FIRST_3', json.dumps([{k:q.get(k) for k in ('no','questionId','score','maxScore','teacherScore')} for q in rs[:3]], ensure_ascii=False)[:500])
except Exception as e:
  print('RESULT_PARSE_FAIL', e)"

echo "E2E_IDENTITY=OK task=$TASK"