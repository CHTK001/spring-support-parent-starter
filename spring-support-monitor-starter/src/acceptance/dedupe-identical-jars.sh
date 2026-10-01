#!/bin/bash
# 去掉**内容完全相同**的 jar 副本，每个 sha256 只保留一份（保留文件名最新的那份）。
#
# 为什么只做「去重」而不做「按数量保留 newest N」：
#   * 去重**不丢任何回滚能力** —— 被删的字节在另一个文件里原样还在，
#     所以即使此刻有部署在进行中（正在生成新的 .bak-），也不会破坏它的回滚目标。
#   * 按数量保留 newest N 会**真的丢掉可回滚的版本**，那是别人的安全网，
#     只能在确认没有部署进行、且由产物所有者决定保留策略时才做。
#
# 排序键用**文件名里的时间戳**，不用 mtime：这些副本若是用 `cp -p` 造的，
# mtime 会全部相同，按 mtime 排序等于随机删（见 rollback-cleanup.sh 头部的说明）。
#
# 安全阀：
#   1. 默认 DRYRUN=1，只列不删
#   2. 删除前逐个断言「同 sha 的另一份确实存在」，否则跳过
#   3. 显式排除现役包（-x 参数给文件名模式），绝不碰正在被服务使用的那个
#   4. 每个 sha 只留文件名最新的那份（便于人工确认留下的就是最近的）
#
# 用法: dedupe-identical-jars.sh <dir> <glob> [-x <exclude-pattern>]
#   例: dedupe-identical-jars.sh /home/kali/eduai/backend 'eduai-backend.jar.bak-*' -x 'eduai-backend.jar'
set +u
DIR=${1:-}
GLOB=${2:-'*.jar*'}
EXCL=${4:-}

[ -d "$DIR" ] || { echo "[FATAL] 目录不存在: $DIR"; exit 2; }
cd "$DIR" || exit 2

echo "== 在 $DIR 里对 '$GLOB' 按 sha256 去重（排除模式: ${EXCL:-无}）=="

# EXCL 为空时**必须跳过 grep**，不能写 `grep -vE "$EXCL"`：
# 空正则匹配一切，`grep -v ""` 会把所有候选都过滤掉，表现为「目录里有 12 个文件却报 0 候选」。
# 我第一版就是这么写的，DRYRUN 跑出来 NOTHING_TO_MATCH，差一点误判成「没有重复副本」。
if [ -n "$EXCL" ]; then
  FILES=$(ls -1 $GLOB 2>/dev/null | grep -vE "$EXCL" || true)
else
  FILES=$(ls -1 $GLOB 2>/dev/null || true)
fi
if [ -z "$FILES" ]; then
  echo "  没有匹配的候选文件（glob='$GLOB' 排除='${EXCL:-无}'）"
  echo "RESULT=NOTHING_TO_MATCH"
  exit 0
fi

echo "  候选 $(echo "$FILES" | grep -c .) 份，逐份算 sha256："
declare -A SHA_OF
for f in $FILES; do
  H=$(sha256sum "$f" 2>/dev/null); H=${H%% *}
  SHA_OF[$f]=$H
  SZ=$(stat -c %s "$f" 2>/dev/null || echo 0)
  printf "    %-52s %9.1fMB  %s\n" "$f" "$(awk -v b="$SZ" 'BEGIN{printf "%.1f", b/1048576}')" "${H:0:16}"
done

echo
echo "  --- 分组：同一 sha 的副本 ---"
DUPLICATE_SHA=$(for f in $FILES; do echo "${SHA_OF[$f]} $f"; done | sort | awk '{c[$1]=c[$1]" "$2; n[$1]++} END{for(s in n) if(n[s]>1) print s" |"c[s]}')

if [ -z "$DUPLICATE_SHA" ]; then
  echo "  没有内容相同的副本，无需去重"
  echo "RESULT=NO_DUPLICATES"
  exit 0
fi

DEL_LIST=""
KEEP_LIST=""
FREED=0
echo "$DUPLICATE_SHA" | while IFS='|' read -r sha names; do
  # names 以空格分隔；按文件名降序 = 时间戳降序 = 新的在前
  ORDERED=$(echo "$names" | tr ' ' '\n' | grep . | sort -r)
  KEEP=$(echo "$ORDERED" | head -n 1)
  DROPS=$(echo "$ORDERED" | tail -n +2 | grep .)
  NDROP=$(echo "$DROPS" | grep -c .)
  echo "    sha=${sha:0:16}  共 $((NDROP + 1)) 份  -> 保留最新的 1 份"
  echo "$ORDERED" | nl -w2 -s'. ' | sed 's/^/        /'
  echo "$DROPS" | while read -r d; do
    [ -z "$d" ] && continue
    # 安全阀 2：确认同 sha 的另一份确实还在，才允许删
    OTHERS=0
    for o in $ORDERED; do [ "$o" != "$d" ] && OTHERS=$((OTHERS + 1)); done
    if [ "$OTHERS" -lt 1 ]; then
      echo "        [SKIP] $d —— 找不到同内容的其他副本，不删"
      continue
    fi
    SZ=$(stat -c %s "$d" 2>/dev/null || echo 0)
    if [ "${DRYRUN:-1}" = "1" ]; then
      echo "        [DRYRUN] 将删 $d  ($(awk -v b="$SZ" 'BEGIN{printf "%.1f", b/1048576}')MB)"
    else
      rm -f "$d" && echo "        [DELETED] $d  ($(awk -v b="$SZ" 'BEGIN{printf "%.1f", b/1048576}')MB)"
    fi
  done
done

echo
if [ "${DRYRUN:-1}" = "1" ]; then
  echo "  DRY RUN（设 DRYRUN=0 才真删）"
  echo "RESULT=DRYRUN"
else
  echo "RESULT=DEDUPED"
fi
echo "  剩余份数 = $(if [ -n "$EXCL" ]; then ls -1 $GLOB 2>/dev/null | grep -vE "$EXCL" | grep -c .; else ls -1 $GLOB 2>/dev/null | grep -c .; fi)"
