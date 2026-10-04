#!/bin/bash
# verify 修复的敏感性对照（正例 + 反例）。
# 只修「正例能过」是不够的：必须同时证明「不该过的不过」，
# 否则把 verify 写成恒 true 也能让套件变绿。
B=http://127.0.0.1:8787
UA="Mozilla/5.0"
F=/opt/ai-starter/acceptance-20261001/fixtures/face_probe.jpg
T=$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"admin123"}' "$B/v2/user/login" \
     | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')")
api() { local t="${1:-30}"; shift; curl -s -m "$t" -A "$UA" -H "x-oauth-token: $T" "$@"; }
P="vc-$$"
echo "=== 夹具 / 基线 ==="
echo "  md5=$(md5sum $F | cut -c1-8)  库内条目=$(api 30 "$B/v2/face/list" | python3 -c "
import json,sys
o=json.load(sys.stdin); d=o.get('data') or {}
if isinstance(d.get('data'),dict): d=d['data']
print(len(d.get('items') or []))")"

echo
echo "=== 1. 登记（走检测链路）==="
echo "  enroll -> $(api 200 -F "file=@$F" -F "id=$P" -o /tmp/vc_en.json -w '%{http_code}' "$B/v2/face/enroll")"

echo
echo "=== 2. 正例：同一张图 verify 自己（修复前为 False）==="
echo "  verify(id=$P, 同图) -> $(api 60 -F "id=$P" -F "file=@$F" -o /tmp/vc_v1.json -w '%{http_code}' "$B/v2/face/verify")"
python3 -c "
import json
d=(json.load(open('/tmp/vc_v1.json',encoding='utf-8')).get('data') or {})
if isinstance(d.get('data'),dict): d=d['data']
print('  matched=%r  → 期望 True' % d.get('matched'))"

echo
echo "=== 3. ★ 反例：不存在的 id（必须 False，否则修成了恒 true）==="
echo "  verify(id=no-such-id-xyz, 同图) -> $(api 60 -F "id=no-such-id-xyz" -F "file=@$F" -o /tmp/vc_v2.json -w '%{http_code}' "$B/v2/face/verify")"
python3 -c "
import json
o=json.load(open('/tmp/vc_v2.json',encoding='utf-8')); d=o.get('data') or {}
if isinstance(d.get('data'),dict): d=d['data']
print('  code=%s matched=%r  → 期望 00000/False' % (o.get('code'), d.get('matched')))"

echo
echo "=== 4. ★ 反例：已删除的 id（必须 False）==="
api 30 -X DELETE -o /dev/null "$B/v2/face/$P"
echo "  delete $P -> 状态见上；列表仍含该 id 数=$(api 30 "$B/v2/face/list" | grep -c "$P")"
echo "  verify(id=$P, 同图) -> $(api 60 -F "id=$P" -F "file=@$F" -o /tmp/vc_v3.json -w '%{http_code}' "$B/v2/face/verify")"
python3 -c "
import json
d=(json.load(open('/tmp/vc_v3.json',encoding='utf-8')).get('data') or {})
if isinstance(d.get('data'),dict): d=d['data']
print('  matched=%r  → 期望 False（该 id 已不在库中）' % d.get('matched'))"

echo
echo "=== 5. 反例：无人脸的图（必须 False，且不 5xx）==="
python3 -c "
import struct,zlib
def ch(t,d):
    c=t+d; return struct.pack('>I',len(d))+c+struct.pack('>I',zlib.crc32(c)&0xffffffff)
raw=b''.join(b'\x00'+bytes([200,180,160]*32) for _ in range(32))
open('/tmp/vc_blank.png','wb').write(b'\x89PNG\r\n\x1a\n'+ch(b'IHDR',struct.pack('>IIBBBBB',32,32,8,2,0,0,0))+ch(b'IDAT',zlib.compress(raw))+ch(b'IEND',b''))
"
echo "  verify(任一 id, 无人脸图) -> $(api 60 -F "id=$P" -F "file=@/tmp/vc_blank.png" -o /tmp/vc_v4.json -w '%{http_code}' "$B/v2/face/verify")"
python3 -c "
import json
o=json.load(open('/tmp/vc_v4.json',encoding='utf-8')); d=o.get('data') or {}
if isinstance(d.get('data'),dict): d=d['data']
print('  code=%s matched=%r  → 期望 00000/False（不得 5xx）' % (o.get('code'), d.get('matched')))"

echo
echo "=== 6. 清理 ==="
echo "  残留 vc- 条目=$(api 30 "$B/v2/face/list" | grep -c 'vc-')"
echo VERIFY_CONTRAST_DONE
