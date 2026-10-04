#!/bin/bash
# 验证「用户改默认模型后立即生效」，并给出**污染防护**的敏感性对照。
#
# 生效链路（两条，缺一不可）：
#   A) SysAiUserSettingServiceImpl.upsertValue → 发布 userScoped=true 事件
#      → FaceStores 按账号重建（每个账号绑定自己的身份取配置）
#   B) SettingsController.publishUserSettingsChanged → 在**无请求身份的新线程**发布
#      → 共享单例（FaceClientManager）重建时只读到**全局**配置，不会把发起人的覆盖固化成全局实例
#
# 因此正确的不变量不是「共享单例从不重建」，而是：
#   ① 每个账号仍按**自己的**配置重建（ops 未配置 / admin 就绪）
#   ② 共享单例的重建**必须发生在非请求线程**（worker/pool）—— 出现在请求线程（*-handler-*）
#      才意味着把发起人的个人配置固化成全局实例
#
# 两处历史踩坑：
#   · 应用日志在 /opt/ai-starter/app-out.log（StandardOutput=append），**不在 journalctl**；
#     且必须 grep -a（文件含 NUL/ANSI，裸 grep 报 binary file matches 后**停止输出**=假空结果）。
#   · 不能按"本地时间"过滤日志（日志自带 -04:00，而本机系统时区重启前后变过），
#     改用**行号水位线**，与时区无关。
set -u
B=http://127.0.0.1:8787
UA="Mozilla/5.0"
LOG=/opt/ai-starter/app-out.log
S() { echo kali | sudo -S "$@" 2>/dev/null; }
Q() { S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1"; }
MARK=0
mark() { MARK=$(S bash -c "wc -l < $LOG" 2>/dev/null); MARK=${MARK:-0}; }
win() { S bash -c "tail -n +$((MARK+1)) $LOG" 2>/dev/null | sed -E 's/\x1b\[[0-9;]*m//g'; }
show() { win | grep -aE "$1" | cut -c1-150 | sed 's/^/  /'; }
# 统计某条日志出现在哪类线程上：'handler'=Tomcat 请求线程，'worker/pool'=无身份异步线程
on_thread() { # on_thread <关键字> <线程特征>
  win | grep -aF "$1" | grep -acE "\[[^]]*($2)[^]]*\]"
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
if [ -z "$OT" ] || [ -z "$AT" ]; then
  echo "  [FATAL] 未取得令牌（ops=${#OT} admin=${#AT}）；服务未就绪或账号不可用，拒绝继续"
  exit 1
fi

echo "=== 0. 基线 ==="
APPJAR=${AI_JAR:-/opt/ai-starter/app.jar}
echo "  被测产物 md5=$(S md5sum $APPJAR | cut -c1-8)  is-active=$(systemctl is-active ai-starter)"
echo "  ops 覆盖行=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';")  （应为 0）"
GLOBAL_DETECTOR=$(eff "$AT" face detector_model)
echo "  admin(全局) 生效 detector_model='${GLOBAL_DETECTOR}'"
if [ -z "$GLOBAL_DETECTOR" ] || [ "$GLOBAL_DETECTOR" = "ERR" ]; then echo "  [FATAL] 读不到全局检测模型（='$GLOBAL_DETECTOR'），停机"; exit 2; fi

echo
echo "=== 1. 先让 ops 的账号库建起来（GET /v2/face/list）==="
mark
curl -s -m 40 -A "$UA" -H "x-oauth-token: $OT" "$B/v2/face/list" -o /dev/null -w '  HTTP=%{http_code}\n'
sleep 5
show "账号 '(ops|admin)' 人脸库就绪|人脸检测模型未配置"

echo
echo "=== 2. ★ 用户级写入：ops 清空自己的 detector_model ==="
mark
C=$(put "$OT" face '{"detector_model":""}')
echo "  ops PUT {'detector_model':\"\"} HTTP=$C   （应 200）"
sleep 8
echo "  --- 该时刻之后的日志（水位线过滤，时区无关）---"
show "\[SysAiUserSetting\] 用户|\[Face\] 人脸设置变更|人脸检测模型未配置|人脸库就绪|\[FaceClientManager\]"

REQ_HANDLER=$(on_thread "FaceClientManager" "handler")
ASYNC_WORKER=$(on_thread "FaceClientManager" "worker|pool|task")
OPS_UNSET=$(on_thread "账号 'ops' 人脸检测模型未配置" "")
ADMIN_OK=$(on_thread "账号 'admin' 人脸库就绪" "")
echo
echo "  【判定①】每个账号按自己的配置重建"
echo "            ops 被判「未配置」次数=$OPS_UNSET （应 >=1）   admin 就绪次数=$ADMIN_OK （应 >=1）"
echo "  【判定②】共享单例重建的线程归属（污染防护）"
echo "            出现在请求线程(*-handler-*)次数=$REQ_HANDLER   （**必须为 0**：在请求线程重建=把发起人的覆盖固化成全局）"
echo "            出现在无身份异步线程次数=$ASYNC_WORKER          （应 >=1：说明按全局配置重建）"

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
echo "  ops DELETE /v2/settings/face/overrides HTTP=$C"
sleep 7
show "撤销分组|人脸设置变更|人脸库就绪"

echo
echo "=== 6. 收尾核对 ==="
echo "  ops 剩余覆盖行=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';")"
echo "  admin 生效 detector='$(eff "$AT" face detector_model)'"
echo "  admin 的 detector 必须仍等于全局 '${GLOBAL_DETECTOR}'（用户的覆盖不得影响他人）"
echo EFFECTIVE_VERIFY_DONE
