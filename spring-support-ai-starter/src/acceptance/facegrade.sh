#!/bin/bash
# 人脸识别生产级验收（可重复跑，对标以图搜图的 prodgrade.sh）
#
# 覆盖 7 组：能力与输入健壮性 / 检测 / 特征 / 1:1 比对 / 1:N 登记识别 /
#            快照持久化 / 鉴权与清理
#
# 依赖：一张含人脸的测试图。
#   仓库来源（已入库，勿再从别处找）：
#     vue-support-parent-starter/apps/vue-support-ai-guada/public/gen-style/photo.jpg
#     md5=3b3742a673d6648e3ff2c1e19c303132  12131 字节  256x256 JPEG
#     实测 SCRFD 检出 1 张脸、置信度 0.8776
#   部署到**持久目录**（绝对不要放 /tmp）：
#     2026-10-01 07:17 那次重启清空了 /tmp，本脚本原本指向 /tmp/face_probe_6.bin，
#     直接 FATAL「测试图不存在」而整套无法复跑 —— 夹具必须是版本控制里的资产。
#     sudo mkdir -p /opt/ai-starter/acceptance-20261001/fixtures
#     sudo install -m 644 photo.jpg /opt/ai-starter/acceptance-20261001/fixtures/face_probe.jpg
#   可用 AI_FACE_IMG 覆盖。
F=${AI_FACE_IMG:-/opt/ai-starter/acceptance-20261001/fixtures/face_probe.jpg}
#
# 约定：ck <名称> <实际> <期望>   —— 顺序固定
set -u
B=${AI_BASE:-http://127.0.0.1:8787}
UA="Mozilla/5.0"
P=0; F_FAIL=0
ck(){ if [ "$2" = "$3" ]; then echo "  [PASS] $1 -> $2"; P=$((P+1)); else echo "  [FAIL] $1 (实际=$2 期望=$3)"; F_FAIL=$((F_FAIL+1)); fi; }

api(){ local t="${1:-30}"; shift; curl -s -m "$t" -A "$UA" -H "x-oauth-token: $TOKEN" "$@"; }
jq_(){ python3 -c "
import json,sys
try:
    o=json.load(open('$1',encoding='utf-8'))
except Exception:
    print('PARSE_FAIL'); raise SystemExit
d=o.get('data')
if isinstance(d,dict) and 'data' in d: d=d['data']
$2
"; }

TOKEN=$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' "$B/v2/user/login" \
  | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null)
if [ -z "$TOKEN" ]; then echo "[FATAL] 未取得令牌"; exit 1; fi
echo "令牌长度=${#TOKEN}  测试图=$F ($(stat -c %s "$F" 2>/dev/null || echo 0) bytes)"

if [ ! -s "$F" ]; then
  echo "[FATAL] 测试图不存在或为空: $F"
  echo "        夹具必须来自版本控制并装到持久目录，**不要放 /tmp**（2026-10-01 07:17 重启清空 /tmp 后整套无法复跑）。"
  echo "        取用步骤："
  echo "          # 源文件（仓库内，已入库）"
  echo "          src=vue-support-parent-starter/apps/vue-support-ai-guada/public/gen-style/photo.jpg"
  echo "          # 期望 md5=3b3742a673d6648e3ff2c1e19c303132  size=12131（装完请回读校验）"
  echo "          sudo mkdir -p /opt/ai-starter/acceptance-20261001/fixtures"
  echo "          sudo install -m 644 \"\$src\" /opt/ai-starter/acceptance-20261001/fixtures/face_probe.jpg"
  echo "          md5sum /opt/ai-starter/acceptance-20261001/fixtures/face_probe.jpg"
  echo "        或用 AI_FACE_IMG 指向另一张含人脸的图。"
  exit 1
fi

echo
echo "################ 1. 能力与输入健壮性 ################"
api 30 -o /tmp/f_models.json "$B/v2/face/models" -w '%{http_code}' > /tmp/f_c1.txt
RES=$(python3 -c "
import json
o=json.load(open('/tmp/f_models.json',encoding='utf-8'))
d=o.get('data') or {}
if isinstance(d,dict) and isinstance(d.get('data'),dict): d=d['data']
print('%s|%s|%s' % (o.get('code'), 'detector' in (d or {}), 'feature' in (d or {})))")
ck "人脸能力已启用且列出 detector/feature" "$RES" "00000|True|True"

printf 'not an image at all' > /tmp/f_text.bin
: > /tmp/f_empty.bin
head -c 300 /dev/urandom > /tmp/f_garbage.bin
python3 -c "
import struct,zlib
def chunk(t,d):
    c=t+d; return struct.pack('>I',len(d))+c+struct.pack('>I',zlib.crc32(c)&0xffffffff)
raw=b''.join(b'\x00'+bytes([200,180,160]*32) for _ in range(32))
open('/tmp/f_blank.png','wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
"
for F2 in f_text.bin f_empty.bin f_garbage.bin; do
  R=$(api 60 -F "file=@/tmp/$F2" -o /tmp/f_r.json -w '%{http_code}' "$B/v2/face/detect")
  ck "畸形检测 $F2 不 5xx" "$([ "$R" -ge 500 ] 2>/dev/null && echo bad || echo ok)" "ok"
done

echo
echo "################ 2. 检测 ################"
R=$(api 120 -F "file=@$F" -o /tmp/f_det.json -w '%{http_code}' "$B/v2/face/detect")
ck "真实人脸图 HTTP 200" "$R" "200"
N=$(jq_ /tmp/f_det.json "print(len(d) if isinstance(d,list) else (d or {}).get('count'))")
ck "检出人脸数 >= 1" "$([ "${N:-0}" -ge 1 ] 2>/dev/null && echo yes || echo no)" "yes"
BOX=$(jq_ /tmp/f_det.json "
f = (d[0] if isinstance(d,list) else ((d or {}).get('faces') or [{}])[0])
b = f.get('box') or f.get('bbox') or {}
print('yes' if b.get('width') and b.get('height') else 'no')")
ck "首张人脸带框" "$BOX" "yes"
KP=$(jq_ /tmp/f_det.json "
f = (d[0] if isinstance(d,list) else ((d or {}).get('faces') or [{}])[0])
b = f.get('box') or {}
print('yes' if b.get('keypoints') else 'no')")
ck "首张人脸带关键点（在 box 内）" "$KP" "yes"

R=$(api 120 -F "file=@/tmp/f_blank.png" -o /tmp/f_det0.json -w '%{http_code}' "$B/v2/face/detect")
N0=$(jq_ /tmp/f_det0.json "print(len(d) if isinstance(d,list) else (d or {}).get('count'))")
ck "纯色图检不出人脸（0）" "${N0:-0}" "0"

echo
echo "################ 3. 特征 ################"
R=$(api 200 -F "file=@$F" -o /tmp/f_feat.json -w '%{http_code}' "$B/v2/face/feature")
DIM=$(jq_ /tmp/f_feat.json "f=(d or {}).get('feature'); print(len(f) if f else 0)")
ck "特征维度 = feature_dimension(512)" "$DIM" "512"
R=$(api 200 -F "file=@/tmp/f_blank.png" -o /tmp/f_feat0.json -w '%{http_code}' "$B/v2/face/feature")
DIM0=$(jq_ /tmp/f_feat0.json "f=(d or {}).get('feature'); print(len(f) if f else 0)")
ck "无人脸时特征为空（不报 5xx）" "$(echo $R | cut -c1)$([ "${DIM0:-0}" = "0" ] && echo ok || echo bad)" "2ok"

echo
echo "################ 4. 1:1 比对 ################"
R=$(api 200 -F "fileA=@$F" -F "fileB=@$F" -o /tmp/f_cmp.json -w '%{http_code}' "$B/v2/face/compare")
SAME=$(jq_ /tmp/f_cmp.json "print('%s|%s' % (round((d or {}).get('score',0),3), (d or {}).get('matched')))")
ck "同一张图 score=1.0 且 matched" "$SAME" "1.0|True"
R=$(api 200 -F "fileA=@$F" -F "fileB=@/tmp/f_blank.png" -o /tmp/f_cmp2.json -w '%{http_code}' "$B/v2/face/compare")
c_ok=$(python3 -c "
import json
o=json.load(open('/tmp/f_cmp2.json',encoding='utf-8'))
print('ok' if o.get('code') != '00000' or (o.get('data') or {}).get('matched') is False else 'bad')" 2>/dev/null)
ck "人脸 vs 无人脸 = 不匹配 / 明确报错" "$c_ok" "ok"

echo
echo "################ 5. 1:N 登记与识别 ################"
echo "  入库前库内条目数: $(sudo_ bash -c "python3 -c \"
import json
try:
    print(len(json.load(open('/opt/ai-starter/runtime/admin/face/index.json',encoding='utf-8'))))
except Exception: print('?')
\"" 2>/dev/null)"
# 空库行为必须被验到（新账号第一次识别的场景）：报明确业务码 + matched=false，而不是 NPE
R=$(api 200 -F "file=@$F" -o /tmp/f_idempty.json -w '%{http_code}' "$B/v2/face/identify")
EMPTYRES=$(jq_ /tmp/f_idempty.json "
print('%s|%s' % (o.get('code'), (d or {}).get('matched')))")
BEFORE_N=$(sudo_ bash -c "python3 -c \"
import json
try:
    print(len(json.load(open('/opt/ai-starter/runtime/admin/face/index.json',encoding='utf-8'))))
except Exception: print(-1)
\"" 2>/dev/null)
if [ "${BEFORE_N:-0}" = "0" ]; then
  ck "空库识别：明确业务码且不报错（原为 NPE）" "$EMPTYRES" "00000|False"
else
  echo "  [SKIP] 库非空（$BEFORE_N 条），空库场景留待空库时验证"
fi

PROBE="facegrade-$RANDOM$RANDOM"
R=$(api 200 -F "file=@$F" -F "id=$PROBE" -o /tmp/f_en.json -w '%{http_code}' "$B/v2/face/enroll")
ck "登记成功" "$(jq_ /tmp/f_en.json "print(o.get('code'))")" "00000"
R=$(api 200 -F "file=@$F" -o /tmp/f_id.json -w '%{http_code}' "$B/v2/face/identify")
# 注意：库里可能存在「同一张图重复登记」的条目（彼此 score 都是 1.0），
# repo 也没有人脸删除接口，因此不能断言「top1 就是本次这条」——
# 只能断言「本次这条出现在命中列表里且 score≈1.0」。这是用例判据问题，不是产品缺陷。
MID=$(jq_ /tmp/f_id.json "
hits=(d or {}).get('hits') or []
mine=[h for h in hits if h.get('id')=='$PROBE']
print('%s|%s' % ('found' if mine else 'missing', round(mine[0].get('score'),2) if mine else 0))")
ck "本次登记的条目出现在命中列表且 score=1.0" "$MID" "found|1.0"
R=$(api 200 -F "file=@/tmp/f_blank.png" -o /tmp/f_id0.json -w '%{http_code}' "$B/v2/face/identify")
# 必须同时断言业务码：只看「命中 0 条」会把「报错」误判成「空结果」（真空绿灯）
NOFALSE=$(jq_ /tmp/f_id0.json "
hits=(d or {}).get('hits')
print('%s|%s|%s' % (o.get('code'),
      'empty' if hits == [] else 'nonempty',
      'notmatched' if (d or {}).get('matched') is False else 'matched'))")
ck "无人脸的图返回 00000 且 matched=false 且 hits 为空" "$NOFALSE" "00000|empty|notmatched"

echo
echo "################ 6. 快照持久化（重启后仍在）################"
sudo_() { echo kali | sudo -S "$@" 2>/dev/null; }
SNAP_OK=$(sudo_ bash -c "python3 -c \"
import json
try:
    x=json.load(open('/opt/ai-starter/runtime/admin/face/index.json',encoding='utf-8'))
    ids=[r.get('id') for r in x]
    print('yes' if '$PROBE' in ids else 'no')
except Exception as e:
    print('no')
\"" 2>/dev/null)
ck "快照文件里有该条目" "$SNAP_OK" "yes"

echo "  重启服务..."
sudo_ systemctl restart ai-starter
for i in $(seq 1 200); do
  C=$(curl -s -f -m 3 -o /dev/null -w '%{http_code}' -A "$UA" "$B/v1/captcha" 2>/dev/null)
  [ "$C" = "200" ] && break
  sleep 2
done
TOKEN=$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' "$B/v2/user/login" \
  | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null)
R=$(api 200 -F "file=@$F" -o /tmp/f_id2.json -w '%{http_code}' "$B/v2/face/identify")
AFTER=$(jq_ /tmp/f_id2.json "
hits=(d or {}).get('hits') or []
mine=[h for h in hits if h.get('id')=='$PROBE']
print('%s|%s' % ('found' if mine else 'missing', round(mine[0].get('score'),2) if mine else 0))")
ck "重启后仍能识别出本次登记的那条" "$AFTER" "found|1.0"

echo
echo "################ 7. 列表与删除 ################"
# 列表：后端此前没有列举出口，前端只能在浏览器本地存登记簿；现在服务端能列了
R=$(api 60 -o /tmp/f_ls.json -w '%{http_code}' "$B/v2/face/list")
LISTED=$(jq_ /tmp/f_ls.json "
items=(d or {}).get('items') or []
mine=[r for r in items if r.get('id')=='$PROBE']
print('%s|%s' % ('listed' if mine else 'missing', 'withmeta' if (mine and isinstance(mine[0].get('metadata'),dict)) else 'nometa'))")
ck "列表里能查到本次条目且带元数据" "$LISTED" "listed|withmeta"

R=$(api 60 -X DELETE -o /tmp/f_del.json -w '%{http_code}' "$B/v2/face/$PROBE")
DELOK=$(jq_ /tmp/f_del.json "print('%s|%s' % (o.get('code'), d))")
ck "删除返回成功" "$DELOK" "00000|True"

R=$(api 60 -o /tmp/f_ls2.json -w '%{http_code}' "$B/v2/face/list")
GONE=$(jq_ /tmp/f_ls2.json "
items=(d or {}).get('items') or []
print('gone' if all(r.get('id')!='$PROBE' for r in items) else 'still')")
ck "列表里已查不到" "$GONE" "gone"

R=$(api 200 -F "file=@$F" -o /tmp/f_id3.json -w '%{http_code}' "$B/v2/face/identify")
NOMATCH=$(jq_ /tmp/f_id3.json "
hits=(d or {}).get('hits') or []
print('nomatch' if all(h.get('id')!='$PROBE' for h in hits) else 'stillmatch')")
ck "识别不再命中已删除的条目" "$NOMATCH" "nomatch"

echo "  再重启一次：删除必须也持久化（快照里不能复活）..."
sudo_ systemctl restart ai-starter
for i in $(seq 1 200); do
  C=$(curl -s -f -m 3 -o /dev/null -w '%{http_code}' -A "$UA" "$B/v1/captcha" 2>/dev/null)
  [ "$C" = "200" ] && break
  sleep 2
done
TOKEN=$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' "$B/v2/user/login" \
  | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null)
R=$(api 60 -o /tmp/f_ls3.json -w '%{http_code}' "$B/v2/face/list")
STAY=$(jq_ /tmp/f_ls3.json "
items=(d or {}).get('items') or []
print('gone' if all(r.get('id')!='$PROBE' for r in items) else 'revived')")
ck "重启后删除仍然生效（快照已同步）" "$STAY" "gone"

echo
echo "################ 8. 鉴权 ################"
ck "匿名访问被拒" "$(curl -s -m 20 -A "$UA" -o /dev/null -w '%{http_code}' "$B/v2/face/models")" "403"
ck "伪 token 被拒" "$(curl -s -m 20 -A "$UA" -H 'x-oauth-token: forged' -o /dev/null -w '%{http_code}' "$B/v2/face/models")" "403"

echo
echo "################ 小结 ################"
echo "  PASS=$P  FAIL=$F_FAIL"
echo "  说明：本套件自清理 —— 测试条目 $PROBE 已在第 7 组删除并验证"
echo FACEGRADE_DONE
