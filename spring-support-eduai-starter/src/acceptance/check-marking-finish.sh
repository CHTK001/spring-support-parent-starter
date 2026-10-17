#!/bin/sh
# 阅卷「完成」验收（在 eduai 后端所在主机上对 127.0.0.1:19095 执行）
#
# 验的是什么：
#   老师点「完成」-> 该卷按**最终分**（老师分优先，未改则 AI 分）判为错的题，
#   归入**对应学生**的错题库，并带上**试卷 id**；重复完成不重复计数；未识别学生时拒绝。
#
# 受控：所有写入在结尾按 id 精确回滚（错题行 + 任务完成标记），恢复运行前状态。
BASE=http://127.0.0.1:19095
SSH_PWD=kali
PASS=0
FAIL=0
ok()  { PASS=$((PASS + 1)); echo "  PASS  $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  FAIL  $1"; }
has() { case "$1" in *"$2"*) return 0 ;; *) return 1 ;; esac; }
sql() { echo "$SSH_PWD" | sudo -S mysql -N -e "$1" 2>/dev/null; }

# ---- 选任务：学生（非匿名）+ 有试卷 id + 有结果行 ----
TASK=$(sql "SELECT t.marking_task_task_id FROM eduai.edu_marking_task t WHERE t.marking_task_status=2 AND t.marking_task_paper_id IS NOT NULL AND t.marking_task_student_id IS NOT NULL AND t.marking_task_student_id<>'' AND t.marking_task_student_id<>'anonymous' AND (SELECT COUNT(*) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id=t.marking_task_task_id) > 0 ORDER BY t.marking_task_id DESC LIMIT 1;")
[ -n "$TASK" ] || { echo "找不到可用任务"; exit 1; }
STU=$(sql "SELECT marking_task_student_id FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")
PAPER=$(sql "SELECT marking_task_paper_id FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")
PREV_FIN=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")
echo "task=$TASK student=$STU paper=$PAPER teacher_finished_before=$PREV_FIN"

# 期望登记数：最终分（老师分优先）<=0 且 question_id>0
EXPECT=$(sql "SELECT COUNT(*) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id='$TASK' AND r.marking_result_question_id>0 AND (CASE WHEN r.marking_result_teacher_score IS NOT NULL THEN r.marking_result_teacher_score ELSE IFNULL(r.marking_result_ai_score,0) END) <= 0;")
echo "expected_wrong_count=$EXPECT"

sql "SELECT wrong_question_id FROM eduai.edu_wrong_question;" | sort > /tmp/wq_before.txt
BEFORE_N=$(wc -l < /tmp/wq_before.txt | tr -d ' ')

echo "###### 1) 第一次「完成」 ######"
R1=$(curl -s -X POST "$BASE/v2/edu/marking/$TASK/finish")
echo "  resp=$(printf '%s' "$R1" | tr -d '\n' | head -c 400)"
if has "$R1" '"code" : "00000"'; then ok "接口返回成功"; else bad "接口未成功"; fi
if has "$R1" "\"studentId\" : \"$STU\""; then ok "studentId=$STU（对应学生）"; else bad "studentId 不符"; fi
if has "$R1" "\"paperId\" : $PAPER"; then ok "paperId=$PAPER（试卷归因）"; else bad "paperId 不符（期望 $PAPER）"; fi
if has "$R1" "\"wrongCount\" : $EXPECT"; then ok "wrongCount=$EXPECT（与最终分判定一致）"; else bad "wrongCount 与期望($EXPECT)不符"; fi
if has "$R1" '"alreadyFinished" : false'; then ok "首次完成 alreadyFinished=false"; else bad "首次 alreadyFinished 应为 false"; fi

sql "SELECT wrong_question_id FROM eduai.edu_wrong_question;" | sort > /tmp/wq_after1.txt
NEW=$(comm -13 /tmp/wq_before.txt /tmp/wq_after1.txt)
NEW_N=$(printf '%s\n' "$NEW" | grep -c . || true)
if [ "$NEW_N" = "$EXPECT" ]; then ok "实际新增错题行数 == 期望（$EXPECT）"; else bad "新增行数 $NEW_N != 期望 $EXPECT"; fi

