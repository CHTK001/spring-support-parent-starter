#!/bin/bash
# 把当前 app.jar 登记为 jar-watch 的期望版本（=「本次部署是有意为之」）。
#
# 为什么需要这个脚本：
#   jar-watch 靠比对 sha256 判断「jar 是否被并发会话意外覆盖」。
#   正常部署同样会换 jar，若不更新期望值，watcher 会把每一次正常部署都当成
#   「被意外覆盖」而永久告警 —— 2026-10-01 的磁盘事故就是这么来的
#   （详见 jar-watch.sh 头部注释）。
#   把这一步做成一个命令，正确动作才不会因为「忘了手改常量」而失效。
#
# 用法：部署完 jar、重启服务并确认业务正常之后执行
#   sudo /opt/ai-starter/jar-watch-accept.sh [备注]
set -e

JAR=/opt/ai-starter/app.jar
EXPECT_FILE=/opt/ai-starter/jar-watch.expected
GOOD=/opt/ai-starter/jar-watch-good
LAST_SEEN=/opt/ai-starter/jar-watch.last-seen
NOTE=${1:-}

[ -f "$JAR" ] || { echo "[FATAL] 找不到 $JAR"; exit 1; }

SHA=$(sha256sum "$JAR" | cut -d' ' -f1)
PREV=$(head -1 "$EXPECT_FILE" 2>/dev/null | tr -d '[:space:]' || true)

echo "当前 jar sha256 = $SHA"
if [ "$SHA" = "$PREV" ]; then
  echo "期望值本来就是这个，无需变更"
else
  echo "旧期望值         = ${PREV:-<空>}"
fi

printf '%s\n' "$SHA" > "$EXPECT_FILE"
# 同步 good 副本：EXPECT 变了，auto-rollback 的回滚目标也必须跟着变，
# 否则自动回滚会拿旧 jar 去覆盖新 jar（比不回滚更糟）。
mkdir -p "$GOOD"
cp -p "$JAR" "$GOOD/app.jar"
# 清掉去重标记，避免 watcher 把「刚登记的 jar」当成新替换再留一次证
echo "$SHA" > "$LAST_SEEN" 2>/dev/null || true

echo "已更新 -> $EXPECT_FILE"
echo "已同步 good 副本 -> $GOOD/app.jar"
[ -n "$NOTE" ] && echo "备注: $NOTE"
echo
echo "验证（应无 ALERT、证据份数不变）："
sleep 35
tail -3 /opt/ai-starter/jar-watch.log 2>/dev/null | sed 's/^/  /'
echo "  证据份数 = $(ls -1 /opt/ai-starter/jar-watch-evidence 2>/dev/null | wc -l)"
echo "JAR_WATCH_ACCEPT_DONE"
