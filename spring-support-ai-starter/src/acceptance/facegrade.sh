#!/bin/bash
# 人脸识别生产级验收（可重复跑，对标以图搜图的 prodgrade.sh）
#
# 覆盖：能力与输入健壮性 / 检测 / 特征 / 1:1 比对 / 1:N 登记识别 /
#            快照持久化 / 鉴权与清理 / 配置热更新后能力保持可用（rebuild 回归）
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
echo "################ 9. 配置热更新后能力保持可用（rebuild 回归） ################"
# 这组针对一个真实缺陷：超分模型是唯一带「留空回退默认」的字段，
# 而 rebuild() 曾经直接 create(配置值) 不带回退。于是配置留空时
# 「刚启动能用 -> 改过任意 face 配置就用不了」，调用抛
# IllegalStateException: 模型未注册，表现为 HTTP 500。
#
# 线上 super_resolution_model 就是空字符串，所以这个组合是现场成立的，
# 且触发 rebuild 的正是管理员改配置这条主路径。
#
# 断言必须打在【服务端日志签名】上，不能打 HTTP 响应体：
# 框架对未捕获异常返回的是通用信封 {"msg":"数据处理异常，请稍后重试"}，
# 「模型未注册」四个字只出现在 app-out.log 里。
# 第一版断言查响应体，于是在坏产物上照样打出 [PASS] —— 假绿灯。
#
# 也不用「必须 200」：回退到的默认模型另有独立的 DJL 侧问题（见交付说明），
# 那是另一件事。本组只钉住本缺陷的签名。
#
# 日志路径不硬编码：与 11-log.conf 的 StandardOutput 保持一致，可用 AI_APP_LOG 覆盖。
SRLOG=${AI_APP_LOG:-/opt/ai-starter/app-out.log}
sr_sig() { sudo_ bash -c "grep -ac '模型未注册' '$SRLOG' 2>/dev/null" 2>/dev/null || echo 0; }

N0=$(sr_sig)
SR_BEFORE=$(api 180 -F "file=@$F" -o /tmp/f_sr_b.json -w '%{http_code}' "$B/v2/face/super-resolution")
N1=$(sr_sig)
echo "  热更新前（刚重启，走留空回退）：HTTP=$SR_BEFORE  日志签名增量=$((N1-N0))"

ck "热更新前超分不因「模型未注册」失败" "$((N1-N0))" "0"

