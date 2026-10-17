#!/bin/sh
# 阅卷完成验收的库侧助手：snapshot / assert / rollback
# 用法: sh db-marking-finish.sh <snapshot|assert-nowrite|assert-write|rollback> <taskId>
BASE_TASK="${2:-416151d0cbb64f5b82b28c705681843b}"
PAPER=9043
STU=2026000001
sql() { echo kali | sudo -S mysql -N -e "$1" 2>/dev/null; }
anon() { sql "SELECT t.marking_task_task_id FROM eduai.edu_marking_task t WHERE t.marking_task_status=2 AND t.marking_task_student_id='anonymous' AND (SELECT COUNT(*) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id=t.marking_task_task_id) > 0 ORDER BY t.marking_task_id DESC LIMIT 1;"; }

case "$1" in
  snapshot)
    echo "TASK=$BASE_TASK"
    echo "ANON=$(anon)"
    echo "TOTAL=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question;")"
    echo "PAPER_ROWS=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_paper_id=$PAPER;")"
    echo "FLAG=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$BASE_TASK';")"
    ;;
  assert-nowrite)
    T=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question;")
    R=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_paper_id=$PAPER;")
    F=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$BASE_TASK';")
    echo "TOTAL=$T PAPER_ROWS=$R FLAG=$F"
    if [ "$R" = "0" ] && [ "$F" = "0" ]; then echo "NOWRITE_OK"; else echo "NOWRITE_BAD"; fi
    ;;
  assert-write)
    R=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_paper_id=$PAPER;")
    S=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_paper_id=$PAPER AND wrong_question_student_id='$STU';")
    F=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$BASE_TASK';")
    echo "PAPER_ROWS=$R STUDENT_ROWS=$S FLAG=$F"
    if [ "$R" = "$S" ] && [ "$R" -gt 0 ] && [ "$F" = "1" ]; then echo "WRITE_OK"; else echo "WRITE_BAD"; fi
    ;;
  reset-anon)
    sql "UPDATE eduai.edu_marking_task SET marking_task_teacher_finished=0, marking_task_teacher_finish_time=NULL WHERE marking_task_student_id='anonymous';"
    echo "anon_flag_sum=$(sql "SELECT IFNULL(SUM(IFNULL(marking_task_teacher_finished,0)),0) FROM eduai.edu_marking_task WHERE marking_task_student_id='anonymous';")"
    ;;
  rollback)
    sql "DELETE FROM eduai.edu_wrong_question WHERE wrong_question_paper_id=$PAPER;"
    sql "UPDATE eduai.edu_marking_task SET marking_task_teacher_finished=0, marking_task_teacher_finish_time=NULL WHERE marking_task_task_id='$BASE_TASK';"
    sql "UPDATE eduai.edu_marking_task SET marking_task_teacher_finished=0, marking_task_teacher_finish_time=NULL WHERE marking_task_student_id='anonymous';"
    echo "TOTAL=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question;")"
    echo "PAPER_ROWS=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_paper_id=$PAPER;")"
    echo "FLAG=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$BASE_TASK';")"
    ;;
  *) echo "usage: $0 <snapshot|assert-nowrite|assert-write|rollback> [taskId]"; exit 1;;
esac
