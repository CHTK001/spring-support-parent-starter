#!/bin/sh
# 工号登录验收（在 eduai 后端所在主机上对 127.0.0.1:19095 执行）
#
# 判据口径说明（重要）：
#  - 本部署开启了 plugin.oauth.local-admin-fallback=true，AuthFilter 在「拿不到有效令牌」时
#    会把身份提升为内置 admin('1')。因此「用令牌访问受保护接口」得到的结果**不能**作为
#    令牌身份的证据（实测：带令牌与不带令牌请求 /v2/edu/student/me 返回完全相同）。
#  - 真正可信的身份证据是**登录响应里的 userInfo**：它直接来自本次签发的令牌载荷。
#    本脚本因此把身份断言放在 userInfo 上。
BASE=http://127.0.0.1:19095
TEACHER_NO=ACC-1791086381
TEACHER_PWD=Teach@12345
TEACHER_NAME=验收老师
PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); echo "  PASS  $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  FAIL  $1"; }
has() { case "$1" in *"$2"*) return 0 ;; *) return 1 ;; esac; }

echo "###### 0) 管理员登录取令牌 ######"
ADMIN_JSON=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary '{"username":"admin","password":"admin123"}')
ATOK=$(printf '%s' "$ADMIN_JSON" | tr -d '\n' | sed -e 's/.*"accessToken" *: *"\([^"]*\)".*/\1/')
if [ "${#ATOK}" -gt 100 ]; then ok "管理员登录取到令牌 (len=${#ATOK})"; else bad "管理员登录失败"; fi

echo "###### 1) 教师 1 重置登录口令（新端点） ######"
RST=$(curl -s -X POST "$BASE/v2/edu/dimension/teachers/reset-password" \
  -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
  --data-binary "{\"teacherId\":1,\"newPassword\":\"$TEACHER_PWD\"}")
if has "$RST" '"data" : true'; then ok "reset-password -> data=true"; else bad "reset-password 未成功: $(printf '%s' "$RST" | head -c 300)"; fi

echo "###### 2) 正例：工号 + 口令登录（成败判据） ######"
LOGIN=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary "{\"username\":\"$TEACHER_NO\",\"password\":\"$TEACHER_PWD\",\"authType\":\"TEACHER\",\"accountType\":\"teacher\"}")
TTOK=$(printf '%s' "$LOGIN" | tr -d '\n' | sed -e 's/.*"accessToken" *: *"\([^"]*\)".*/\1/')
if has "$LOGIN" '"code" : "00000"' && [ "${#TTOK}" -gt 100 ]; then
  ok "工号登录签发令牌 (len=${#TTOK})"
else
  bad "工号登录未签发令牌: $(printf '%s' "$LOGIN" | head -c 300)"
fi

echo "###### 3) 令牌身份（取自登录响应的 userInfo，不经兜底） ######"
UI=$(printf '%s' "$LOGIN" | tr -d '\n' | sed 's/.*"userInfo"/"userInfo"/')
if has "$UI" "\"sysUserUsername\" : \"$TEACHER_NO\""; then ok "userInfo.sysUserUsername = $TEACHER_NO"; else bad "userInfo 身份不是工号: $(printf '%s' "$UI" | head -c 240)"; fi
if has "$UI" "\"sysUserNickname\" : \"$TEACHER_NAME\""; then ok "userInfo.sysUserNickname = $TEACHER_NAME"; else bad "userInfo 昵称不符"; fi
if has "$UI" '"TEACHER"'; then ok "userInfo.roles 含 TEACHER"; else bad "userInfo.roles 不含 TEACHER"; fi

echo "###### 4) 反例：口令错误（须业务拒绝，不得 500） ######"
WRONG=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary "{\"username\":\"$TEACHER_NO\",\"password\":\"definitely-wrong\",\"accountType\":\"teacher\"}")
if has "$WRONG" 'S0512C0000'; then
  bad "口令错误仍返回 500 S0512C0000"
elif has "$WRONG" '口令错误'; then
  ok "口令错误 -> 业务拒绝「口令错误」"
else
  bad "口令错误返回非预期: $(printf '%s' "$WRONG" | head -c 300)"
fi

echo "###### 5) 反例：工号不存在 ######"
NOSUCH=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary '{"username":"NO-SUCH-TEACHER-9999","password":"Teach@12345","accountType":"teacher"}')
if has "$NOSUCH" '工号不存在'; then ok "不存在的工号 -> 业务拒绝「工号不存在」"; else bad "非预期: $(printf '%s' "$NOSUCH" | head -c 300)"; fi

echo "###### 6) 未知账号来源必须回落默认实现（不得 500）——SPI getNewExtension 回落 ######"
UNKNOWN=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary '{"username":"whatever","password":"x","accountType":"nosuchtype"}')
if has "$UNKNOWN" 'S0512C0000'; then
  bad "未知 accountType 仍然 500 S0512C0000（SPI 未回落默认实现）"
else
  ok "未知 accountType 不是 500，而是业务响应: $(printf '%s' "$UNKNOWN" | tr -d '\n' | head -c 90)"
fi

echo "###### 7) 环境限制如实记录（不计 PASS/FAIL） ######"
A=$(curl -s "$BASE/v2/edu/student/me" -H "Authorization: Bearer $TTOK")
B=$(curl -s "$BASE/v2/edu/student/me")
if [ "$A" = "$B" ]; then
  echo "  INFO  令牌未被采信：带令牌与不带令牌请求 /v2/edu/student/me 返回完全相同"
  echo "        → plugin.oauth.local-admin-fallback=true 生效，身份恒为 '1'；"
  echo "        → 本部署下「令牌授权/越权」类断言不可验证，故此脚本不再做这类断言。"
else
  echo "  INFO  带令牌与不带令牌返回不同，令牌已被采信（本部署未走兜底）"
fi

echo
echo "###### 汇总: PASS=$PASS FAIL=$FAIL ######"
[ "$FAIL" -eq 0 ] || exit 1
