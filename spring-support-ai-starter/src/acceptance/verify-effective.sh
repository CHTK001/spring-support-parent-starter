#!/bin/bash
# 验证「用户改默认模型后立即生效」：
#   ① 用户级写入**会**触发重建（此前不发事件 → 不重建 → 「改了不生效」）
#   ② 重建**按账号**绑定身份（ops 清空检测模型 → 只有 ops 的库失能，admin 不受影响）
#   ③ 共享单例**不**被用户级写入重建（否则会把触发者的配置串给其他用户）
#   ④ 清空覆盖也生效
#
# 两处历史踩坑，本版已修：
#   · 应用日志在 /opt/ai-starter/app-out.log（StandardOutput=append），**不在 journalctl**。
#     原用 journalctl 取"该时刻之后的日志"，结果证据段落**全为空**却照样跑完 ——
#     空证据不是通过。且必须 grep -a：文件含 NUL/ANSI，裸 grep 会报 binary file matches
#     并**停止输出**，给出假空结果。
#   · 原先按"本地时间"过滤日志，而日志自带 -04:00 时区；本机重启前后系统时区变过
#     （UTC+8 → EDT），按本地时间比对会整体错位 12 小时。故改用**行号水位线**，与时区无关。
B=http://127.0.0.1:8787
UA="Mozilla/5.0"
LOG=/opt/ai-starter/app-out.log
S() { echo kali | sudo -S "$@" 2>/dev/null; }
Q() { S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1"; }
MARK=0
mark() { MARK=$(S bash -c "wc -l < $LOG" 2>/dev/null); MARK=${MARK:-0}; }
show() { # show <grep-ERE>
  S bash -c "tail -n +$((MARK+1)) $LOG | grep -aE '$1'" 2>/dev/null \
    | sed -E 's/\x1b\[[0-9;]*m//g' | cut -c1-150 | sed 's/^/  /'
}
tok() {
  curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" "$B/v2/user/login" \
    | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null
}
put() { curl -s -m 40 -A "$UA" -H "x-oauth-token: $1" -X PUT -H 'Content-Type: application/json' -d "$3" -o /tmp/vr.json -w '%{http_code}' "$B/v2/settings/$2"; }
eff() {
  curl -s -m 40 -A "$UA" -H "x-oauth-token: $1" "$B/v2/settings/$2" \
    | python3 -c "
import json,sys
f=sys.argv[1]
try:
  o=json.load(sys.stdin); d=o.get('data') or {}
  if isinstance(d.get('data'), dict): d=d['data']
  v=d.get(f); print('' if v is None else v)
except Exception: print('ERR')" "$3" 2>/dev/null
}

OT=$(tok ops 'Ops@Guada2026'); AT=$(tok admin admin123)
echo "=== 0. 基线 ==="
echo "  md5=$(S md5sum /opt/ai-starter/app.jar | cut -c1-8)  is-active=$(systemctl is-active ai-starter)"
echo "  ops 覆盖行=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';")  （应为 0）"
GLOBAL_DETECTOR=$(eff "$AT" face detector_model)
echo "  admin(全局) 生效 detector_model='${GLOBAL_DETECTOR}'"
if [ -z "$GLOBAL_DETECTOR" ]; then echo "  [FATAL] 读不到全局检测模型，停机"; exit 2; fi
# 共享单例重建的全局基线计数（步 6 报增量，避免只看到最后一个水位线的窗口）
HOT0=$(S bash -c "grep -ac '开始热重建' $LOG" 2>/dev/null); HOT0=${HOT0:-0}

echo
echo "=== 1. 先让 ops 的账号库建起来（GET /v2/face/list）==="
mark
curl -s -m 40 -A "$UA" -H "x-oauth-token: $OT" "$B/v2/face/list" -o /dev/null -w '  HTTP=%{http_code}\n'
sleep 5
show "账号 '(ops|admin)' 人脸库就绪"
echo "  （应出现 ops 的库就绪）"

echo
echo "=== 2. ★ 用户级写入：ops 清空自己的 detector_model ==="
mark
C=$(put "$OT" face '{"detector_model":""}')
echo "  ops PUT {'detector_model':\"\"} HTTP=$C   （应 200）"
sleep 7
echo "  --- 该时刻之后的日志（水位线过滤，时区无关）---"
show "\[SysAiUserSetting\] 用户|\[Face\] 人脸设置变更|人脸检测模型未配置|人脸库就绪|FaceClientManager"
echo "  【期望①】[SysAiUserSetting] 用户 ops 覆盖已写入 → 紧接着 [Face] 人脸设置变更（用户级）"
echo "  【期望②】账号 'ops' 人脸检测模型未配置（**只有 ops**）"
echo "  【期望③】不出现其他账号的「未配置」= 没串号"
echo "  【期望④】不出现 FaceClientManager「开始热重建」= 共享单例没被用户级写入重建"

echo
echo "=== 3. 逐账号核对生效模型 ==="
echo "  ops   -> detector='$(eff "$OT" face detector_model)'  feature='$(eff "$OT" face feature_model)'"
echo "  admin -> detector='$(eff "$AT" face detector_model)'  feature='$(eff "$AT" face feature_model)'"
echo "  【期望】ops 的 detector 为空、admin 的仍为 '${GLOBAL_DETECTOR}'"

echo
echo "=== 4. 恢复：ops 写回全局模型，应再次重建 ==="
mark
C=$(put "$OT" face "{\"detector_model\":\"${GLOBAL_DETECTOR}\"}")
echo "  ops PUT 回 '${GLOBAL_DETECTOR}' HTTP=$C"
sleep 7
show "人脸设置变更|人脸检测模型未配置|人脸库就绪"
echo "  【期望】再次重建，且**不再**出现 ops「未配置」（已恢复可用）"
echo "  ops   -> detector='$(eff "$OT" face detector_model)'"

echo
echo "=== 5. 清空覆盖（前端「清空（使用默认）」的同一路径）==="
mark
C=$(curl -s -m 40 -A "$UA" -H "x-oauth-token: $OT" -X DELETE -o /tmp/vr.json -w '%{http_code}' "$B/v2/settings/face/overrides")
echo "  ops DELETE /v2/settings/face/overrides HTTP=$C  （有覆盖行才发事件；无行不发=不空转）"
sleep 6
show "撤销分组|人脸设置变更|人脸库就绪"

echo
echo "=== 6. 收尾核对 ==="
echo "  ops 剩余覆盖行=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';")"
echo "  admin 生效 detector='$(eff "$AT" face detector_model)'"
HOT1=$(S bash -c "grep -ac '开始热重建' $LOG" 2>/dev/null); HOT1=${HOT1:-0}
echo "  共享单例重建次数增量 = $((HOT1-HOT0)) （本次全程应为 0：用户级写入不得重建共享实例）"
echo EFFECTIVE_VERIFY_DONE
