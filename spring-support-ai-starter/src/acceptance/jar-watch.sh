#!/bin/bash
# ============================================================================
# ai-starter app.jar 监视器
#
# 背景：同一台机上有多个会话在部署 ai-starter，app.jar 会被互相覆盖
#       （一天内实测被换十几次），导致「刚验收完就被换掉」。本脚本把这件事
#       从「不可控」变成「可观测 + 可回滚」。
#
# 行为：
#   1. 每 INTERVAL 秒校验 /opt/ai-starter/app.jar 的 sha256
#   2. 与 EXPECT 不符 -> 写 ALERT 日志，并把被换上去的 jar 留证（evidence 目录）
#   3. 若存在开关文件 /opt/ai-starter/.auto-rollback，则把 good 副本回滚并重启服务
#      默认 **不开启** 回滚：直接覆盖会与并发会话互相打架，先观测再决定
#
# 用法：
#   systemctl status jar-watch                 # 看状态
#   tail -f /opt/ai-starter/jar-watch.log      # 看告警
#   /opt/ai-starter/jar-watch-accept.sh        # 【正常部署后执行】把当前 jar 登记为新的期望版本
#   touch /opt/ai-starter/.auto-rollback       # 开启自动回滚
#   rm -f  /opt/ai-starter/.auto-rollback      # 关闭自动回滚
#
# ---------------------------------------------------------------------------
# 2026-10-01 修复：EXPECT 硬编码导致「永久告警 + 每 30 秒复制 590MB」
#
#   原实现把 EXPECT 写成脚本内的常量 b75fc426…，而**任何一次正常部署**都会换 jar，
#   部署方不会去改这个常量 —— 于是从那以后 NOW 永远不等于 EXPECT，
#   脚本进入「每 30 秒告警 + 每 30 秒复制一份 ~590MB」的状态。
#   实测后果：证据目录累积 113 份 = 59G，把 78G 根分区撑到 100%，整机卡死、
#   sshd 握手超时（一次约 45 分钟失联）。加了 EVIDENCE_KEEP=3 后磁盘不再被撑满，
#   但**仍在每 30 秒复制 590MB**，I/O 与日志持续 churn。
#
#   根因不是「忘了加上限」，而是「期望值无法被正常部署流程更新」。
#   故本次做三层修复，均为**增强**，不改变原有检测语义：
#     ① EXPECT 外置：优先读 /opt/ai-starter/jar-watch.expected（该文件早就在，
#        但旧脚本从不读它），读不到才回落到内置常量。**每轮重读**，所以
#        jar-watch-accept.sh 登记后无需重启单元即可生效。
#        配套 jar-watch-accept.sh 把当前 jar 登记为新基线 —— 让「正确动作」廉价，
#        而不是靠人记得手改常量（这正是上一次失效的原因）。
#     ② 按 hash 去重：只有当观测到的 sha256 **不同于上次留证的那个**时才复制。
#        这样即使 EXPECT 再次陈旧，最多每个不同 jar 留证一次，
#        不可能退化成「每 30 秒复制一次」，从结构上杜绝磁盘事故复发。
#     ③ 保留 EVIDENCE_KEEP=3 硬上限作为第三层兜底；保留策略按**文件名降序**排序
#        （`sort -r`），不用 `ls -1t` —— 因为 `cp -p` 保留源 mtime，所有证据 mtime 相同，
#        按 mtime 排序无效；且方向必须是降序，升序会删掉最新留证。
# ============================================================================
set +e

JAR=/opt/ai-starter/app.jar
EXPECT_FILE=/opt/ai-starter/jar-watch.expected
EXPECT_FALLBACK=b75fc426f62ab522b4aa3540c4112979226aa83291cb3805efbc61362eb657f8
LOG=/opt/ai-starter/jar-watch.log
EVIDENCE=/opt/ai-starter/jar-watch-evidence
GOOD=/opt/ai-starter/jar-watch-good
LAST_SEEN=/opt/ai-starter/jar-watch.last-seen
FLAG=/opt/ai-starter/.auto-rollback
SVC=ai-starter
INTERVAL=${JAR_WATCH_INTERVAL:-30}

mkdir -p "$EVIDENCE" "$GOOD"

