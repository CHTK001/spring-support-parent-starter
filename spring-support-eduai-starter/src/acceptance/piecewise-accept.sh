#!/bin/sh
# 分段守卫验收：每段独立跑，段内 md5 未变才采纳；被换包则重试该段。
J=/home/kali/eduai/backend/eduai-backend.jar
B=http://127.0.0.1:19095

wait_ready() {
  i=0
  while [ $i -lt 60 ]; do
    c=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -X POST -H 'Content-Type: application/json' \
        -d '{"username":"admin","password":"admin123"}' $B/v2/user/login 2>/dev/null)
    [ "$c" = "200" ] && return 0
    sleep 5; i=$((i+1))
  done
  return 1
}

run_valid() {
  name=$1
  cmd=$2
  i=1
  while [ $i -le 6 ]; do
    wait_ready || { echo "===== $name: 后端未就绪，重试 ====="; i=$((i+1)); sleep 20; continue; }
    before=$(md5sum $J | cut -c1-32)
    sh -c "$cmd" > /tmp/sec.out 2>&1
    after=$(md5sum $J | cut -c1-32)
    if [ "$before" = "$after" ]; then
      echo "===== $name [VALID] md5=$after（第 $i 次）====="
      cat /tmp/sec.out
      return 0
    fi
    echo "===== $name 第 $i 次被换包（$before -> $after），重跑 ====="
    i=$((i+1)); sleep 15
  done
  echo "===== $name [FAIL] 6 次内未取得有效结果 ====="
  cat /tmp/sec.out
  return 1
}

cp -f /home/kali/answer-sheet.png /tmp/answer-sheet.png
run_valid "A. AI 全流程" "python3 /home/kali/ai-flow-acceptance.py"
run_valid "B. OCR+判分矩阵" "sh /home/kali/final-accept.sh"
run_valid "C. SELF 确定性" "sh /home/kali/probe-determinism.sh 5"
echo "现役 md5=$(md5sum $J | cut -c1-32)"