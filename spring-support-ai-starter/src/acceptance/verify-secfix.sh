#!/bin/bash
# 越权修复的敏感性对照：ops 改凭据必须 403，模型类/纯偏好组必须 200。
#
# ★ 两条硬性纪律（2026-10-04 各踩过一次，都会产出**假绿**）：
#   1) 拿不到令牌就 FATAL 退出。原版在服务不可用时会一路跑完：
#      HTTP 全是 000、响应体根本没写出来，`code()` 于是读到**上一轮遗留的 /tmp/sf*.json**，
#      打印出「403 仅管理员可修改」「200 操作成功」——一份服务已经挂了却全绿的假报告。
#   2) 每次请求前先 rm 输出文件，并断言 HTTP != 000。curl 失败时不写文件，
#      留着旧文件就等于把上一次的结论当成本次的。
set -u
B=http://127.0.0.1:8787; UA="Mozilla/5.0"
S() { echo kali | sudo -S "$@" 2>/dev/null; }
echo "=== 现役产物 ==="
echo "  md5=$(md5sum /opt/ai-starter/app.jar | cut -c1-8)"
echo "  is-active=$(systemctl is-active ai-starter)  8787=$(ss -lntH 2>/dev/null | awk '{print $4}' | grep -c ':8787$')"
tok() { curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" "$B/v2/user/login" \
  | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null; }
Q() { echo kali | sudo -S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1" 2>/dev/null; }
code() { python3 -c "
import json
try: o=json.load(open('$1',encoding='utf-8'))
except Exception: print('NONJSON(响应体不存在或非 JSON)'); raise SystemExit
print(o.get('code'), str(o.get('msg'))[:70])"; }
# put <token> <group> <json> <临时文件> → 打印 "HTTP code msg"，并拒绝 000
put() {
  rm -f "$4"
  local c
  c=$(curl -s -m 30 -A "$UA" -H "x-oauth-token: $1" -X PUT -H 'Content-Type: application/json' \
        -d "$3" -o "$4" -w '%{http_code}' "$B/v2/settings/$2")
  if [ "$c" = "000" ]; then echo "HTTP=000 连不上（不得据此判定权限）"; else echo "HTTP=$c $(code "$4")"; fi
}

OT=$(tok ops 'Ops@Guada2026'); AT=$(tok admin admin123)
echo "  ops 令牌=${#OT}  admin 令牌=${#AT}"
if [ -z "$OT" ] || [ -z "$AT" ]; then
  echo "  [FATAL] 未取得令牌（服务未就绪或账号不可用）—— 拒绝在不可达状态下给出权限结论"
  echo "          检查：systemctl is-active ai-starter / 8787 监听 / 鉴权服务器是否可达"
  exit 1
fi

echo
echo "=== ★ 敏感性对照：ops 改凭据（修复前实测 200 成功）==="
for pair in "provider_api_key:HACK" "provider_base_url:http://evil.example/v1" "provider_app_secret:LEAK" "workspaceBaseDir:/tmp/evil"; do
  f=${pair%%:*}; v=${pair#*:}
  printf '  %-20s %s\n' "$f" "$(put "$OT" runtime "{\"$f\":\"$v\"}" /tmp/sf.json)"
done
echo "  （期望：HTTP=403 且 msg 含「仅管理员可修改」）"
echo "  库里是否被写入: $(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops' AND sys_ai_user_setting_field IN ('provider_api_key','provider_base_url','provider_app_secret','workspaceBaseDir');")  （应为 0）"

echo
echo "=== 模型类字段仍应可改（不能修过头）==="
echo "  ops PUT default_model            $(put "$OT" runtime '{"default_model":"agnes-2.0-flash"}' /tmp/sf2.json)   （应 200 成功）"
echo "  ops PUT image_search 特征模型     $(put "$OT" image_search '{"default_image_feature_model":"cn-clip-rn50-image"}' /tmp/sf3.json)   （应 200 成功）"
echo "  ops PUT face.detector_model      $(put "$OT" face '{"detector_model":"scrfd-face-detector"}' /tmp/sf4.json)   （应 200 成功）"
echo "  ops 覆盖行数 = $(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';")"

echo
echo "=== 外观组（纯偏好，整组放行）仍应可改 ==="
echo "  ops PUT appearance.language      $(put "$OT" appearance '{"language":"zh-CN"}' /tmp/sf5.json)   （应 200）"

echo
echo "=== 清理 ops 覆盖（只删 ops，不动别人的行）==="
Q "DELETE FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';" >/dev/null
echo "  ops 剩余=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';")"
echo SECFIX_VERIFY_DONE