if [ "$NEW_N" -gt 0 ]; then
  IDS=$(printf '%s' "$NEW" | paste -sd, -)
  W_DOM=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_id IN ($IDS) AND wrong_question_student_id='$STU';")
  W_PAP=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_id IN ($IDS) AND wrong_question_paper_id=$PAPER;")
  W_TM=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_id IN ($IDS) AND wrong_question_last_wrong_time IS NOT NULL;")
  [ "$W_DOM" = "$NEW_N" ] && ok "新增错题全部归属学生 $STU" || bad "归属学生 $STU 的仅 $W_DOM/$NEW_N"
  [ "$W_PAP" = "$NEW_N" ] && ok "新增错题全部带试卷 id=$PAPER" || bad "带试卷 id 的仅 $W_PAP/$NEW_N"
  [ "$W_TM" = "$NEW_N" ] && ok "新增错题均有最近错误时间" || bad "有错误时间的仅 $W_TM/$NEW_N"
fi
FIN1=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")
[ "$FIN1" = "1" ] && ok "任务已标记 teacher_finished=1" || bad "teacher_finished 未置 1"

echo "###### 2) 第二次「完成」（幂等） ######"
R2=$(curl -s -X POST "$BASE/v2/edu/marking/$TASK/finish")
echo "  resp=$(printf '%s' "$R2" | tr -d '\n' | head -c 300)"
has "$R2" '"alreadyFinished" : true' && ok "重复完成 alreadyFinished=true" || bad "重复完成未识别"
has "$R2" '"wrongCount" : 0' && ok "重复完成 wrongCount=0（不重复计数）" || bad "重复完成仍计数"
sql "SELECT wrong_question_id FROM eduai.edu_wrong_question;" | sort > /tmp/wq_after2.txt
cmp -s /tmp/wq_after1.txt /tmp/wq_after2.txt && ok "错题行集合未变（幂等）" || bad "重复完成改动了错题行"

echo "###### 3) 未识别学生（anonymous）时拒绝完成 ######"
T2=$(sql "SELECT t.marking_task_task_id FROM eduai.edu_marking_task t WHERE t.marking_task_status=2 AND t.marking_task_student_id='anonymous' AND (SELECT COUNT(*) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id=t.marking_task_task_id) > 0 ORDER BY t.marking_task_id DESC LIMIT 1;")
if [ -z "$T2" ]; then
  bad "找不到 student=anonymous 且有结果的已完成任务"
else
  echo "  匿名任务=$T2"
  F3_BEFORE=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$T2';")
  sql "SELECT wrong_question_id FROM eduai.edu_wrong_question;" | sort > /tmp/wq_before3.txt
  R3=$(curl -s -X POST "$BASE/v2/edu/marking/$T2/finish")
  echo "  resp=$(printf '%s' "$R3" | tr -d '\n' | head -c 240)"
  has "$R3" '修改学生' && ok "匿名卷 -> 业务拒绝并提示先设置学生" || bad "匿名卷未按预期拒绝"
  has "$R3" 'S0512C0000' && bad "匿名卷返回 500" || ok "匿名卷不是 500"
  sql "SELECT wrong_question_id FROM eduai.edu_wrong_question;" | sort > /tmp/wq_after3.txt
  cmp -s /tmp/wq_before3.txt /tmp/wq_after3.txt && ok "被拒绝时未写入任何错题" || bad "被拒绝却写入了错题"
  F3=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$T2';")
  [ "$F3" = "$F3_BEFORE" ] && ok "被拒绝时完成标记未变（$F3_BEFORE）" || bad "被拒绝却改了完成标记（$F3_BEFORE -> $F3）"
  sql "UPDATE eduai.edu_marking_task SET marking_task_teacher_finished=$F3_BEFORE, marking_task_teacher_finish_time=NULL WHERE marking_task_task_id='$T2';"
fi

echo "###### 4) 回滚本次写入 ######"
if [ "$NEW_N" -gt 0 ]; then
  IDS=$(printf '%s' "$NEW" | paste -sd, -)
  sql "DELETE FROM eduai.edu_wrong_question WHERE wrong_question_id IN ($IDS);"
fi
sql "UPDATE eduai.edu_marking_task SET marking_task_teacher_finished=$PREV_FIN, marking_task_teacher_finish_time=NULL WHERE marking_task_task_id='$TASK';"
AFTER_N=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question;")
[ "$AFTER_N" = "$BEFORE_N" ] && ok "错题表行数回到运行前（$BEFORE_N）" || bad "错题表行数 $AFTER_N != 运行前 $BEFORE_N"
REST=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")
[ "$REST" = "$PREV_FIN" ] && ok "任务完成标记已回滚" || bad "任务完成标记未回滚（$REST != $PREV_FIN）"

echo
echo "###### 汇总: PASS=$PASS FAIL=$FAIL ######"
[ "$FAIL" -eq 0 ] || exit 1
