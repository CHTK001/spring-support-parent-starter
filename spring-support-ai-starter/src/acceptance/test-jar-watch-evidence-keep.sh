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

# 保留策略现在是**多行块**（先按文件名末尾抽时间戳、再按时间戳降序），
# 用 sed 按范围抽取整块，而不是 grep 单行 ——
# grep 只能拿到 `EVIDENCE_KEEP=3` 那一行，测的就不是真正在跑的那段逻辑。
BLOCK=$(sed -n '/^[[:space:]]*EVIDENCE_KEEP=/,/xargs -r rm -f/p' "$SCRIPT")
if [ -z "$BLOCK" ]; then
  echo "[FATAL] 在 $SCRIPT 里找不到保留策略代码块"
  exit 1
fi
echo "被测代码块（从 $SCRIPT 原样抽出，$(echo "$BLOCK" | grep -c .) 行）："
echo "$BLOCK" | sed 's/^/    /'

run_keep() { EVIDENCE="$1" EVIDENCE_KEEP=$KEEP bash -c "$BLOCK"; }

# 造**单个指定文件名**的样本。
# 与 mk() 的区别要说清：mk 是 `mk <dir> <count>`，按 count 生成
# app.jar.replaced-2026010N-00000N。我第一版误以为 mk 是 `mk <dir> <sha> <name>`，
# 于是 `seq 1 x` 直接报错、一个文件都没造出来，新增的三个用例全部空跑失败 ——
# 断言报的是「保留策略删错了」，实际是夹具根本没生成。
one() { # one <dir> <name>
  printf 'x' > "$1/$2"
  touch -d '2026-01-01 00:00:00' "$1/$2" 2>/dev/null
}

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
# 把被测块里的降序翻成升序，模拟「排序方向写反」这个具体错误。
# 必须用**独立的函数名**：bash 的函数重定义是全局的，我第一版直接写
# `run_keep() { ... "$FLIPPED"; }`，结果从这一行之后所有用例都在跑升序桩 ——
# 用例 3/4 因为「本来就不删文件」照样 PASS，把这个污染掩盖了，
# 直到用例 5（对方向敏感）才暴露为「保留策略删错了最新证据」。
FLIPPED=$(printf '%s' "$BLOCK" | sed 's/sort -r/sort/')
run_keep_flipped() { EVIDENCE="$1" EVIDENCE_KEEP=$KEEP bash -c "$FLIPPED"; }
run_keep_flipped "$T"
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
echo
echo "== 用例 5：replaced-* 与 rolledback-from-* 必须按【时间】混排，不能按前缀分组 =="
echo "   回归：2026-10-01 20:45 实测 auto-rollback 触发后，rolledback-from-* 落在"
echo "   只 glob replaced-* 的保留策略之外，永不清理；开关打开后每回滚一次漏一份。"
T=$(mktemp -d)
one "$T" app.jar.replaced-20261001-100000
one "$T" app.jar.rolledback-from-20261001-110000
one "$T" app.jar.replaced-20261001-120000
one "$T" app.jar.rolledback-from-20261001-130000
one "$T" app.jar.replaced-20261001-140000
echo "   造 5 份（replaced 3 + rolledback-from 2，时间 10:00~14:00 交错）"
run_keep "$T"
GOT=$(ls -1 "$T" 2>/dev/null | sort)
echo "$GOT" | sed 's/^/     /'
WANT=$(printf '%s\n' app.jar.replaced-20261001-140000 app.jar.rolledback-from-20261001-130000 app.jar.replaced-20261001-120000 | sort)
if [ "$GOT" = "$WANT" ]; then
  echo "   [PASS] 留下的是时间上最新的 3 份（两种前缀正确混排）"
else
  echo "   [FAIL] 期望 12:00/13:00/14:00 三份；若只剩 replaced-* 说明按前缀分组排了"
  RC=1
fi
NFROM=$(ls -1 "$T" 2>/dev/null | grep -c 'rolledback-from-')
if [ "$NFROM" -eq 1 ]; then
  echo "   [PASS] rolledback-from-* 也在保留集内（留 1 份），未被漏掉"
else
  echo "   [FAIL] rolledback-from-* 留了 $NFROM 份（期望 1）"
  RC=1
fi
rm -rf "$T"

echo
echo "== 用例 6：文件名不带时间戳的噪声文件不得被误删 =="
T=$(mktemp -d)
one "$T" app.jar.replaced-20261001-140000
one "$T" app.jar.replaced-20261001-130000
one "$T" app.jar.replaced-20261001-120000
one "$T" app.jar.replaced-20261001-110000
one "$T" README.txt
one "$T" app.jar.some-other-name
run_keep "$T"
if [ -f "$T/README.txt" ] && [ -f "$T/app.jar.some-other-name" ]; then
  echo "   [PASS] 无时间戳的文件未被删除"
else
  echo "   [FAIL] 无时间戳的文件被误删了"
  ls -1 "$T" | sed 's/^/     /'
  RC=1
fi
rm -rf "$T"

echo
[ "$RC" = "0" ] && echo "JAR_WATCH_EVIDENCE_KEEP_TEST_PASS" || echo "JAR_WATCH_EVIDENCE_KEEP_TEST_FAIL"
exit $RC