TOPK0=$(api 30 "$B/v2/settings/face" | python3 -c "
import json,sys
try: o=json.load(sys.stdin)
except Exception: print(''); raise SystemExit
d=o.get('data') or o
if isinstance(d,dict) and isinstance(d.get('data'),dict): d=d['data']
print((d or {}).get('top_k') or '')" 2>/dev/null)

# 改一个与模型无关的字段，触发 FaceSettingsChangedEvent -> rebuild()
api 40 -X PUT -H 'Content-Type: application/json' -d '{"top_k":"6"}' -o /dev/null "$B/v2/settings/face"
sleep 4
api 40 -X PUT -H 'Content-Type: application/json' -d "{\"top_k\":\"${TOPK0:-5}\"}" -o /dev/null "$B/v2/settings/face"
echo "  已触发一次 rebuild（PUT face.top_k）并复原"

SR_AFTER=$(api 180 -F "file=@$F" -o /tmp/f_sr_a.json -w '%{http_code}' "$B/v2/face/super-resolution")
N2=$(sr_sig)
echo "  热更新后（走 rebuild）：HTTP=$SR_AFTER  日志签名增量=$((N2-N1))"

ck "热更新后超分不因「模型未注册」失败" "$((N2-N1))" "0"
ck "热更新前后超分状态码一致（rebuild 不应改变能力）" "$SR_AFTER" "$SR_BEFORE"
if [ "$((N2-N1))" != "0" ]; then
  echo "         根因: rebuild() 未对 super_resolution_model 应用留空回退默认，"
  echo "         把绑定默认模型的包装换成了绑定空模型标识的包装"
fi

echo
echo "################ 10. 补齐此前未覆盖的端点 ################"
# 本组来源：对 /v2/face + /v2/image-search + /v2/image-recognition 全端点做
# 「实际端点 vs 验收引用路径」对齐，再让浏览器真跑一遍取后端收到的请求增量，
# 发现 19 个端点三套验收都没碰过（浏览器九套实际上不测 /v2/face/*）。
#
# 断言分三类，都按【实测行为 + 设计意图】写，不按猜测：
#   (a) 必选链路（detector/feature 已配置）应可用 -> 200 + code 00000
#   (b) 可选能力关闭时 -> 必须是 200 + 结构完整信封（业务错误），
#       **不得是 5xx / 容器错误页**。不断言具体 code：
#       它取决于该能力是否被配置，属环境状态而非契约。
#   (c) 二进制端点（超分/修复）关闭时 -> 不得返回 200 图像
G10ID="facegrade-g10-$$"

# 顶层 code 读取：不能用 jq_ —— 它会把 data 解包，而业务错误信封没有 data，
# 于是对「未配置」这类响应会取到空串，断言变成假失败。
topcode() {
  python3 -c "
import json
try: o=json.load(open('$1',encoding='utf-8'))
except Exception: print(''); raise SystemExit
print(o.get('code') or '')"
}

# --- 10.1 人脸对齐（必选链路，纯图像处理）---
ck "人脸对齐可用" "$(api 60 -F "file=@$F" -o /tmp/g10_align.json -w '%{http_code}' "$B/v2/face/align")" "200"
ck "人脸对齐返回成功码" "$(topcode /tmp/g10_align.json)" "00000"

# --- 10.2 特征向量直入 -> 列表可见 -> 核验命中 -> 删除（全环自清）---
api 60 -F "file=@$F" -o /tmp/g10_feat.json "$B/v2/face/feature" > /dev/null
FT=$(jq_ /tmp/g10_feat.json "import json as _j; print(_j.dumps((d or {}).get('feature') or []))")
FTLEN=$(python3 -c "import json;print(len(json.loads('''$FT''')))" 2>/dev/null)
ck "特征维度与 feature_dimension 一致" "$FTLEN" "512"

ck "特征向量直入可用" "$(api 60 -F "id=$G10ID" -F "feature=$FT" -o /tmp/g10_enr.json -w '%{http_code}' "$B/v2/face/enroll-feature")" "200"
ck "特征向量直入返回成功码" "$(topcode /tmp/g10_enr.json)" "00000"
ck "直入后列表可见" "$(api 30 "$B/v2/face/list" | grep -c "$G10ID")" "1"

# 核验：同一张图应与刚直入的特征匹配
ck "人脸核验可用" "$(api 60 -F "id=$G10ID" -F "file=@$F" -o /tmp/g10_ver.json -w '%{http_code}' "$B/v2/face/verify")" "200"
ck "核验命中（同一张图）" "$(jq_ /tmp/g10_ver.json 'print((d or {}).get("matched"))')" "True"

# 检索：维度守卫（用小向量，避免触发下面 10.3 记录的 URI 长度问题）
SMALL=$(api 60 --get --data-urlencode "feature=[0.1,0.2,0.3,0.4]" --data-urlencode "topK=5" \
        -o /tmp/g10_se.json -w '%{http_code}' "$B/v2/face/search")
ck "人脸检索对维度不符给出业务错误（非 5xx）" "$SMALL" "200"
ck "维度不符时返回结构完整信封" "$(topcode /tmp/g10_se.json)" "S0503C0000"

# 清理并确认删除生效
ck "删除直入条目" "$(api 30 -X DELETE -o /tmp/g10_del.json -w '%{http_code}' "$B/v2/face/$G10ID")" "200"
ck "删除后列表已不可见" "$(api 30 "$B/v2/face/list" | grep -c "$G10ID")" "0"

# --- 10.3 检索端点的一个已知缺陷（不断言为「正常」，只钉住现状，避免回归时静默）---
# GET /v2/face/search 用 【正确的 512 维向量】时 URL 约 6.8KB，超过 Tomcat 的
# 请求行/头上限，被容器以 400 + 默认 HTML 错误页拒绝 —— 连业务信封都到不了。
# 即该端点按设计用法（512 维）不可用。详情见交付说明 §20。
# 这里只断言「小向量走业务信封」已在 10.2 覆盖；512 维路径不做 200 断言，
# 否则等于把缺陷写进契约。前后端均无调用方（已核查），故未单方面改 HTTP 契约。
SEARCH512=$(api 60 --get --data-urlencode "feature=$FT" -o /tmp/g10_s512.out -w '%{http_code}' "$B/v2/face/search")
echo "  [记录] GET /v2/face/search 传 512 维实际返回 HTTP=$SEARCH512（容器层拒绝，代码契约应为业务信封）"

# --- 10.4 配置端点（@Permission admin）---
ck "读取人脸配置可用" "$(api 30 -o /tmp/g10_cfg.json -w '%{http_code}' "$B/v2/face/config")" "200"
ck "配置字段数（SPI 声明 31 项）" "$(jq_ /tmp/g10_cfg.json 'print(len(d or []))')" "31"
# 必须精确匹配字段名：子串匹配会把 anime_detector_model 也算成 detector_model（第一版就这么假失败过）
FIELD_HIT() {
  python3 -c "
import json
try: d=json.load(open('$1',encoding='utf-8')).get('data') or []
except Exception: print('no'); raise SystemExit
print('yes' if any(isinstance(x,dict) and x.get('field')=='$2' for x in d) else 'no')"
}
ck "配置含必选 detector_model" "$(FIELD_HIT /tmp/g10_cfg.json detector_model)" "yes"
ck "配置含必选 feature_model" "$(FIELD_HIT /tmp/g10_cfg.json feature_model)" "yes"
ck "模型清单可用" "$(api 120 -o /tmp/g10_models.json -w '%{http_code}' "$B/v2/face/config/models")" "200"
ck "模型清单含检测/特征/超分三类" "$(jq_ /tmp/g10_models.json 'print(",".join(k for k in ("detector","feature","superResolution") if d.get(k)))')" "detector,feature,superResolution"

# --- 10.5 可选能力关闭时：必须降级成结构完整信封，不得 5xx ---
for ep in liveness anime-detect attributes emotion deepfake; do
  ST=$(api 60 -F "file=@$F" -o /tmp/g10_opt.json -w '%{http_code}' "$B/v2/face/$ep")
  CC=$(topcode /tmp/g10_opt.json)
  ck "可选能力 $ep 关闭时不返回 5xx" "$ST" "200"
  ck "可选能力 $ep 关闭时信封结构完整" "$(python3 -c "print('yes' if '''$CC''' else 'no')")" "yes"
done

# --- 10.6 二进制端点关闭时不得返回 200 图像 ---
for ep in super-resolution restore; do
  ST=$(api 180 -F "file=@$F" -o /dev/null -w '%{http_code}' "$B/v2/face/$ep")
  ck "二进制端点 $ep 关闭时不返回 200" "$(python3 -c "print('not200' if '$ST' != '200' else '200')")" "not200"
  echo "         （实测 HTTP=$ST；控制器代码意图是 badRequest=400）"
done

# --- 10.7 图像识别（image 分组，未配置能力应降级）---
ck "图像综合分析可用" "$(api 120 -F "file=@$F" -o /tmp/g10_ia.json -w '%{http_code}' "$B/v2/image-recognition/analyze")" "200"
ck "图像综合分析返回成功码" "$(topcode /tmp/g10_ia.json)" "00000"
ck "图像模型清单可用" "$(api 60 -o /tmp/g10_im.json -w '%{http_code}' "$B/v2/image-recognition/models")" "200"
ck "图像超分未启用时返回 409（已在控制器判定）" "$(api 120 -F "file=@$F" -o /tmp/g10_isr.out -w '%{http_code}' "$B/v2/image-recognition/super-resolution")" "409"

# --- 10.8 以图搜图缩略图（浏览器经 <img src> 触达，后端这里独立钉住）---
IMGID=$(api 30 "$B/v2/image-search/list" | jq_ /dev/stdin "print((d or {}).get('items',[{}])[0].get('id') if isinstance(d,dict) else '')" 2>/dev/null)
if [ -n "$IMGID" ]; then
  api 30 -o /tmp/g10_thumb.png -D /tmp/g10_thumb.h "$B/v2/image-search/$IMGID/image" > /dev/null
  # 按魔数判定，不按 Content-Type：该端点实际返回 application/octet-stream，
  # 而字节是真 PNG（89 50 4E 47）。魔数才是「这是不是一张图」的确证。
  ck "底库缩略图是真实 PNG（魔数）" "$(python3 -c "print('png' if open('/tmp/g10_thumb.png','rb').read(4)==b'\x89PNG' else 'notpng')")" "png"
  ck "底库缩略图非空" "$(python3 -c "import os;print('yes' if os.path.getsize('/tmp/g10_thumb.png')>0 else 'no')")" "yes"
  # 记录（不断言）：Content-Type 与实际字节不符，且响应带 nosniff。见交付说明 §20。
  echo "  [记录] 缩略图 Content-Type=$(grep -i '^content-type' /tmp/g10_thumb.h | tr -d '\r' | cut -d' ' -f2-)，实际字节为 PNG"
else
  echo "  [SKIP] 底库为空，缩略图用例跳过"
fi

echo
echo "################ 小结 ################"
echo "  PASS=$P  FAIL=$F_FAIL"
echo "  说明：本套件自清理 —— 测试条目 $PROBE 已在第 7 组删除并验证"
echo FACEGRADE_DONE
