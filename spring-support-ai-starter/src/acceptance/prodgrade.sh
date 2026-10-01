#!/bin/bash
# 生产级验收（对当前构建 b8f7059bbbe3c7ec92a3b991abb836a1）
#
# 覆盖 7 组：输入健壮性 / 并发与幂等 / 数据完整性 / 重启一致性 /
#           鉴权 / 配置热更新 / 资源与落盘
#
# 约定：ck <名称> <实际> <期望>   —— 顺序固定，前面写反过两次，此处刻意标注
set -u
# 基址与底库路径可取环境变量：隔离实例验收时指到 8788 / 私有底库目录
B=${AI_BASE:-http://127.0.0.1:8787}
UA="Mozilla/5.0"
sudo_() { echo kali | sudo -S "$@" 2>/dev/null; }
# 以图搜图已改为「按账号隔离」布局：<runtime>/<账号>/imagesearch/{index.json,images/,next-id,*.vectors}。
# 旧默认值 /opt/ai-starter/runtime/image-search-store 是隔离改造前的单账号路径，**已不存在**：
# 用它统计 images/ 文件数会前后都得到 0（0 < 0 为假 -> 断言恒假），
# 用它 cat index.json 会读到空串 -> json.loads 抛 JSONDecodeError -> 期望值恒为空。
# 两条都曾因此假红（2026-10-01 实测 36/38），故此处改为按账号推导并做存在性硬断言。
ACC=${AI_ACC:-admin}
STORE=${AI_STORE:-/opt/ai-starter/runtime/$ACC/imagesearch}
if ! sudo_ test -d "$STORE"; then
  # 账号目录不在就退化为「发现唯一一处」，并仍然保持硬断言
  STORE=$(sudo_ bash -c "ls -1d /opt/ai-starter/runtime/*/imagesearch 2>/dev/null | head -1")
fi
if ! sudo_ test -d "$STORE"; then
  echo "  [FATAL] 找不到以图搜图存储目录（ACC=$ACC）；本组落盘断言会恒假，拒绝继续"
  echo "          期望形如 /opt/ai-starter/runtime/<账号>/imagesearch"
  exit 2
fi
echo "  [INFO] ACC=$ACC  STORE=$STORE"
LOG=${AI_LOG:-/opt/ai-starter/app-out.log}
# 必须 export：第 7 组的期望值是在 python 里用 subprocess 起内层 bash 读 $STORE/index.json，
# 不 export 的话内层 bash 看到的是空变量，期望值恒为空、该条断言永远失败（实际发生过）。
export STORE B LOG
P=0; F=0
ck(){ if [ "$2" = "$3" ]; then echo "  [PASS] $1 -> $2"; P=$((P+1)); else echo "  [FAIL] $1 (实际=$2 期望=$3)"; F=$((F+1)); fi; }

# 第一个参数是超时，其余参数原样转交给 curl。
# 曾写成 `api(){ curl -s -m "${1:-30}" ...; }` —— 没有转发 "$@"，
# 于是 `api 30 -o t_real.jpg URL` 里的 -o 和 URL 全被丢掉，
# curl 报 "(2) no URL specified" 并不生成文件。
# 后果是下游十几条断言全部级联失败（拿不到夹具图 -> 上传 0 张 -> 断言 id 为空 ->
# 取图 405/000），看起来像产品崩了，其实只有这一行是错的。
# 这类"验证手段出错而代码是对的"已经踩到第 9 次，教训同前：不许只看 FAIL 就下结论，
# 必须先确认夹具与判据本身是对的。
api(){ local _t="${1:-30}"; shift; curl -s -m "$_t" -A "$UA" -H "x-oauth-token: $TOKEN" "$@"; }

TOKEN=$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' "$B/v2/user/login" \
  | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null)
AUTH="x-oauth-token: $TOKEN"
if [ -z "$TOKEN" ]; then echo "[FATAL] 未取得令牌"; exit 1; fi

# 基线 **id 集合**（不是条数）。
# 教训：曾用 `id > 条数` 判断「新增记录」，而基线是 ids=[1,2,3,4,7,8] / 条数=6，
# 于是 id 7 和 8 被当成测试数据删掉，误删两条真实记录。
curl -s -m 25 -A "$UA" -H "x-oauth-token: $TOKEN" "$B/v2/image-search/list?page=1&size=200" \
  -o /tmp/_base_all.json
python3 -c "
import json
d=json.load(open('/tmp/_base_all.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
ids=sorted(i['id'] for i in (d.get('items') or []))
open('/tmp/_baseids.txt','w').write(' '.join(map(str,ids)))
print('基线 id 集合: %s' % ids)
"
BASE_N=$(wc -w < /tmp/_baseids.txt)
echo "基线条数: $BASE_N"

echo "################ 1. 输入健壮性 ################"
cd /tmp
printf 'not an image at all' > t_text.jpg
: > t_empty.jpg
head -c 200 /dev/urandom > t_garbage.jpg
# 自造夹具：**不依赖底库里已有记录**。
# 此前这里取 id=8 的原图当夹具，而空库（新建部署 / 主机重建后）里没有 id=8，
# 取图得到 0 字节文件，导致后续所有上传与检索全部失败（实测：PASS=28 FAIL=10，
# 报错集中在「入库返回 S0503C0000」「8 路并发全成功=0」）。验收用例不该依赖既有数据。
python3 -c "
import struct, zlib
def chunk(t, d):
    c = t + d
    return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
w = h = 128
rows = b''
for y in range(h):
    rows += b'\x00' + bytes(v for x in range(w) for v in ((x * 2) % 256, (y * 2) % 256, (x + y) % 256))
open('/tmp/t_real.jpg', 'wb').write(
    b'\x89PNG\r\n\x1a\n'
    + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    + chunk(b'IDAT', zlib.compress(rows))
    + chunk(b'IEND', b''))
"
echo "  自造夹具 t_real.jpg: $(stat -c %s t_real.jpg) bytes"
head -c 2000 t_real.jpg > t_trunc.jpg
python3 -c "
import struct,zlib
def chunk(t,d):
    c=t+d
    return struct.pack('>I',len(d))+c+struct.pack('>I',zlib.crc32(c)&0xffffffff)
raw=b''.join(b'\x00'+bytes([255,0,0]*8) for _ in range(8))
open('t_tiny.png','wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',8,8,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
"
cp t_real.jpg t_big.jpg; for i in 1 2 3 4 5 6 7 8; do cat t_real.jpg >> t_big.jpg; done

for F2 in t_text.jpg t_empty.jpg t_garbage.jpg t_trunc.jpg; do
  R=$(curl -s -m 60 -A "$UA" -H "$AUTH" -F "file=@$F2" -o /tmp/_r.json -w '%{http_code}' "$B/v2/image-search/index")
  if [ "$R" -ge 500 ] 2>/dev/null; then ck "畸形入库 $F2 不 5xx" "$R" "<500"; else ck "畸形入库 $F2 不 5xx" "ok" "ok"; fi
done
for F2 in t_text.jpg t_garbage.jpg t_trunc.jpg; do
  R=$(curl -s -m 60 -A "$UA" -H "$AUTH" -F "file=@$F2" -o /dev/null -w '%{http_code}' "$B/v2/image-search/search")
  if [ "$R" -ge 500 ] 2>/dev/null; then ck "畸形检索 $F2 不 5xx" "$R" "<500"; else ck "畸形检索 $F2 不 5xx" "ok" "ok"; fi
done
ck "合法小图可入库" "$(curl -s -m 60 -A "$UA" -H "$AUTH" -F 'file=@t_tiny.png' "$B/v2/image-search/index" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code'))" 2>/dev/null)" "00000"
ck "超大图可入库" "$(curl -s -m 120 -A "$UA" -H "$AUTH" -F 'file=@t_big.jpg' "$B/v2/image-search/index" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code'))" 2>/dev/null)" "00000"
for KV in "topK=0" "topK=-5" "topK=99999"; do
  R=$(curl -s -m 60 -A "$UA" -H "$AUTH" -F "file=@t_real.jpg" -F "$KV" -o /dev/null -w '%{http_code}' "$B/v2/image-search/search")
  if [ "$R" -ge 500 ] 2>/dev/null; then ck "越界 $KV 不 5xx" "$R" "<500"; else ck "越界 $KV 不 5xx" "ok" "ok"; fi
done
ck "topK=abc 参数非法 400" "$(curl -s -m 60 -A "$UA" -H "$AUTH" -F 'file=@t_real.jpg' -F 'topK=abc' -o /dev/null -w '%{http_code}' "$B/v2/image-search/search")" "400"

# 清理本组造的数据
curl -s -m 30 -A "$UA" -H "$AUTH" "$B/v2/image-search/list?page=1&size=100" -o /tmp/_c0.json
for ID in $(python3 -c "
import json
d=json.load(open('/tmp/_c0.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
base=set(open('/tmp/_baseids.txt').read().split())
print(' '.join(str(i['id']) for i in (d.get('items') or []) if str(i['id']) not in base))
"); do curl -s -m 30 -A "$UA" -H "$AUTH" -X DELETE -o /dev/null "$B/v2/image-search/$ID"; done

echo
echo "################ 2. 并发与幂等 ################"
curl -s -m 25 -A "$UA" -H "$AUTH" "$B/v2/image-search/list?page=1&size=1" -o /tmp/_b.json
BASE=$(python3 -c "
import json
d=json.load(open('/tmp/_b.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
print(d.get('total'))
")
echo "  并发前 total=$BASE"
for i in $(seq 1 8); do cp t_real.jpg "cc$i.jpg"; done
for i in $(seq 1 8); do
  curl -s -m 120 -A "$UA" -H "$AUTH" -F "file=@cc$i.jpg" -o "rb$i.json" -w "%{http_code}\n" "$B/v2/image-search/index" > "hb$i.txt" 2>/dev/null &
done
wait
OK=0
for i in $(seq 1 8); do
  C=$(python3 -c "import json;print(json.load(open('rb$i.json',encoding='utf-8')).get('code'))" 2>/dev/null || echo ERR)
  [ "$C" = "00000" ] && OK=$((OK+1))
done
ck "8 路并发入库全成功" "$OK" "8"
curl -s -m 25 -A "$UA" -H "$AUTH" "$B/v2/image-search/list?page=1&size=100" -o /tmp/_c1.json
ck "id 无重复" "$(python3 -c "
import json,collections
d=json.load(open('/tmp/_c1.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
ids=[i['id'] for i in (d.get('items') or [])]
dup=[k for k,v in collections.Counter(ids).items() if v>1]
print('0' if not dup else len(dup))
")" "0"
ck "增长数与并发数一致" "$(python3 -c "
import json
d=json.load(open('/tmp/_c1.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
print(d.get('total'))
")" "$((BASE+8))"
for i in $(seq 1 8); do
  curl -s -m 120 -A "$UA" -H "$AUTH" -F "file=@t_real.jpg" -o /dev/null -w "%{http_code}\n" "$B/v2/image-search/search" > "sb$i.txt" 2>/dev/null &
done
wait
S=0; for i in $(seq 1 8); do [ "$(cat sb$i.txt)" = "200" ] && S=$((S+1)); done
ck "8 路并发检索全 200" "$S" "8"

echo
echo "################ 3. 数据完整性（删除三处同步）################"
NEWID=$(python3 -c "
import json
d=json.load(open('/tmp/_c1.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
base=set(open('/tmp/_baseids.txt').read().split())
ids=[i['id'] for i in (d.get('items') or []) if str(i['id']) not in base]
print(sorted(ids)[-1] if ids else '')
")
echo "  取最新一条 id=$NEWID"
ck "删除前检索命中该 id" "$(curl -s -m 120 -A "$UA" -H "$AUTH" -F "file=@t_real.jpg" "$B/v2/image-search/search" | python3 -c "
import json,sys
ids=[x.get('id') for x in (json.load(sys.stdin).get('data') or [])]
print('1' if $NEWID in ids else '0')
")" "1"
BEFORE_FILES=$(sudo_ ls -1 $STORE/images 2>/dev/null | wc -l)
ck "删除返回成功" "$(curl -s -m 30 -A "$UA" -H "$AUTH" -X DELETE "$B/v2/image-search/$NEWID" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code'))" 2>/dev/null)" "00000"
AFTER_FILES=$(sudo_ ls -1 $STORE/images 2>/dev/null | wc -l)
ck "原图文件已随之清理" "$([ "$AFTER_FILES" -lt "$BEFORE_FILES" ] && echo 1 || echo 0)" "1"
ck "删除后不再命中（向量已删）" "$(curl -s -m 120 -A "$UA" -H "$AUTH" -F "file=@t_real.jpg" "$B/v2/image-search/search" | python3 -c "
import json,sys
ids=[x.get('id') for x in (json.load(sys.stdin).get('data') or [])]
print('0' if $NEWID in ids else '1')
")" "1"
ck "取图接口 404" "$(curl -s -m 20 -A "$UA" -H "$AUTH" -o /dev/null -w '%{http_code}' "$B/v2/image-search/$NEWID/image")" "404"
ck "重复删除幂等（返回业务错误）" "$(curl -s -m 30 -A "$UA" -H "$AUTH" -X DELETE "$B/v2/image-search/$NEWID" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code'))" 2>/dev/null)" "S0503C0000"
# 清理剩余并发数据
curl -s -m 25 -A "$UA" -H "$AUTH" "$B/v2/image-search/list?page=1&size=100" -o /tmp/_c2.json
for ID in $(python3 -c "
import json
d=json.load(open('/tmp/_c2.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
base=set(open('/tmp/_baseids.txt').read().split())
print(' '.join(str(i['id']) for i in (d.get('items') or []) if str(i['id']) not in base))
"); do curl -s -m 30 -A "$UA" -H "$AUTH" -X DELETE -o /dev/null "$B/v2/image-search/$ID"; done
ck "已回到基线（含 id 集合一致）" "$(curl -s -m 25 -A "$UA" -H "$AUTH" "$B/v2/image-search/list?page=1&size=200" | python3 -c "
import json,sys
d=json.load(sys.stdin).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
now=sorted(str(i['id']) for i in (d.get('items') or []))
was=open('/tmp/_baseids.txt').read().split()
print('1' if now==sorted(was) else '0 实际=%s 期望=%s' % (now, sorted(was)))
")" "1"

echo
echo "################ 4. 重启一致性 ################"
curl -s -m 25 -A "$UA" -H "$AUTH" "$B/v2/image-search/list?page=1&size=100" -o /tmp/_pre.json
python3 -c "
import json
d=json.load(open('/tmp/_pre.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
open('/tmp/_preids.txt','w').write(' '.join(map(str,sorted(i['id'] for i in (d.get('items') or [])))))
"
cp -f t_real.jpg rq.jpg   # 同 L58：不依赖库里已有 id（原为取 id=8 的原图）
PREHIT=$(curl -s -m 120 -A "$UA" -H "$AUTH" -F "file=@rq.jpg" "$B/v2/image-search/search" | python3 -c "import json,sys;print(len(json.load(sys.stdin).get('data') or []))")
# 重启命令：默认重启生产服务；隔离实例验收时用 AI_RESTART 指到私有实例，
# 否则这一组会去重启别人的服务（既打扰对方，也把被测对象换掉）。
if [ -n "${AI_RESTART:-}" ]; then
  echo kali | sudo -S bash -c "$AI_RESTART" 2>/dev/null
else
  sudo_ systemctl restart ai-starter
fi
for i in $(seq 1 90); do
  C=$(curl -s -f -m 3 -o /dev/null -w '%{http_code}' -A "$UA" "$B/v1/captcha" 2>/dev/null)
  [ "$C" = "200" ] && break
  sleep 2
done
TOKEN=$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' "$B/v2/user/login" \
  | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null)
AUTH="x-oauth-token: $TOKEN"
curl -s -m 25 -A "$UA" -H "$AUTH" "$B/v2/image-search/list?page=1&size=100" -o /tmp/_post.json
ck "重启后记录一条不少" "$(python3 -c "
import json
d=json.load(open('/tmp/_post.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
post=sorted(i['id'] for i in (d.get('items') or []))
pre=[int(x) for x in open('/tmp/_preids.txt').read().split()]
print('1' if post==pre else '0')
")" "1"
POSTHIT=$(curl -s -m 120 -A "$UA" -H "$AUTH" -F "file=@rq.jpg" "$B/v2/image-search/search" | python3 -c "import json,sys;print(len(json.load(sys.stdin).get('data') or []))")
ck "重启后检索结果数与重启前一致" "$POSTHIT" "$PREHIT"
sudo_ grep -a '图像管线已就绪' "$LOG" 2>/dev/null | tail -1 | sed 's/^/  /'

echo
echo "################ 5. 鉴权 ################"
for EP in "/v2/image-search/list?page=1&size=1" "/v2/image-search/status" "/v2/settings/image_search" "/v2/usage/parsers" "/v2/ai/models/capability/feature"; do
  ck "匿名 $EP" "$(curl -s -m 20 -A "$UA" -o /dev/null -w '%{http_code}' "$B$EP")" "403"
done
ck "伪造 token" "$(curl -s -m 20 -A "$UA" -H 'x-oauth-token: forged' -o /dev/null -w '%{http_code}' "$B/v2/image-search/status")" "403"
ck "错误口令被拒" "$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}' "$B/v2/user/login" | python3 -c "import json,sys
try:
    o=json.load(sys.stdin); d=o.get('data') or {}
    print('YES' if d.get('accessToken') else 'NO')
except Exception: print('NO')")" "NO"

echo
echo "################ 6. 配置热更新（不重启）################"
curl -s -m 25 -A "$UA" -H "$AUTH" "$B/v2/settings/image_search" -o /tmp/_cfg.json
OLDK=$(python3 -c "
import json
d=(json.load(open('/tmp/_cfg.json',encoding='utf-8')).get('data') or {})
d=d.get('data') if isinstance(d.get('data'),dict) else d
print(d.get('search_top_k'))
")
echo "  改前 search_top_k=$OLDK"
R=$(curl -s -m 30 -A "$UA" -H "$AUTH" -X PUT -H 'Content-Type: application/json' \
  -d '{"search_top_k":"3"}' -o /dev/null -w '%{http_code}' "$B/v2/settings/image_search")
ck "PUT 改配置" "$R" "200"
H3=$(curl -s -m 120 -A "$UA" -H "$AUTH" -F "file=@rq.jpg" "$B/v2/image-search/search" | python3 -c "import json,sys;print(len(json.load(sys.stdin).get('data') or []))")
ck "不重启即生效（命中数 <= 3）" "$([ "$H3" -le 3 ] && echo 1 || echo 0)" "1"
curl -s -m 30 -A "$UA" -H "$AUTH" -X PUT -H 'Content-Type: application/json' \
  -d "{\"search_top_k\":\"$OLDK\"}" -o /dev/null "$B/v2/settings/image_search"
ck "恢复原值" "$(curl -s -m 25 -A "$UA" -H "$AUTH" "$B/v2/settings/image_search" | python3 -c "
import json,sys
d=(json.load(sys.stdin).get('data') or {})
d=d.get('data') if isinstance(d.get('data'),dict) else d
print(d.get('search_top_k'))
")" "$OLDK"

echo
echo "################ 7. 资源与落盘 ################"
ck "images/ 无孤儿（文件数 <= 记录数）" "$(python3 -c "
import json,subprocess
d=json.load(open('/tmp/_post.json',encoding='utf-8')).get('data') or {}
d=d.get('data') if isinstance(d.get('data'),dict) else d
n=len(d.get('items') or [])
files=int(subprocess.run(['bash','-c','echo kali | sudo -S ls -1 $STORE/images 2>/dev/null | wc -l'],capture_output=True,text=True).stdout.strip() or 0)
print('1' if files<=n else '0')
")" "1"
ck "index.json 与记录数一致" "$(python3 -c "
import json
a=json.load(open('/tmp/_post.json',encoding='utf-8')).get('data') or {}
a=a.get('data') if isinstance(a.get('data'),dict) else a
print(a.get('total'))
")" "$(python3 -c "
import json, subprocess
# 解析失败要**明确报出来**，不能让 python 崩掉后期望值变成空串：
# 空串与任何实际值都不相等，断言会失败但看不出是「读不到文件」还是「真的不一致」。
out=subprocess.run(['bash','-c','echo kali | sudo -S cat \$STORE/index.json 2>/dev/null'],capture_output=True,text=True).stdout
if not out.strip():
    print('READ_EMPTY'); raise SystemExit
try:
    o=json.loads(out)
except Exception as e:
    print('PARSE_FAIL:'+type(e).__name__); raise SystemExit
print(len(o if isinstance(o,list) else (o.get('images') or [])))
")"
echo "  /opt 可用: $(df -BG /opt | tail -1 | awk '{print $4}')   备份包: $(sudo_ ls -1 /opt/ai-starter/app.jar.bak-* 2>/dev/null | wc -l) 个"

echo
echo "################ 小结 ################"
echo "  PASS=$P  FAIL=$F"
echo "PRODGRADE_DONE"