# ---- ① EXPECT 外置：文件优先，内置常量兜底 --------------------------------
read_expect() {
  local v=""
  if [ -f "$EXPECT_FILE" ]; then
    v=$(head -1 "$EXPECT_FILE" 2>/dev/null | tr -d '[:space:]')
  fi
  # 只接受 64 位十六进制；否则视为无效，回落到内置常量
  case "$v" in
    [0-9a-fA-F]*) [ ${#v} -eq 64 ] && { echo "$v"; return 0; } ;;
  esac
  echo "$EXPECT_FALLBACK"
}

EXPECT=$(read_expect)
EXPECT_SRC="fallback-constant"
[ -f "$EXPECT_FILE" ] && grep -qE '^[0-9a-fA-F]{64}$' "$EXPECT_FILE" 2>/dev/null && EXPECT_SRC="$EXPECT_FILE"

echo "[$(date '+%F %T')] watcher started  expect=${EXPECT:0:16}($EXPECT_SRC)  interval=${INTERVAL}s  autoRollback=$([ -f "$FLAG" ] && echo ON || echo off)" >> "$LOG"

# 首次：若当前就是期望版本，存一份 good 副本供回滚用
FIRST=$(sha256sum "$JAR" 2>/dev/null | cut -d' ' -f1)
if [ "$FIRST" = "$EXPECT" ]; then
  cp -p "$JAR" "$GOOD/app.jar" && echo "[$(date '+%F %T')] 已保存 good 副本 -> $GOOD/app.jar" >> "$LOG"
else
  echo "[$(date '+%F %T')] 启动时现役已是 ${FIRST:0:16}（非期望值），未建立 good 副本" >> "$LOG"
fi
# 启动时把 last-seen 置为当前值：避免重启后对同一个 jar 重复留证
echo "$FIRST" > "$LAST_SEEN" 2>/dev/null

while true; do
  # ---- ① 的配套修正：期望值每轮重读 ----
  # 原实现只在启动时读一次 EXPECT，于是 jar-watch-accept.sh 改完文件后，
  # **运行中的 watcher 仍在用旧值**，必须再手动 restart 单元才生效 ——
  # 等于把「一条命令」又变回「一条命令 + 记得重启」，正是本次要消灭的那类依赖。
  NEW_EXPECT=$(read_expect)
  if [ "$NEW_EXPECT" != "$EXPECT" ]; then
    echo "[$(date '+%F %T')] 期望值已更新: ${EXPECT:0:16} -> ${NEW_EXPECT:0:16}（由 jar-watch-accept.sh 登记）" >> "$LOG"
    EXPECT="$NEW_EXPECT"
    # 基线变了，去重标记要重置：新基线下的首次不匹配应当正常告警并留证一次
    : > "$LAST_SEEN" 2>/dev/null
  fi

  NOW=$(sha256sum "$JAR" 2>/dev/null | cut -d' ' -f1)
  if [ -z "$NOW" ]; then
    echo "[$(date '+%F %T')] WARN app.jar 不可读（可能在换包中间态）" >> "$LOG"
  elif [ "$NOW" != "$EXPECT" ]; then
    PREV=$(cat "$LAST_SEEN" 2>/dev/null | tr -d '[:space:]')
    if [ "$NOW" = "$PREV" ]; then
      # ---- ② 同一 jar 已经留过证：只跳过复制，不再刷日志 ----
      :
    else
      TS=$(date +%Y%m%d-%H%M%S)
      SZ=$(stat -c %s "$JAR" 2>/dev/null)
      echo "[$(date '+%F %T')] ALERT app.jar 已被替换: now=${NOW:0:16} size=${SZ} expect=${EXPECT:0:16}" >> "$LOG"
      cp -p "$JAR" "$EVIDENCE/app.jar.replaced-$TS" 2>/dev/null \
        && echo "[$(date '+%F %T')]       已留证 $EVIDENCE/app.jar.replaced-$TS" >> "$LOG"
      echo "$NOW" > "$LAST_SEEN" 2>/dev/null

      # ---- ③ 硬上限兜底：只保留最新 EVIDENCE_KEEP 份 ----
      # 必须按**文件名**排序，不能用 `ls -1t`（按 mtime）：
      # 上面用的是 `cp -p`，它**保留源文件的 mtime**，而源文件是 app.jar，
      # 于是每份证据的 mtime 都等于 app.jar 的构建时间 —— 实测三份证据的
      # mtime 完全相同（都是 Oct 1 05:33），`ls -1t` 在这种并列下顺序不确定，
      # 「保留最新 3 份」就退化成了「随机删 3 份」，可能删掉新证据、留下旧证据。
      # 文件名里的 app.jar.replaced-YYYYmmdd-HHMMSS 才是真正的**留证时刻**，
      # 且该格式按字符串排序即为时间顺序。mtime 保留着，供取证看构建时间。
      #
      # 注意 `sort -r` 的方向：降序才是「新 → 旧」，`tail -n +$((KEEP+1))` 删掉的
      # 才是**最旧**的那些。写成升序 `sort` 会反过来删掉最新留证 —— 实测踩过：
      # 造 7 份同 mtime 的证据，期望留 5/6/7，实际留下 1/2/3。
      EVIDENCE_KEEP=3
      ls -1 "$EVIDENCE"/app.jar.replaced-* 2>/dev/null | sort -r | tail -n +$((EVIDENCE_KEEP + 1)) | xargs -r rm -f

      # ---- 可选自动回滚 ----
      if [ -f "$FLAG" ]; then
        GS=$(sha256sum "$GOOD/app.jar" 2>/dev/null | cut -d' ' -f1)
        if [ "$GS" = "$EXPECT" ]; then
          echo "[$(date '+%F %T')]       auto-rollback: 恢复期望 jar 并重启 $SVC" >> "$LOG"
          cp -p "$JAR" "$EVIDENCE/app.jar.rolledback-from-$TS" 2>/dev/null
          cp "$GOOD/app.jar" "$JAR.restoring-$TS" && mv -f "$JAR.restoring-$TS" "$JAR"
          chown root:root "$JAR"
          systemctl restart "$SVC"
          sleep 60
          echo "[$(date '+%F %T')]       rollback 完成，is-active=$(systemctl is-active $SVC) sha=$(sha256sum $JAR 2>/dev/null | cut -c1-16)" >> "$LOG"
        else
          echo "[$(date '+%F %T')]       auto-rollback 跳过：good 副本不可用或校验不符" >> "$LOG"
        fi
      fi
    fi
  fi
  sleep "$INTERVAL"
done
