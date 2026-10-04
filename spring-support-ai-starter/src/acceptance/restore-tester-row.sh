#!/bin/bash
S() { echo kali | sudo -S "$@" 2>/dev/null; }
Q() { S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1"; }
echo "=== 清理误删前的残留（另一会话的 tester 行）==="
echo "  （我的 cleanup-final-state.sh 用了无条件 DELETE FROM sys_ai_user_setting，把它一起删了）"
echo
echo "=== 当前 sys_ai_user_setting ==="
Q "SELECT COUNT(*) FROM sys_ai_user_setting;" | sed 's/^/  行数=/'
echo
echo "=== tester 用户是否真实存在（确认不是我这边臆造的账号）==="
Q "SELECT COUNT(*) FROM sys_user WHERE sys_user_name='tester' OR sys_user_id='tester';" 2>/dev/null | sed 's/^/  sys_user 命中=/'
Q "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ai_test' AND TABLE_NAME='sys_user' LIMIT 8;" 2>/dev/null | sed 's/^/    /'
echo
echo "=== 还原 tester 的运行时默认模型覆盖（值取自清理日志）==="
Q "INSERT INTO sys_ai_user_setting
     (sys_ai_user_setting_user_id, sys_ai_user_setting_group, sys_ai_user_setting_field,
      sys_ai_user_setting_value, sys_ai_user_setting_value_type, sys_ai_user_setting_description,
      sys_ai_user_setting_sort, sys_ai_user_setting_status, create_time, update_time)
   VALUES ('tester','runtime','default_model','agnes:agnes-2.5-flash','STRING','',
           0,1,NOW(),NOW());" >/dev/null 2>&1
echo "  insert exit=$?"
echo
echo "=== 还原后核对 ==="
Q "SELECT CONCAT_WS(' | ', sys_ai_user_setting_user_id, sys_ai_user_setting_group, sys_ai_user_setting_field, COALESCE(sys_ai_user_setting_value,'<NULL>')) FROM sys_ai_user_setting ORDER BY 1;" | sed 's/^/  /'
echo "  行数=$(Q 'SELECT COUNT(*) FROM sys_ai_user_setting;')"
echo RESTORE_DONE
