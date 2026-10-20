#!/bin/sh
# 阅卷增强（判错阈值 / 来源任务 / 撤销）的库侧助手。
# 用法: sh verify-marking-enhance.sh <expect|finished|undone|rollback> <taskId>
TASK="${2:-}"
sql() { echo kali | sudo -S mysql -N -e "$1" 2>/dev/null; }

# 每题满分口径（与后端一致）：结果 detail 里的 maxScore 优先，其次 5（兜底单题分）
MAXEXPR="CASE WHEN JSON_VALID(r.marking_result_detail) AND JSON_EXTRACT(r.marking_result_detail,'\$.maxScore') IS NOT NULL
               THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(r.marking_result_detail,'\$.maxScore')) AS DECIMAL(10,2))
               ELSE 5 END"
FINEXPR="CASE WHEN r.marking_result_teacher_score IS NOT NULL THEN r.marking_result_teacher_score ELSE IFNULL(r.marking_result_ai_score,0) END"

case "$1" in
  expect)
    echo "TASK=$TASK"
    echo "STU=$(sql "SELECT marking_task_student_id FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")"
    echo "PAPER=$(sql "SELECT IFNULL(marking_task_paper_id,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")"
    echo "FLAG_BEFORE=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")"
    echo "TOTAL_ROWS=$(sql "SELECT COUNT(*) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id='$TASK' AND r.marking_result_question_id>0;")"
    echo "ZERO_CNT=$(sql "SELECT COUNT(*) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id='$TASK' AND r.marking_result_question_id>0 AND ($FINEXPR) <= 0;")"
    echo "UNDER_RATE_CNT=$(sql "SELECT COUNT(*) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id='$TASK' AND r.marking_result_question_id>0 AND ($FINEXPR) > 0 AND ($FINEXPR) < 0.6 * ($MAXEXPR);")"
    echo "EXPECTED=$(sql "SELECT COUNT(*) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id='$TASK' AND r.marking_result_question_id>0 AND (($FINEXPR) <= 0 OR (($FINEXPR) > 0 AND ($FINEXPR) < 0.6 * ($MAXEXPR)));")"
    echo "PARTIAL_SAMPLE=$(sql "SELECT CONCAT(r.marking_result_question_id, ':', ($FINEXPR), '/', ($MAXEXPR)) FROM eduai.edu_marking_result r WHERE r.marking_result_task_id='$TASK' AND ($FINEXPR) > 0 AND ($FINEXPR) < 0.6 * ($MAXEXPR) ORDER BY r.marking_result_id LIMIT 3;" | tr '\n' ' ')"
    ;;
  finished)
    echo "FLAG=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")"
    echo "ROWS_WITH_SOURCE=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_source_task_id='$TASK';")"
    echo "ROWS_ALL_STUDENT=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question w WHERE w.wrong_question_source_task_id='$TASK' AND w.wrong_question_student_id = (SELECT marking_task_student_id FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK');")"
    echo "PARTIAL_INCLUDED=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question w JOIN eduai.edu_marking_result r ON r.marking_result_question_id = w.wrong_question_question_id AND r.marking_result_task_id='$TASK' WHERE w.wrong_question_source_task_id='$TASK' AND (CASE WHEN r.marking_result_teacher_score IS NOT NULL THEN r.marking_result_teacher_score ELSE IFNULL(r.marking_result_ai_score,0) END) > 0;")"
    ;;
  undone)
    echo "FLAG=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")"
    echo "ROWS_WITH_SOURCE=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_source_task_id='$TASK';")"
    echo "TOTAL=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question;")"
    ;;
  stage)
    # 造一个「部分得分低于 60%」的题：把该任务里某道 2 分且当前满分的题改成 1 分。
    # 记下原值到 edu_marking_task 之外的一个临时文件（见 rollback 的 unstage）。
    QID=$(sql "SELECT r.marking_result_id FROM eduai.edu_marking_result r
      WHERE r.marking_result_task_id='$TASK' AND r.marking_result_question_id>0
        AND (CASE WHEN r.marking_result_teacher_score IS NOT NULL THEN r.marking_result_teacher_score ELSE IFNULL(r.marking_result_ai_score,0) END) > 0
        AND (CASE WHEN JSON_VALID(r.marking_result_detail) AND JSON_EXTRACT(r.marking_result_detail,'\$.maxScore') IS NOT NULL
                  THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(r.marking_result_detail,'\$.maxScore')) AS DECIMAL(10,2)) ELSE 5 END) >= 2
      ORDER BY r.marking_result_id LIMIT 1;")
    echo "STAGE_RESULT_ID=$QID"
    echo "STAGE_ORIG=$(sql "SELECT IFNULL(r.marking_result_teacher_score,'NULL') FROM eduai.edu_marking_result r WHERE r.marking_result_id=$QID;")"
    printf '%s' "$QID" > /tmp/vme_stage_id
    sql "UPDATE eduai.edu_marking_result SET marking_result_teacher_score=1 WHERE marking_result_id=$QID;"
    echo "STAGED=$(sql "SELECT r.marking_result_question_id FROM eduai.edu_marking_result r WHERE r.marking_result_id=$QID;")"
    ;;
  unstage)
    QID=$(cat /tmp/vme_stage_id 2>/dev/null)
    if [ -n "$QID" ]; then
      sql "UPDATE eduai.edu_marking_result SET marking_result_teacher_score=NULL WHERE marking_result_id=$QID;"
      echo "UNSTAGED=$QID"
    else
      echo "UNSTAGED=none"
    fi
    ;;
  rollback)
    sql "DELETE FROM eduai.edu_wrong_question WHERE wrong_question_source_task_id='$TASK';"
    sql "UPDATE eduai.edu_marking_task SET marking_task_teacher_finished=0, marking_task_teacher_finish_time=NULL WHERE marking_task_task_id='$TASK';"
    echo "FLAG=$(sql "SELECT IFNULL(marking_task_teacher_finished,0) FROM eduai.edu_marking_task WHERE marking_task_task_id='$TASK';")"
    echo "ROWS_WITH_SOURCE=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question WHERE wrong_question_source_task_id='$TASK';")"
    echo "TOTAL=$(sql "SELECT COUNT(*) FROM eduai.edu_wrong_question;")"
    ;;
  *) echo "usage: $0 <expect|finished|undone|rollback> <taskId>"; exit 1;;
esac
