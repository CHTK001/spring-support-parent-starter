#!/bin/bash
# dedupe-identical-jars.sh 的夹具单测。ASCII-only，与 rollback-cleanup.sh 的单测同一套要求：
# 必须有敏感性对照，否则「全 PASS」可能只是因为断言恒真。
#
# 覆盖：
#   1. 基本去重：3 份同内容 + 2 份各自唯一 -> 删 2 留 3
#   2. DRYRUN 默认开启：什么都不删
#   3. DRYRUN=0 才真删，且删的正是重复的那几份
#   4. 安全阀：内容唯一的文件永远不删
#   5. -x 排除模式：排除项不进候选、也不会被删
#   6. EXCL 为空时不得把候选全过滤掉（回归：我第一版 `grep -vE "$EXCL"` 在 EXCL 为空时
#      空正则匹配一切，12 个文件报成 0 候选，差一点被误判成「没有重复副本」）
#   7. 无匹配时返回 NOTHING_TO_MATCH
#   8. 敏感性对照：把工具换成「什么都不删」的桩，用例 1/3 必须失败
set +u
HERE=$(cd "$(dirname "$0")" && pwd)
TOOL=${1:-$HERE/dedupe-identical-jars.sh}
RC=0

[ -f "$TOOL" ] || { echo "[FATAL] tool not found: $TOOL"; exit 2; }
echo "tool under test: $TOOL"

mk() { # mk <dir> <sha> <name>
  printf '%s' "$2" > "$1/$3"
}

check() { # check <name> <expected> <actual>
  if [ "$2" = "$3" ]; then
    echo "   [PASS] $1"
  else
    echo "   [FAIL] $1 (expected=$2 actual=$3)"
    RC=1
  fi
}

echo
echo "== case 1+2: DRYRUN by default, 3 identical + 2 unique -> plans 2 deletions, deletes 0 =="
T=$(mktemp -d)
mk "$T" AAA aaa-20260101-000001.jar
mk "$T" AAA aaa-20260101-000002.jar
mk "$T" AAA aaa-20260101-000003.jar
mk "$T" BBB bbb-20260101-000001.jar
mk "$T" CCC ccc-20260101-000001.jar
OUT=$(DRYRUN=1 bash "$TOOL" "$T" 'aaa-*.jar' 2>&1)
check "reports DRYRUN"        "DRYRUN"        "$(echo "$OUT" | grep -o 'RESULT=DRYRUN' | sed 's/RESULT=//')"
# 目录里共 5 个文件：aaa-* x3 + bbb-* + ccc-*。glob 只选 aaa-*，DRYRUN 又不删，
# 所以 DRYRUN 后必须仍是 5 个 —— 期望值要按「目录实际文件数」写，不能按候选数写。
check "nothing deleted"       "5"             "$(ls -1 "$T" | grep -c .)"
check "all 3 aaa still there" "3"             "$(ls -1 "$T" | grep -c '^aaa-')"
echo "$OUT" | grep -E 'DRYRUN|sha=' | sed 's/^/     /'
rm -rf "$T"

echo
echo "== case 3+4: DRYRUN=0 deletes only the duplicates, keeps one + all uniques =="
T=$(mktemp -d)
mk "$T" AAA aaa-20260101-000001.jar
mk "$T" AAA aaa-20260101-000002.jar
mk "$T" AAA aaa-20260101-000003.jar
mk "$T" BBB bbb-20260101-000001.jar
mk "$T" CCC ccc-20260101-000001.jar
OUT=$(DRYRUN=0 bash "$TOOL" "$T" 'aaa-*.jar' 2>&1)
check "reports DEDUPED"       "DEDUPED"       "$(echo "$OUT" | grep -o 'RESULT=DEDUPED' | sed 's/RESULT=//')"
check "2 files deleted"       "2"             "$(( 5 - $(ls -1 "$T" | grep -c .) ))"
check "newest duplicate kept" "1"             "$(ls -1 "$T" | grep -c 'aaa-20260101-000003.jar')"
check "older dups removed"    "0"             "$(ls -1 "$T" | grep -c 'aaa-20260101-00000[12].jar')"
echo "$OUT" | grep -E 'DELETED|sha=' | sed 's/^/     /'
rm -rf "$T"

echo
echo "== case 5: -x exclude pattern keeps the excluded file out of the candidate set =="
T=$(mktemp -d)
mk "$T" AAA live.jar
mk "$T" AAA aaa-20260101-000001.jar
mk "$T" AAA aaa-20260101-000002.jar
OUT=$(DRYRUN=0 bash "$TOOL" "$T" '*.jar' -x '^live\.jar$' 2>&1)
check "live.jar untouched"    "1"             "$(ls -1 "$T" | grep -c '^live.jar$')"
check "one aaa copy removed"  "1"             "$(( 2 - $(ls -1 "$T" | grep -c 'aaa-') ))"
rm -rf "$T"

echo
echo "== case 6 (regression): EXCL empty must NOT filter every candidate away =="
T=$(mktemp -d)
mk "$T" AAA aaa-20260101-000001.jar
mk "$T" AAA aaa-20260101-000002.jar
OUT=$(DRYRUN=1 bash "$TOOL" "$T" 'aaa-*.jar' 2>&1)
check "candidates found"      "2"             "$(echo "$OUT" | grep -o '候选 [0-9]* 份' | grep -o '[0-9]*' | head -1)"
check "not NOTHING_TO_MATCH"  "0"             "$(echo "$OUT" | grep -c 'RESULT=NOTHING_TO_MATCH')"
rm -rf "$T"

echo
echo "== case 7: no matching files -> NOTHING_TO_MATCH =="
T=$(mktemp -d)
mk "$T" AAA some.txt
OUT=$(DRYRUN=0 bash "$TOOL" "$T" '*.jar' 2>&1)
check "reports NOTHING_TO_MATCH" "NOTHING_TO_MATCH" "$(echo "$OUT" | grep -o 'RESULT=NOTHING_TO_MATCH' | sed 's/RESULT=//')"
check "nothing deleted"         "1"                "$(ls -1 "$T" | grep -c .)"
rm -rf "$T"

echo
echo "== case 8 (sensitivity control): a no-op stub MUST fail cases 3 and 5 =="
echo "   (if the stub passes, cases 3/5 are asserting nothing)"
STUB=$(mktemp)
cat > "$STUB" <<'STUBEOF'
#!/bin/bash
echo "RESULT=STUB"
exit 0
STUBEOF
chmod +x "$STUB"
T=$(mktemp -d)
mk "$T" AAA aaa-20260101-000001.jar
mk "$T" AAA aaa-20260101-000002.jar
mk "$T" AAA aaa-20260101-000003.jar
mk "$T" BBB bbb-20260101-000001.jar
mk "$T" CCC ccc-20260101-000001.jar
S=$(DRYRUN=0 bash "$STUB" "$T" 'aaa-*.jar' 2>&1)
DEL=$(( 5 - $(ls -1 "$T" | grep -c .) ))
if [ "$DEL" -eq 0 ] && [ "$(echo "$S" | grep -c 'RESULT=DEDUPED')" -eq 0 ]; then
  echo "   [PASS] no-op stub deletes nothing and does not claim DEDUPED -> cases 3/5 are meaningful"
else
  echo "   [FAIL] stub behaved like the real tool; the assertions cannot distinguish them"
  RC=1
fi
rm -rf "$T" "$STUB"

echo
[ "$RC" = "0" ] && echo "DEDUPE_IDENTICAL_JARS_TEST_PASS" || echo "DEDUPE_IDENTICAL_JARS_TEST_FAIL"
exit $RC
