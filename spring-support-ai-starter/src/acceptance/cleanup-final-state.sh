#!/bin/bash
# 验收残留清理 + 线上最终状态核对
#
# ★ 只删**本次验收会产生**的覆盖行（白名单），绝不无条件 `DELETE FROM sys_ai_user_setting`。
#   2026-10-04 实测事故：本脚本原来就是无条件全表删除，跑的时候把**另一会话**正在用的
#   `tester | runtime | default_model | agnes:agnes-2.5-flash` 一起删了。
#   共享环境里"全表 delete"= 越权操作别人的数据；只能按 (用户,分组,字段) 精确删。
S() { echo kali | sudo -S "$@" 2>/dev/null; }
Q() { S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1"; }
LOG=/opt/ai-starter/app-out.log

# 本次验收会写的覆盖行（白名单）。新增用例时在这里登记，不要放宽成通配。
#   prodgrade.sh      → admin 的 image_search.search_top_k（配置热更新组，写完会改回原值）
#   facegrade.sh      → admin 的 face.top_k（第 9 组「rebuild 回归」写后复原）
#   verify-secfix.sh  → ops 的 runtime.default_model / image_search.* / face.detector_model / appearance.*
#   verify-effective.sh → ops 的 face.detector_model
ALLOW_SQL="
 (sys_ai_user_setting_user_id='ops')
 OR (sys_ai_user_setting_user_id='admin' AND sys_ai_user_setting_group='image_search' AND sys_ai_user_setting_field='search_top_k')
 OR (sys_ai_user_setting_user_id='admin' AND sys_ai_user_setting_group='face' AND sys_ai_user_setting_field='detector_model')
 OR (sys_ai_user_setting_user_id='admin' AND sys_ai_user_setting_group='face' AND sys_ai_user_setting_field='top_k')
"

echo "=== 清理验收残留（仅白名单内的 (用户,分组,字段)）==="
echo "  清理前（全部）："
Q "SELECT CONCAT_WS(' | ', sys_ai_user_setting_user_id, sys_ai_user_setting_group, COALESCE(sys_ai_user_setting_field,'-'), CONCAT('[', COALESCE(sys_ai_user_setting_value,'<NULL>'), ']')) FROM sys_ai_user_setting ORDER BY 1;" | sed 's/^/    /'
echo "  其中属于本次验收白名单："
Q "SELECT CONCAT_WS(' | ', sys_ai_user_setting_user_id, sys_ai_user_setting_group, sys_ai_user_setting_field) FROM sys_ai_user_setting WHERE $ALLOW_SQL ORDER BY 1;" | sed 's/^/    /'
Q "DELETE FROM sys_ai_user_setting WHERE $ALLOW_SQL;" >/dev/null 2>&1
echo "  清理后："
Q "SELECT CONCAT_WS(' | ', sys_ai_user_setting_user_id, sys_ai_user_setting_group, COALESCE(sys_ai_user_setting_field,'-'), CONCAT('[', COALESCE(sys_ai_user_setting_value,'<NULL>'), ']')) FROM sys_ai_user_setting ORDER BY 1;" | sed 's/^/    /'
echo "  剩余行数 = $(Q 'SELECT COUNT(*) FROM sys_ai_user_setting;')  （**非 0 是正常的**：可能是别人的行，不得再删；应逐条确认归属）"

echo
echo "=== 线上最终状态 ==="
MAINPID=$(systemctl show ai-starter -p MainPID --value)
echo "  md5=$(md5sum /opt/ai-starter/app.jar 2>/dev/null | cut -c1-8)  is-active=$(systemctl is-active ai-starter)  NRestarts=$(systemctl show ai-starter -p NRestarts --value)"
echo "  8787 监听=$(ss -lntH 2>/dev/null | awk '{print $4}' | grep -c ':8787$')"
echo "  JVM 紧凑头: $(S ps -o args= -p $MAINPID | grep -c 'UseCompactObjectHeaders') （1=已启用）"
echo "  数据库=$(Q 'SELECT VERSION();')"
echo "  /opt 可用=$(df -BG /opt | tail -1 | awk '{print $4}')  回滚点=$(S ls -1 /opt/ai-starter/backup/app.jar.bak-* 2>/dev/null | wc -l) 个"
echo
echo "=== 全局层关键模型值（不应被验收改动）==="
Q "SELECT CONCAT(sys_ai_global_setting_group,'.',sys_ai_global_setting_field,' = ',LEFT(sys_ai_global_setting_value,42)) FROM sys_ai_global_setting WHERE (sys_ai_global_setting_group='face' AND sys_ai_global_setting_field IN ('detector_model','feature_model')) OR (sys_ai_global_setting_group='image_search' AND sys_ai_global_setting_field='default_image_feature_model') OR (sys_ai_global_setting_group='runtime' AND sys_ai_global_setting_field='default_model') OR (sys_ai_global_setting_group='rag' AND sys_ai_global_setting_field='rag_llm_model') OR (sys_ai_global_setting_group='bot' AND sys_ai_global_setting_field='default_model') ORDER BY 1;" | sed 's/^/  /'
echo
echo "=== 底库条目 ==="
for u in admin ops; do
  n=$(S bash -c "python3 -c \"
import json
try: print(len(json.load(open('/opt/ai-starter/runtime/$u/face/index.json',encoding='utf-8'))))
except Exception: print('无文件/空')\"" 2>/dev/null)
  echo "  $u face 快照条目 = $n"
done
echo
echo "=== 应用错误扫描（最近 400 行，需 grep -a）==="
S bash -c "tail -400 $LOG" 2>/dev/null | grep -aE "ERROR|Exception|Caused by" | tail -6 | sed -E 's/\x1b\[[0-9;]*m//g' | cut -c1-140 | sed 's/^/  /'
echo CLEANUP_FINAL_DONE
