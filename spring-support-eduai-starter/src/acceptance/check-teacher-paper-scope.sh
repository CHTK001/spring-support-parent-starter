#!/bin/sh
# 教师端「我的试卷」后端强制 scope 验收（在 eduai 后端所在主机上执行）
#
# 判据：
#   /v2/edu/teacher/papers 走 pageMySelect（AND paper_owner_user_id = 当前账号），
#   必须看不到「他人 owner」的卷，也不含 owner 为空的系统预置卷。
#
# 做法（受控、可回滚）：
#   1) 插入两张探针卷：一张 owner='1'（应可见）、一张 owner='FOREIGN-SCOPE-PROBE'（应不可见）
#   2) 调 /v2/edu/teacher/papers，断言：可见探针在、外卷不在、条数 == DB 中 owner='1' 的条数
#   3) 无论成败都删掉两张探针（trap）
#
# 口径提示：本部署 local-admin-fallback=true，令牌不被采信，身份恒为 '1'。
#   故这里验证的是「owner 过滤这一 SQL 约束真实生效」，不是「两个不同教师各看各的」。
BASE=http://127.0.0.1:19095
OWNER_OK=1
OWNER_FOREIGN=FOREIGN-SCOPE-PROBE
SSH_PWD=kali
PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); echo "  PASS  $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  FAIL  $1"; }
has() { case "$1" in *"$2"*) return 0 ;; *) return 1 ;; esac; }
sql() { echo "$SSH_PWD" | sudo -S mysql -N -e "$1" 2>/dev/null; }

PROBE_OK_NO=ZZ-SCOPE-PROBE-MINE
PROBE_FOREIGN_NO=ZZ-SCOPE-PROBE-FOREIGN
PROBE_PRESET_NO=ZZ-SCOPE-PROBE-PRESET

cleanup() {
  echo "###### 清理探针 ######"
  sql "DELETE FROM eduai.edu_paper WHERE paper_no IN ('$PROBE_OK_NO','$PROBE_FOREIGN_NO','$PROBE_PRESET_NO');"
  sql "SELECT CONCAT('剩余探针=', COUNT(*)) FROM eduai.edu_paper WHERE paper_no IN ('$PROBE_OK_NO','$PROBE_FOREIGN_NO','$PROBE_PRESET_NO');"
}
trap cleanup EXIT

echo "###### 1) 插入三张探针卷 ######"
sql "DELETE FROM eduai.edu_paper WHERE paper_no IN ('$PROBE_OK_NO','$PROBE_FOREIGN_NO','$PROBE_PRESET_NO');"
sql "INSERT INTO eduai.edu_paper
      (paper_id, paper_no, paper_name, paper_subject, paper_grade_level, paper_grade,
       paper_total_score, paper_owner_user_id, paper_status)
     VALUES
      (999901, '$PROBE_OK_NO', 'scope 探针-本人', '数学', '小学', '1', 100, '1', 1),
      (999902, '$PROBE_FOREIGN_NO', 'scope 探针-他人', '数学', '小学', '1', 100, '$OWNER_FOREIGN', 1),
      (999903, '$PROBE_PRESET_NO', 'scope 探针-系统预置', '数学', '小学', '1', 100, NULL, 1);"
CNT_MINE=$(sql "SELECT COUNT(*) FROM eduai.edu_paper WHERE paper_owner_user_id='$OWNER_OK';")
echo "  DB 中 owner=$OWNER_OK 的卷数 = $CNT_MINE"

echo "###### 2) 调 /v2/edu/teacher/papers ######"
RESP=$(curl -s "$BASE/v2/edu/teacher/papers?pageNum=1&pageSize=200")
TOTAL=$(printf '%s' "$RESP" | tr -d '\n' | sed -e 's/.*"total" *: *\([0-9]*\).*/\1/')
echo "  接口 total=$TOTAL"

echo "###### 3) 断言 ######"
if has "$RESP" "$PROBE_OK_NO"; then
  ok "本人探针卷出现在列表里（owner=$OWNER_OK）"
else
  bad "本人探针卷缺失（接口未返回 owner=$OWNER_OK 的卷）"
fi

if has "$RESP" "$PROBE_FOREIGN_NO"; then
  bad "他人探针卷泄漏进列表（scope 未生效）"
else
  ok "他人探针卷未出现在列表里（owner=$OWNER_FOREIGN 被过滤）"
fi

if has "$RESP" "$PROBE_PRESET_NO"; then
  bad "系统预置卷（owner=NULL）泄漏进列表（pageMySelect 未排除）"
else
  ok "系统预置卷（owner=NULL）未出现在列表里（精确匹配不放行 NULL）"
fi

if [ "$TOTAL" = "$CNT_MINE" ]; then
  ok "接口 total ($TOTAL) == DB 中 owner=$OWNER_OK 的条数"
else
  bad "接口 total ($TOTAL) != DB 中 owner=$OWNER_OK 的条数 ($CNT_MINE)"
fi

echo
echo "###### 汇总: PASS=$PASS FAIL=$FAIL ######"
[ "$FAIL" -eq 0 ] || exit 1
