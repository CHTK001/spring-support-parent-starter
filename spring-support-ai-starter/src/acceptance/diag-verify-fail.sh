#!/bin/bash
# 诊断 facegrade 第 10.2 组唯一失败项：「核验命中（同一张图）」
# 判据链：enroll-feature(直入向量) -> verify(id + 同一张图) 应 matched=true
B=http://127.0.0.1:8787
UA="Mozilla/5.0"
F=/opt/ai-starter/acceptance-20261001/fixtures/face_probe.jpg
S() { echo kali | sudo -S "$@" 2>/dev/null; }
Q() { S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1"; }
T=$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"admin123"}' "$B/v2/user/login" \
     | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')")
api() { local t="${1:-30}"; shift; curl -s -m "$t" -A "$UA" -H "x-oauth-token: $T" "$@"; }

echo "=== 0. 前置：夹具 / 覆盖行 / 阈值 ==="
echo "  夹具 md5=$(md5sum $F | cut -c1-8) size=$(stat -c %s $F)"
echo "  sys_ai_user_setting 行数 = $(Q 'SELECT COUNT(*) FROM sys_ai_user_setting;')"
api 30 "$B/v2/settings/face" | python3 -c "
import json,sys
o=json.load(sys.stdin); d=o.get('data') or {}
if isinstance(d.get('data'),dict): d=d['data']
for k in ['detector_model','feature_model','match_threshold','compare_threshold','top_k','feature_dimension']:
    print('  face.%s = %r' % (k, d.get(k)))"

P="diag-$$"
echo
echo "=== 1. /v2/face/feature（同一张图）==="
api 60 -F "file=@$F" -o /tmp/d_feat.json "$B/v2/face/feature" >/dev/null
FT=$(python3 -c "
import json
d=json.load(open('/tmp/d_feat.json',encoding='utf-8')).get('data') or {}
f=d.get('feature') or []
print(json.dumps(f))")
echo "  维度=$(python3 -c "import json;print(len(json.loads('''$FT''')))")"
echo "  前 3 维=$(python3 -c "import json;print(json.loads('''$FT''')[:3])")"

echo
echo "=== 2. /v2/face/enroll-feature（直入该向量）==="
code=$(api 60 -F "id=$P" -F "feature=$FT" -o /tmp/d_enr.json -w '%{http_code}' "$B/v2/face/enroll-feature")
echo "  HTTP=$code  body=$(cat /tmp/d_enr.json | head -c 200)"
echo "  列表可见=$(api 30 "$B/v2/face/list" | grep -c "$P")"

echo
echo "=== 3. ★ /v2/face/verify（同一张图）——失败项所在 ==="
echo "  响应: $(api 60 -F "id=$P" -F "file=@$F" -o /tmp/d_ver.json -w '%{http_code}' "$B/v2/face/verify")"
cat /tmp/d_ver.json | head -c 500; echo
echo "  解析: $(python3 -c "
import json
o=json.load(open('/tmp/d_ver.json',encoding='utf-8')); d=o.get('data') or {}
if isinstance(d.get('data'),dict): d=d['data']
print('code=%s matched=%r score=%r threshold=%r' % (o.get('code'), d.get('matched'), d.get('score'), d.get('threshold')))")"

echo
echo "=== 4. 对照组 A：1:1 compare（同图 vs 同图）==="
cat /tmp/d_ver.json >/dev/null
api 200 -F "fileA=@$F" -F "fileB=@$F" -o /tmp/d_cmp.json "$B/v2/face/compare" >/dev/null
python3 -c "
import json
o=json.load(open('/tmp/d_cmp.json',encoding='utf-8')); d=o.get('data') or {}
if isinstance(d.get('data'),dict): d=d['data']
print('  compare: code=%s matched=%r score=%r' % (o.get('code'), d.get('matched'), d.get('score')))"

echo
echo "=== 5. 对照组 B：identify（同一张图）==="
api 200 -F "file=@$F" -o /tmp/d_id.json "$B/v2/face/identify" >/dev/null
python3 -c "
import json
o=json.load(open('/tmp/d_id.json',encoding='utf-8')); d=o.get('data') or {}
if isinstance(d.get('data'),dict): d=d['data']
hits=d.get('hits') or []
mine=[h for h in hits if h.get('id')=='$P']
print('  identify: matched=%r hits=%d 本次条目在命中里=%s score=%r' % (d.get('matched'), len(hits), bool(mine), (mine[0].get('score') if mine else None)))"

echo
echo "=== 6. 对照组 C：先 enroll（走检测+裁剪）再 verify ==="
P2="diag2-$$"
api 200 -F "file=@$F" -F "id=$P2" -o /tmp/d_en2.json "$B/v2/face/enroll" >/dev/null
echo "  enroll HTTP 体: $(cat /tmp/d_en2.json | head -c 160)"
api 60 -F "id=$P2" -F "file=@$F" -o /tmp/d_ver2.json "$B/v2/face/verify" >/dev/null
python3 -c "
import json
o=json.load(open('/tmp/d_ver2.json',encoding='utf-8')); d=o.get('data') or {}
if isinstance(d.get('data'),dict): d=d['data']
print('  verify(检测入库): code=%s matched=%r score=%r' % (o.get('code'), d.get('matched'), d.get('score')))"

echo
echo "=== 7. 直接比两个向量（直入的 vs 检测链路的）==="
python3 -c "
import json
a=json.load(open('/tmp/d_feat.json',encoding='utf-8')).get('data') or {}
a=a.get('feature') or []
b=json.loads(open('/tmp/d_ver.json',encoding='utf-8').read())
print('  直入向量维度=%d' % len(a))
import math
sa=sum(x*x for x in a); print('  直入向量范数=%.6f' % math.sqrt(sa))
"

echo
echo "=== 8. 清理 ==="
echo "  delete $P  -> $(api 30 -X DELETE -o /dev/null -w '%{http_code}' "$B/v2/face/$P")"
echo "  delete $P2 -> $(api 30 -X DELETE -o /dev/null -w '%{http_code}' "$B/v2/face/$P2")"
echo "  残留 diag 条目=$(api 30 "$B/v2/face/list" | grep -c 'diag')"
echo DIAG_VERIFY_DONE
