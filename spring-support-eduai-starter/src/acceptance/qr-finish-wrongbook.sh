#!/usr/bin/env bash
set -u
BASE=http://127.0.0.1:19095
TASK=${1:-5482679715c34a99af32b3996a6530db}
STUDENT=${2:-20260001}

TOK=$(curl -s --max-time 20 -X POST "$BASE/v2/user/login" \
  -H 'Content-Type: application/json' \
  --data-binary '{"username":"admin","password":"admin123"}')
T=$(echo "$TOK" | python3 -c "import sys,json
try:
  print(((json.load(sys.stdin).get('data') or {}).get('accessToken') or ''))
except Exception:
  print('')" 2>/dev/null)
if [ -z "$T" ]; then echo "TOKEN_FAIL"; exit 2; fi

echo "--- finish 预览 ---"
curl -s --max-time 30 "$BASE/v2/edu/marking/finish-preview/$TASK" -H "Authorization: Bearer $T" | python3 -m json.tool 2>&1 | head -40

echo "--- 调用 finish ---"
curl -s --max-time 30 -X POST "$BASE/v2/edu/marking/$TASK/finish" -H "Authorization: Bearer $T" | python3 -m json.tool 2>&1 | head -40

echo "--- 错题表（本次任务学生 + 9001 卷题） ---"
mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N -e "select wrong_question_student_id, wrong_question_question_id, wrong_question_paper_id, wrong_question_source_task_id, wrong_question_wrong_count, wrong_question_corrected from eduai.edu_wrong_question where wrong_question_student_id='$STUDENT' and wrong_question_source_task_id='$TASK' order by wrong_question_question_id;" 2>&1

echo "--- 该学生全部错题行 ---"
mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N -e "select wrong_question_student_id, wrong_question_question_id, wrong_question_paper_id, wrong_question_source_task_id, wrong_question_wrong_count, wrong_question_corrected from eduai.edu_wrong_question where wrong_question_student_id='$STUDENT' order by wrong_question_question_id;" 2>&1

echo "CHECK_DONE"