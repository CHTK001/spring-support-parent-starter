#!/bin/bash
# jar-watch 证据保留策略的单测（对应 jar-watch.sh 里的 EVIDENCE_KEEP 那一行）
#
# 为什么需要单测，而不是靠端到端看一眼：
#   端到端只能看到「份数是 3」，而「份数是 3」在**删对了**和**删反了**两种情况下
#   观测量完全一样 —— 我实测就把 `sort -r`（新→旧，删最旧）写成升序 `sort`
#   （旧→新，删最新），端到端跑完「份数不变」看起来一切正常，
#   是靠「造同 mtime 样本」这个夹具才暴露的（留存的 1/2/3 其实是**最旧**的三份）。
#
# 两个坑，本夹具同时覆盖：
#   ① jar-watch 用 `cp -p`，保留**源文件** mtime（源是被监视的 app.jar），
#      于是所有证据副本的 mtime 完全相同 —— 按 mtime 排序等于随机删。
#      所以夹具必须让所有样本**共享同一个 mtime**。
#   ② 排序方向必须显式断言。`tail -n +$((KEEP+1))` 只有配合**降序**才是删最旧；
#      写成升序会删掉刚留的那一份。
#
# 本测试**不安装、不碰真实证据目录**：只从 jar-watch.sh 里把那一行原样抽出来，
# 在 mktemp 目录上执行。因此它测的就是线上真正在跑的那一行，不是复制的副本。
set -u
RC=0
SCRIPT=${1:-$(dirname "$0")/jar-watch.sh}
KEEP=3

LINE=$(grep -E 'ls -1 .*EVIDENCE.*sort.*tail' "$SCRIPT" | head -1)
if [ -z "$LINE" ]; then
  echo "[FATAL] 在 $SCRIPT 里找不到保留策略那一行"
  exit 1
fi
echo "被测命令（从 $SCRIPT 原样抽出）："
echo "    $LINE"

run_keep() { EVIDENCE="$1" EVIDENCE_KEEP=$KEEP bash -c "$LINE"; }

mk() { # mk <dir> <count>
  local d=$1 n=$2 i
  for i in $(seq 1 "$n"); do
    printf 'x' > "$d/app.jar.replaced-2026010${i}-00000$i"
    # 全部同一个 mtime —— 复现 cp -p 的真实状态
    touch -d '2026-01-01 00:00:00' "$d/app.jar.replaced-2026010${i}-00000$i"
  done
}

echo
echo "== 用例 1：7 份同 mtime，应留下文件名最新的 3 份 =="
T=$(mktemp -d); mk "$T" 7
echo "   造 7 份，mtime 全为 $(ls -l --time-style=+%H:%M:%S -1 "$T" | awk '{print $6}' | sort -u | tr '\n' ' ')"
run_keep "$T"
GOT=$(ls -1 "$T" 2>/dev/null | sort)
echo "$GOT" | sed 's/^/     /'
#   ③ 期望值必须用与夹具生成**完全相同**的拼法，不要另写一条 sed 去拼字符串：
#      我第一版写 `seq 5 7 | sed 's|^|app.jar.replaced-2026010|; s|$|-00000|'`，
#      拼出来是 `...-00000`，漏了尾部序号（真实文件名是 `...-000005`），
#      结果**行为正确却被判 FAIL**。所以下面直接复用与 mk() 相同的模板。
WANT=$(for i in 5 6 7; do echo "app.jar.replaced-2026010${i}-00000${i}"; done | sort)
if [ "$GOT" = "$WANT" ]; then
  echo "   [PASS] 留下的正是最新的 3 份（5/6/7）"
else
  echo "   [FAIL] 期望 5/6/7；若留下 1/2/3 说明排序方向反了（会删掉最新留证）"
  RC=1
fi
rm -rf "$T"

echo
echo "== 用例 2（敏感性对照）：把方向翻转成升序，必须判失败 =="
echo "   这一步是本测试的关键 —— 它证明用例 1 的 PASS 不是恒真的"
T=$(mktemp -d); mk "$T" 7
run_keep() { EVIDENCE="$1" EVIDENCE_KEEP=$KEEP bash -c 'ls -1 "$EVIDENCE"/app.jar.replaced-* 2>/dev/null | sort | tail -n +$((EVIDENCE_KEEP + 1)) | xargs -r rm -f'; }
run_keep "$T"
GOT=$(ls -1 "$T" 2>/dev/null | sort)
echo "$GOT" | sed 's/^/     /'
if [ "$GOT" != "$WANT" ]; then
  echo "   [PASS] 升序版被判为不同结果（留下的是最旧 3 份）→ 用例 1 确实在检验排序方向"
else
  echo "   [FAIL] 升序版与降序版结果相同 → 用例 1 无法区分方向，测试无效"
  RC=1
fi
rm -rf "$T"

echo
echo "== 用例 3：不足 KEEP 份时不得误删 =="
for n in 1 2 3; do
  T=$(mktemp -d); mk "$T" "$n"
  run_keep() { EVIDENCE="$1" EVIDENCE_KEEP=$KEEP bash -c "$LINE"; }
  run_keep "$T"
  LEFT=$(ls -1 "$T" 2>/dev/null | grep -c .)
  if [ "$LEFT" = "$n" ]; then
    echo "   [PASS] 输入 $n 份 -> 留 $LEFT 份"
  else
    echo "   [FAIL] 输入 $n 份 -> 留 $LEFT 份（误删）"
    RC=1
  fi
  rm -rf "$T"
done

echo
echo "== 用例 4：目录不存在时不得报错退出非零 =="
T=$(mktemp -d); rm -rf "$T"
if run_keep "$T"; then echo "   [PASS] 空/不存在目录时命令仍返回 0"; else echo "   [FAIL] 返回非零"; RC=1; fi

echo
[ "$RC" = "0" ] && echo "JAR_WATCH_EVIDENCE_KEEP_TEST_PASS" || echo "JAR_WATCH_EVIDENCE_KEEP_TEST_FAIL"
exit $RC
