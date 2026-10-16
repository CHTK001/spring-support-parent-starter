#!/bin/sh
# 学号登录验收（在 eduai 后端所在主机上对 127.0.0.1:19095 执行）
#
# 判据口径说明同 check-teacher-login.sh：本部署开启 local-admin-fallback，
# 令牌访问受保护接口的结果不可作为身份证据；身份断言一律取自登录响应的 userInfo。
BASE=http://127.0.0.1:19095
STUDENT_ID=1
STUDENT_NO=2026000001
STUDENT_PWD=Stud@12345
PARENT_PHONE=13800000001
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

echo "###### 1) 学生 1 重置登录密码 ######"
RST=$(curl -s -X POST "$BASE/v2/edu/dimension/students/reset-password" \
  -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
  --data-binary "{\"studentId\":$STUDENT_ID,\"newPassword\":\"$STUDENT_PWD\"}")
if has "$RST" '"data" : true'; then ok "reset-password -> data=true"; else bad "reset-password 未成功: $(printf '%s' "$RST" | head -c 300)"; fi

echo "###### 2) 正例：学号 + 口令登录（成败判据） ######"
LOGIN=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary "{\"username\":\"$STUDENT_NO\",\"password\":\"$STUDENT_PWD\",\"authType\":\"STUDENT\",\"accountType\":\"student\"}")
STOK=$(printf '%s' "$LOGIN" | tr -d '\n' | sed -e 's/.*"accessToken" *: *"\([^"]*\)".*/\1/')
if has "$LOGIN" '"code" : "00000"' && [ "${#STOK}" -gt 100 ]; then
  ok "学号登录签发令牌 (len=${#STOK})"
else
  bad "学号登录未签发令牌: $(printf '%s' "$LOGIN" | head -c 300)"
fi

echo "###### 3) 令牌身份（取自登录响应的 userInfo，不经兜底） ######"
UI=$(printf '%s' "$LOGIN" | tr -d '\n' | sed 's/.*"userInfo"/"userInfo"/')
if has "$UI" "\"sysUserUsername\" : \"$STUDENT_NO\""; then ok "userInfo.sysUserUsername = $STUDENT_NO"; else bad "userInfo 身份不是学号: $(printf '%s' "$UI" | head -c 240)"; fi
if has "$UI" "\"sysUserId\" : $STUDENT_NO"; then ok "userInfo.sysUserId = $STUDENT_NO"; else bad "userInfo.sysUserId 不符"; fi
if has "$UI" '"STUDENT"'; then ok "userInfo.roles 含 STUDENT"; else bad "userInfo.roles 不含 STUDENT"; fi

echo "###### 4) 正例：家长手机号 + 口令登录（身份仍应落到学号） ######"
PHONE=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary "{\"username\":\"$PARENT_PHONE\",\"password\":\"$STUDENT_PWD\",\"accountType\":\"student\"}")
PUI=$(printf '%s' "$PHONE" | tr -d '\n' | sed 's/.*"userInfo"/"userInfo"/')
if has "$PHONE" '"code" : "00000"' && [ "$(printf '%s' "$PHONE" | tr -d '\n' | sed -e 's/.*"accessToken" *: *"\([^"]*\)".*/\1/' | wc -c)" -gt 100 ]; then
  ok "家长手机号登录签发令牌"
else
  bad "家长手机号登录失败: $(printf '%s' "$PHONE" | head -c 300)"
fi
if has "$PUI" "\"sysUserUsername\" : \"$STUDENT_NO\""; then ok "手机号登录后身份为学号 $STUDENT_NO"; else bad "手机号登录后身份不符"; fi

echo "###### 5) 反例：密码错误（须业务拒绝，不得 500） ######"
WRONG=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary "{\"username\":\"$STUDENT_NO\",\"password\":\"definitely-wrong\",\"accountType\":\"student\"}")
if has "$WRONG" 'S0512C0000'; then
  bad "密码错误仍返回 500 S0512C0000"
elif has "$WRONG" '密码错误'; then
  ok "密码错误 -> 业务拒绝「密码错误」"
else
  bad "密码错误返回非预期: $(printf '%s' "$WRONG" | head -c 300)"
fi

echo "###### 6) 反例：账号不存在 ######"
NOSUCH=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary '{"username":"NO-SUCH-STUDENT-9999","password":"Stud@12345","accountType":"student"}')
if has "$NOSUCH" '不存在'; then ok "不存在的账号 -> 业务拒绝「学号或家长手机号不存在」"; else bad "非预期: $(printf '%s' "$NOSUCH" | head -c 300)"; fi

echo "###### 7) 未知账号来源必须回落默认实现（不得 500）——SPI getNewExtension 回落 ######"
UNKNOWN=$(curl -s -X POST "$BASE/v2/user/login" -H 'Content-Type: application/json' \
  --data-binary '{"username":"whatever","password":"x","accountType":"nosuchtype"}')
if has "$UNKNOWN" 'S0512C0000'; then
  bad "未知 accountType 仍然 500 S0512C0000（SPI 未回落默认实现）"
else
  ok "未知 accountType 不是 500，而是业务响应: $(printf '%s' "$UNKNOWN" | tr -d '\n' | head -c 90)"
fi

echo
echo "###### 汇总: PASS=$PASS FAIL=$FAIL ######"
[ "$FAIL" -eq 0 ] || exit 1
