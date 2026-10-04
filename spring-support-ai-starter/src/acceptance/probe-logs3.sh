#!/bin/bash
LOG=/opt/ai-starter/app-out.log
TOTAL=$(wc -l < $LOG)
START=$(grep -an "Started AiStarterApplication" $LOG | tail -1 | cut -d: -f1)
echo "=== 日志 ==="
ls -l $LOG | awk '{print "  大小="$5"  改于="$6" "$7" "$8}'
echo "  总行数=$TOTAL  最后一次启动在第 $START 行（本次部署）"
echo "  grep 必须用 -a：文件含 NUL/ANSI，裸 grep 会报 'binary file matches' 并**停止输出**（假空结果）"
echo
echo "=== A. 本次部署后的「人脸设置变更」重建（含 用户级 标记）==="
tail -n +${START} $LOG | grep -aoE "\[Face\] [^ ]*设置变更[^（]*（账号数 [0-9]+）" | sort | uniq -c | sed 's/^/  /'
echo
echo "=== B. 本次部署后的 FaceClientManager 共享实例重建 ==="
tail -n +${START} $LOG | grep -ac "FaceClientManager] 收到 face 分组配置变更，开始热重建" | sed 's/^/  「开始热重建」出现次数 = /'
tail -n +${START} $LOG | grep -ac "FaceClientManager] 收到用户级 face 配置写入" | sed 's/^/  「用户级写入，跳过重建」出现次数 = /'
echo
echo "=== C. 本次部署后「未配置模型」的账号（应为 ops）==="
tail -n +${START} $LOG | grep -aoE "账号 '[^']+' 人脸检测模型未配置" | sort | uniq -c | sed 's/^/  /'
echo
echo "=== D. 本次部署后账号库就绪（谁在执行重建）==="
tail -n +${START} $LOG | grep -aoE "账号 '[^']+' 人脸库就绪: [0-9]+/[0-9]+ 条" | sort | uniq -c | sed 's/^/  /'
echo
echo "=== E. 时间线：最后一次启动之后的 Face 相关行（前 40 行）==="
tail -n +${START} $LOG | grep -aE "FaceClientManager|\[Face\]|SysAiUserSetting" | head -40 | sed -E 's/\x1b\[[0-9;]*m//g' | cut -c1-150 | sed 's/^/  /'
echo PROBE4_DONE
