#!/bin/bash
B=http://127.0.0.1:8787; UA="Mozilla/5.0"
echo "=== 现役产物 ==="
echo "  md5=$(md5sum /opt/ai-starter/app.jar | cut -c1-8)"
echo "  is-active=$(systemctl is-active ai-starter)  8787=$(ss -lntH 2>/dev/null | awk '{print $4}' | grep -c ':8787$')"
tok() { curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" "$B/v2/user/login" \
  | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null; }
OT=$(tok ops 'Ops@Guada2026'); AT=$(tok admin admin123)
echo "  ops 令牌=${#OT}  admin 令牌=${#AT}"
Q() { echo kali | sudo -S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1" 2>/dev/null; }
code() { python3 -c "
import json
try: o=json.load(open('$1',encoding='utf-8'))
except Exception: print('NONJSON'); raise SystemExit
print(o.get('code'), str(o.get('msg'))[:70])"; }

echo
echo "=== ★ 敏感性对照：ops 改凭据（修复前实测 200 成功）==="
for pair in "provider_api_key:HACK" "provider_base_url:http://evil.example/v1" "provider_app_secret:LEAK" "workspaceBaseDir:/tmp/evil"; do
  f=${pair%%:*}; v=${pair#*:}
  C=$(curl -s -m 30 -A "$UA" -H "x-oauth-token: $OT" -X PUT -H 'Content-Type: application/json' \
    -d "{\"$f\":\"$v\"}" -o /tmp/sf.json -w '%{http_code}' "$B/v2/settings/runtime")
  printf '  %-20s HTTP=%s  %s\n' "$f" "$C" "$(code /tmp/sf.json)"
done
echo "  （期望：403 + 以下配置项仅管理员可修改）"
echo "  库里是否被写入: $(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops' AND sys_ai_user_setting_field IN ('provider_api_key','provider_base_url','provider_app_secret','workspaceBaseDir');")  （应为 0）"

echo
echo "=== 模型类字段仍应可改（不能修过头）==="
C=$(curl -s -m 30 -A "$UA" -H "x-oauth-token: $OT" -X PUT -H 'Content-Type: application/json' \
  -d '{"default_model":"agnes-2.0-flash"}' -o /tmp/sf2.json -w '%{http_code}' "$B/v2/settings/runtime")
echo "  ops PUT default_model HTTP=$C  $(code /tmp/sf2.json)   （应 200 成功）"
C=$(curl -s -m 30 -A "$UA" -H "x-oauth-token: $OT" -X PUT -H 'Content-Type: application/json' \
  -d '{"default_image_feature_model":"cn-clip-rn50-image"}' -o /tmp/sf3.json -w '%{http_code}' "$B/v2/settings/image_search")
echo "  ops PUT image_search 特征模型 HTTP=$C  $(code /tmp/sf3.json)   （应 200 成功）"
C=$(curl -s -m 30 -A "$UA" -H "x-oauth-token: $OT" -X PUT -H 'Content-Type: application/json' \
  -d '{"detector_model":"scrfd-face-detector"}' -o /tmp/sf4.json -w '%{http_code}' "$B/v2/settings/face")
echo "  ops PUT face.detector_model HTTP=$C  $(code /tmp/sf4.json)   （应 200 成功）"
echo "  ops 覆盖行数 = $(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';")"

echo
echo "=== 外观组（纯偏好，整组放行）仍应可改 ==="
C=$(curl -s -m 30 -A "$UA" -H "x-oauth-token: $OT" -X PUT -H 'Content-Type: application/json' \
  -d '{"language":"zh-CN"}' -o /tmp/sf5.json -w '%{http_code}' "$B/v2/settings/appearance")
echo "  ops PUT appearance.language HTTP=$C  $(code /tmp/sf5.json)   （应 200）"

echo
echo "=== 清理 ops 覆盖 ==="
Q "DELETE FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';" >/dev/null
echo "  剩余=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops';")"
echo SECFIX_VERIFY_DONE