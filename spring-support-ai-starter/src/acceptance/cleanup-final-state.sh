#!/bin/bash
S() { echo kali | sudo -S "$@" 2>/dev/null; }
Q() { S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1"; }
LOG=/opt/ai-starter/app-out.log
echo "=== 清理验收脚本留下的覆盖行（测试数据）==="
echo "  清理前："
Q "SELECT CONCAT_WS(' | ', sys_ai_user_setting_user_id, sys_ai_user_setting_group, COALESCE(sys_ai_user_setting_field,'-'), CONCAT('[', COALESCE(sys_ai_user_setting_value,'<NULL>'), ']')) FROM sys_ai_user_setting ORDER BY 1;" | sed 's/^/    /'
echo "  删除行数 = $(Q "SELECT ROW_COUNT();" >/dev/null; S bash -c 'echo kali | sudo -S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot ai_test -e "DELETE FROM sys_ai_user_setting;" 2>/dev/null' >/dev/null; Q "SELECT COUNT(*) FROM sys_ai_user_setting;")"
echo "  清理后总数 = $(Q 'SELECT COUNT(*) FROM sys_ai_user_setting;')  （应为 0）"
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
echo "=== 底库条目（验收后应回到原状）==="
for u in admin ops; do
  n=$(S bash -c "python3 -c \"
import json
try: print(len(json.load(open('/opt/ai-starter/runtime/$u/face/index.json',encoding='utf-8'))))
except Exception: print('无文件/空')\"" 2>/dev/null)
  echo "  $u face 快照条目 = $n"
done
echo
echo "=== 应用错误扫描（本次会话最后 300 行）==="
S bash -c "tail -300 $LOG" 2>/dev/null | grep -aE "ERROR|Exception|Caused by" | tail -5 | sed -E 's/\x1b\[[0-9;]*m//g' | cut -c1-140 | sed 's/^/  /'
echo "  （无输出=最后 300 行无 ERROR/Exception）"
echo CLEANUP_FINAL_DONE
