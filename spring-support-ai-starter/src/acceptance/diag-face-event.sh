#!/bin/bash
# 定位：用户级写入之后，为什么还有一次「非用户级（userScoped=false）」的 face 事件，
# 导致共享单例被重建（实测增量=3，而期望 0）。
LOG=/opt/ai-starter/app-out.log
S() { echo kali | sudo -S "$@" 2>/dev/null; }
echo "=== 1. 该时间窗内的全部日志行（11:07:16.8 ~ 11:07:17.1，含类名）==="
S bash -c "grep -a '2026-10-04T11:07:1[67]' $LOG" 2>/dev/null \
  | sed -E 's/\x1b\[[0-9;]*m//g' | cut -c1-165 | sed 's/^/  /'
echo
echo "=== 2. 关键行的类名提取（谁在发事件 / 谁在改配置）==="
S bash -c "grep -a '2026-10-04T11:07:1[67]' $LOG" 2>/dev/null \
  | sed -E 's/\x1b\[[0-9;]*m//g' \
  | grep -aE "FaceClientManager|FaceStores|SysAiGlobalSetting|SysAiUserSetting|SettingsController|Initializer|Provider|Service" \
  | sed -E 's/^.*(INFO|WARN|ERROR|DEBUG) +[0-9]+ --- \[ai-starter\] \[([^]]+)\] +([^ ]+) +: (.*)$/  [\2] \3 :: \4/' \
  | cut -c1-160
echo
echo "=== 3. 谁会在 face 分组上做**全局**写（代码侧候选）==="
echo "  （在 ai 模块里搜：runAsync + 设置写入；以及给 face 分组写全局值的地方）"
echo DIAG_EVENT_DONE
