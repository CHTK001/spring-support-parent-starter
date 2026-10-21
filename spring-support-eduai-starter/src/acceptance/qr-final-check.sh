#!/usr/bin/env bash
# 最终确认：扫码归卷任务（SELF+9001）的判分明细 + 错题登记
set -u
BASE=http://127.0.0.1:19095
TASK=${1:-5482679715c34a99af32b3996a6530db}

TOK=$(curl -s --max-time 20 -X POST "$BASE/v2/user/login" \
  -H 'Content-Type: application/json' \
  --data-binary '{"username":"admin","password":"admin123"}')
T=$(echo "$TOK" | python3 -c "import sys,json
try:
  print(((json.load(sys.stdin).get('data') or {}).get('accessToken') or ''))
except Exception:
  print('')" 2>/dev/null)

echo "--- 任务行 ---"
mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N -e "select marking_task_task_id, marking_task_mode, ifnull(marking_task_paper_id,'NULL'), marking_task_status, marking_task_stage, marking_task_total, marking_task_done_count, marking_task_student_id from eduai.edu_marking_task where marking_task_task_id='$TASK';" 2>&1

echo "--- 判分明细（results）---"
curl -s --max-time 30 "$BASE/v2/edu/marking/result/$TASK" -H "Authorization: Bearer $T" | python3 -c "import sys,json
try:
  d=json.load(sys.stdin).get('data') or {}
  print('taskId', d.get('taskId'))
  print('taskStatus', d.get('taskStatus'), 'stage', d.get('stage'), 'total', d.get('total'), 'totalScore', d.get('totalScore'), 'finished', d.get('finished'))
  for q in (d.get('results') or []):
    print('  Q', json.dumps({k:q.get(k) for k in ('resultId','questionId','no','studentAnswer','maxScore','score','comment','teacherScore','teacherComment')}, ensure_ascii=False)[:400])
except Exception as e:
  print('PARSE_FAIL', e)" 2>&1

echo "--- 错题登记（本次任务学生下的错误行）---"
mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N -e "select wrong_question_id, wrong_question_question_id, wrong_question_student_id, wrong_question_wrong_count, wrong_question_status from eduai.edu_wrong_question where wrong_question_student_id in (select marking_task_student_id from eduai.edu_marking_task where marking_task_task_id='$TASK') order by wrong_question_id desc limit 10;" 2>&1

echo "CHECK_DONE"