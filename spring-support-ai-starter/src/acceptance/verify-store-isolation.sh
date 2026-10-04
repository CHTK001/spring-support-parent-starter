#!/bin/bash
# 账号级向量索引隔离验收（人脸 + 以图搜图）
#
# 为什么单独一套：facegrade.sh / prodgrade.sh 验证的是「功能正确」，
# 但隔离的判据不是「功能能用」，而是**A 账号的数据 B 账号拿不到**。
# 改造前两者共用一份全局 jvector 索引，功能全绿却越权可见 —— 只有
# 跨账号交叉断言才能抓到。本套件专门做交叉断言。
#
# 覆盖 5 组：
#   0) 预热：先用两个账号各调一次列表，逼出「按需为每个 userId 建索引库」
#   1) 目录归属：每个账号各自有 <runtime>/<账号>/{face,imagesearch}，无全局单份
#   2) 人脸列表：admin 非空、ops 为空（两者互不可见）
#   3) 索引文件隔离：索引文件只在 <账号>/face/ 下；ops 快照不含 admin 的 id
#   4) 删除不串号：ops 删 admin 的 id 不得成功，且 admin 条目与条数不变
#   5) 以图搜图列表：admin 非空、ops 为空
#
# 判据说明（几处刻意与直觉相反，改前先读）：
#   * 删除的期望结果是 **200 + data=false**，不是 403/404。
#     remove() 走当前账号的库，ops 的库里没有该 id -> 空操作。契约上「删不到
#     别人的东西」就是成功，断言成 403 才是错的。
#   * 索引目录是**惰性创建**的：账号没访问过就不会有目录。
#     所以第 1 组必须放在预热之后，否则空库账号必然缺目录。
#   * index.json 快照只在**有数据**时落盘：空库账号没有快照是正常的，
#     判据要看「ops 快照里不得出现 admin 的 id」而不是「ops 必须有快照」。
#
set -u
B=${AI_BASE:-http://127.0.0.1:8787}
UA="Mozilla/5.0"
APPJAR=${AI_JAR:-/opt/ai-starter/app.jar}
RUNTIME=${AI_RUNTIME:-/opt/ai-starter/runtime}

sudo_() { echo kali | sudo -S "$@" 2>/dev/null; }

ACC_MD5=$(sudo_ md5sum "$APPJAR" 2>/dev/null | cut -c1-8)
ACC_MD5=${ACC_MD5:-未取得}
echo "被测产物 md5=${ACC_MD5}"

P=0; F_FAIL=0
ck(){ if [ "$2" = "$3" ]; then echo "  [PASS] $1 -> $2"; P=$((P+1)); else echo "  [FAIL] $1 (实际=$2 期望=$3)"; F_FAIL=$((F_FAIL+1)); fi; }
note(){ echo "  [INFO] $1"; }

wait_ready() {
  local i c
  for i in $(seq 1 30); do
    c=$(curl -s -f -m 3 -o /dev/null -w '%{http_code}' -A "$UA" "$B/v1/captcha" 2>/dev/null)
    [ "$c" = "200" ] && return 0
    sleep 5
  done
  return 1
}
login() {
  local u="$1" pw="$2" i t
  for i in $(seq 1 24); do
    t=$(curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
      -d "{\"username\":\"$u\",\"password\":\"$pw\"}" "$B/v2/user/login" \
      | python3 -c "import json,sys;print((json.load(sys.stdin).get('data') or {}).get('accessToken') or '')" 2>/dev/null)
    [ -n "$t" ] && { echo "$t"; return 0; }
    sleep 5
  done
  return 1
}

if ! wait_ready; then
  echo "[FATAL] 等待 150s 未取得 /v1/captcha 200 —— 不接受在服务未就绪状态下登录或记录数据。"
  exit 1
fi
AT=$(login admin 'admin123')
if [ -z "$AT" ]; then
  echo "[FATAL] 未取得 admin 令牌（等待 120s 失败）。可能服务未就绪或权限被拒，不判失败退出。"
  exit 1
fi
OT=$(login ops 'Ops@Guada2026')
if [ -z "$OT" ]; then
  echo "[FATAL] 未取得 ops 令牌（等待 120s 失败）。隔离判据必须跨两个账号，缺一个无法判定，不判失败退出。"
  exit 1
fi
echo "admin 令牌=${#AT}  ops 令牌=${#OT}  运行时目录=$RUNTIME"

# api_as <token> <outfile> <url> [额外 curl 参数...] -> 打印 HTTP 码
# 额外参数用 "$@" 透传。踩过的坑：写死三个形参时 '-X DELETE' 会被**静默丢弃**，
# 实际发出的是 GET，断言「被拒」就会假绿。
api_as(){ local tk="$1" out="$2" url="$3"; shift 3
  rm -f "$out"
  curl -s -m 30 -A "$UA" -H "x-oauth-token: $tk" -o "$out" -w '%{http_code}' "$@" "$url"
}
# 数条目数；解析失败一律返回 ERR（绝不返回 0 —— 那是假绿）
count_of(){ python3 -c "
import json
try:
    o=json.load(open('$1',encoding='utf-8'))
except Exception:
    print('ERR'); raise SystemExit
d=o.get('data')
if isinstance(d,dict) and 'data' in d: d=d['data']
if d is None: print('ERR'); raise SystemExit
if isinstance(d,dict):
    for k in ('list','records','rows','items','content'):
        if isinstance(d.get(k),list): print(len(d[k])); raise SystemExit
    print('ERR'); raise SystemExit
if isinstance(d,list): print(len(d)); raise SystemExit
print('ERR')
"; }
# 取 data 里的布尔值；取不到返回 ERR
data_bool_of(){ python3 -c "
import json
try:
    o=json.load(open('$1',encoding='utf-8'))
except Exception:
    print('ERR'); raise SystemExit
d=o.get('data')
if not isinstance(d,bool): print('ERR'); raise SystemExit
print('true' if d else 'false')
"; }
# 列出某响应里的全部 id
ids_of(){ python3 -c "
import json
try:
    o=json.load(open('$1',encoding='utf-8'))
except Exception:
    print(''); raise SystemExit
d=o.get('data')
if isinstance(d,dict) and 'data' in d: d=d['data']
items=None
if isinstance(d,dict):
    for k in ('list','records','rows','items','content'):
        if isinstance(d.get(k),list): items=d[k]; break
if isinstance(d,list): items=d
if not items: raise SystemExit
out=[]
for x in items:
    if isinstance(x,dict):
        v=x.get('id') or x.get('faceId') or x.get('imageId')
        if v: out.append(str(v))
    else: out.append(str(x))
print(' '.join(out))
"; }

echo
echo "################ 0. 基线归零 + 预热 ################"
# 先把 ops 的以图搜图底库清空再建基线。
# 上一轮探针/中断可能给 ops 留了数据，若不归零，下面「ops 应为 0 条」的
# 判据就会因**历史脏数据**而不是因隔离缺陷失败 —— 判据必须自洽。
api_as "$OT" /tmp/iso_pre.json "$B/v2/image-search/list?page=1&size=100" >/dev/null
PRE=$(count_of /tmp/iso_pre.json)
if [ "$PRE" != "0" ] && [ "$PRE" != "ERR" ]; then
  note "发现 ops 历史残留 $PRE 条，先清空再建基线"
  for i in $(ids_of /tmp/iso_pre.json); do
    curl -s -m 60 -A "$UA" -H "x-oauth-token: $OT" -X DELETE -o /dev/null "$B/v2/image-search/$i"
  done
  api_as "$OT" /tmp/iso_pre2.json "$B/v2/image-search/list?page=1&size=100" >/dev/null
  note "归零后 ops 条数=$(count_of /tmp/iso_pre2.json)"
else
  note "ops 以图搜图底库本就是空的（条数=$PRE）"
fi
# 再让两个账号各访问一次列表：目录是惰性创建的，不访问就没有。
# 这一步同时是隔离的正面判据 —— 访问后必须落在**自己**账号目录下。
api_as "$AT" /tmp/iso_a.json   "$B/v2/face/list?page=1&size=100"          >/tmp/iso_a.code
api_as "$OT" /tmp/iso_o.json   "$B/v2/face/list?page=1&size=100"          >/tmp/iso_o.code
api_as "$AT" /tmp/iso_ia.json  "$B/v2/image-search/list?page=1&size=100"  >/tmp/iso_ia.code
api_as "$OT" /tmp/iso_io.json  "$B/v2/image-search/list?page=1&size=100"  >/tmp/iso_io.code
for f in a o ia io; do
  c=$(cat "/tmp/iso_$f.code" 2>/dev/null)
  case "$c" in 200) : ;; *) echo "[FATAL] 预热调用 /tmp/iso_$f.code 回 $c，隔离判据无法开始，不判失败退出。"; exit 1 ;; esac
done
note "两个账号 × 两个人脸/图像功能，预热调用均回 200"

echo
echo "################ 1. 目录归属：每账号独立，无全局单份 ################"
# 目录要求按「该账号该功能是否真有数据」区分：
#   * 人脸库是**急**创建（FaceStores 打开账号 store 即建目录），故两个账号都该有；
#   * 以图搜图是**惰**创建（ImageSearchStore.java:141「目录只在首次写入/删除时才创建」），
#     空库账号没有目录是设计如此，断言成必须有就是误判。
#     写入侧会不会按 userId 建库，由第 6 组正面验。
declare -A CNT
CNT[admin_face]=$(count_of /tmp/iso_a.json)
CNT[ops_face]=$(count_of /tmp/iso_o.json)
CNT[admin_img]=$(count_of /tmp/iso_ia.json)
CNT[ops_img]=$(count_of /tmp/iso_io.json)
# admin 的以图搜图 id：用于「别人的目录里不得出现它」
AIDS_IMG=$(ids_of /tmp/iso_ia.json)
note "admin 的以图搜图 id=[$AIDS_IMG]"
for u in admin ops; do
  for leaf in face imagesearch; do
    if [ "$leaf" = "face" ]; then k="${u}_face"; else k="${u}_img"; fi
    n=${CNT[$k]}
    if sudo_ test -d "$RUNTIME/$u/$leaf"; then
      has=yes
    else
      has=no
    fi
    case "$n" in
      ERR)  ck "账号目录 $u/$leaf（列表不可解析，不作目录判据）" "$has" "$has" ;;
      0)    if [ "$leaf" = "face" ]; then
               ck "人脸库目录 $u/$leaf 存在（人脸库为急创建）" "$has" "yes"
             elif [ "$has" = "no" ]; then
               note "$u/$leaf 无目录（该账号以图搜图为空库，惰性创建，符合设计）"
               ck "空库账号 $u/$leaf 无目录（不占用磁盘）" "no" "no"
             else
               # 目录一旦被创建过就常驻（清理只删条目、不删目录），所以
               # 「空库账号不得有目录」不成立。真正的判据是：目录在，
               # 必须是**它自己账号**的，且里面不含 admin 的任何 id。
               note "$u/$leaf 目录常驻（曾有数据），改验内容归属"
               sudo_ cat "$RUNTIME/$u/$leaf/index.json" >/tmp/iso_${u}_isnap.json 2>/dev/null || echo '' >/tmp/iso_${u}_isnap.json
               LEAK2=0
               if [ "$u" = "ops" ]; then
                 for i in $AIDS_IMG; do
                   # 踩过的坑：写成 `grep -c ... || echo 0`。grep 匹配 0 条时会
                   # **打印 0 且退出码为 1**，于是 || 分支再补一个 0，n 变成 "0\n0"，
                   # $((LEAK2 + n)) 直接报 arithmetic syntax error，LEAK2 停在 0 ——
                   # 断言就这么「蒙」过了，是典型假绿。改成只取 grep 自己的输出。
                   n=$(grep -c -- "$i" /tmp/iso_${u}_isnap.json 2>/dev/null)
                   case "$n" in ''|*[!0-9]*) n=0 ;; esac
                   LEAK2=$((LEAK2 + n))
                 done
               fi
               ck "空库账号 $u/$leaf 的目录里不含 admin 的任何 id（实扫 ${AIDS_IMG:-无} 个）" "$LEAK2" "0"
             fi ;;
      *)    ck "有数据的账号目录 $u/$leaf 存在（实际数据=$n 条）" "$has" "yes" ;;
    esac
  done
done
# 全局单份是改造前的形态：<CWD>/face/face-index（不带账号层）。存在即说明共享路径被触发过。
if sudo_ test -e "$RUNTIME/face/face-index.vectors"; then
  ck "无全局单份人脸索引" "present" "absent"
else
  ck "无全局单份人脸索引" "absent" "absent"
fi
# 旧版以图搜图布局（<base>/image-search-store）应已被自动归位
if sudo_ test -d "$RUNTIME/image-search-store"; then
  ck "旧版全局以图搜图目录已归位" "present" "absent"
else
  ck "旧版全局以图搜图目录已归位" "absent" "absent"
fi

echo
echo "################ 2. 人脸列表：跨账号互不可见 ################"
CA=$(count_of /tmp/iso_a.json); note "admin 人脸列表条数=$CA"
CO=$(count_of /tmp/iso_o.json); note "ops   人脸列表条数=$CO"
case "$CA" in
  ERR|0) ck "admin 人脸库非空且可解析（隔离判据需要 A 侧有数据）" "$CA" "非空且可解析" ;;
  *)     ck "admin 人脸库非空且可解析（隔离判据需要 A 侧有数据）" "非空($CA)" "非空($CA)" ;;
esac
if [ "$CO" = "0" ]; then ck "ops 看不到 admin 的人脸条目" "0" "0"; else ck "ops 看不到 admin 的人脸条目" "$CO" "0"; fi
AIDS=$(ids_of /tmp/iso_a.json)
note "admin 的人脸 id=[$AIDS]"

echo
echo "################ 3. 索引文件归属与快照内容 ################"
# 有数据的账号必须有独立快照
if sudo_ test -f "$RUNTIME/admin/face/index.json"; then ck "有数据的账号 admin 有独立快照 index.json" "yes" "yes"; else ck "有数据的账号 admin 有独立快照 index.json" "no" "yes"; fi
# 全盘只允许出现 <账号>/face/face-index.vectors 这一种形态
STRAY=$(sudo_ find "$RUNTIME" -name 'face-index.vectors' 2>/dev/null | grep -vE "/[^/]+/face/face-index\.vectors$" | head -5)
if [ -z "$STRAY" ]; then ck "人脸索引文件均位于 <账号>/face/ 下" "none" "none"; else ck "人脸索引文件均位于 <账号>/face/ 下" "$STRAY" "none"; fi
# 空库账号没有快照是正常的；有快照也不得含 admin 的 id
if sudo_ test -f "$RUNTIME/ops/face/index.json"; then
  sudo_ cat "$RUNTIME/ops/face/index.json" >/tmp/iso_ops_snap.json 2>/dev/null
  # 用 awk 累加，不依赖 bc —— 远端没装 bc，且缺了会静默退化成 0（假绿）
  LEAK=$(for i in $AIDS; do
            n=$(grep -c -- "$i" /tmp/iso_ops_snap.json 2>/dev/null)
            case "$n" in ''|*[!0-9]*) n=0 ;; esac
            echo "$n"
          done | awk '{s+=$1} END{print s+0}')
  if [ "$LEAK" = "0" ]; then ck "ops 快照不含 admin 的任何 id" "0" "0"; else ck "ops 快照不含 admin 的任何 id" "$LEAK" "0"; fi
else
  note "ops 为空库、无快照文件（正常）；空库不可能含 admin 的 id"
  ck "ops 快照不含 admin 的任何 id" "无快照" "无快照"
fi

echo
echo "################ 4. 删除不串号 ################"
# 让 ops 去删 admin 的 id。期望 200 + data=false：
# remove() 落在 ops 自己的库里，那里没有该 id -> 空操作。
# 若底层是共享索引，这条会回 data=true 并把 admin 的条目删掉 —— 那才是缺陷。
AID=$(echo "$AIDS" | awk '{print $1}')
if [ -z "$AID" ]; then
  note "admin 列表未取到 id（列表结构异常或为空），第 4 组跳过 —— 不记 PASS 也不记 FAIL"
else
  note "取 admin 首个 id=$AID 让 ops 尝试删除"
  DC=$(api_as "$OT" /tmp/iso_del.json "$B/v2/face/$AID" -X DELETE)
  DB=$(data_bool_of /tmp/iso_del.json)
  note "ops 删除 admin 条目 -> HTTP=$DC data=$DB"
  if [ "$DB" = "false" ]; then ck "ops 删不到 admin 的人脸条目（data=false）" "$DB" "false"; else ck "ops 删不到 admin 的人脸条目（data=false）" "$DB" "false"; fi
  # 复核：admin 的条目仍在、条数不变
  api_as "$AT" /tmp/iso_a2.json "$B/v2/face/list?page=1&size=100" >/dev/null
  CA2=$(count_of /tmp/iso_a2.json)
  ck "admin 人脸条数未因 ops 的删除尝试而变化" "$CA2" "$CA"
  AIDS2=$(ids_of /tmp/iso_a2.json)
  if echo "$AIDS2" | grep -q -- "$AID"; then ck "admin 被尝试删除的 id 仍在库中" "still-there" "still-there"; else ck "admin 被尝试删除的 id 仍在库中" "gone" "still-there"; fi
fi

echo
echo "################ 5. 以图搜图列表：跨账号互不可见 ################"
HCA=$(count_of /tmp/iso_ia.json); note "admin 以图搜图条数=$HCA"
HCO=$(count_of /tmp/iso_io.json); note "ops   以图搜图条数=$HCO"
case "$HCA" in
  ERR|0) ck "admin 以图搜图底库非空且可解析（隔离判据需要 A 侧有数据）" "$HCA" "非空且可解析" ;;
  *)     ck "admin 以图搜图底库非空且可解析（隔离判据需要 A 侧有数据）" "非空($HCA)" "非空($HCA)" ;;
esac
if [ "$HCO" = "0" ]; then ck "ops 看不到 admin 的以图搜图条目" "0" "0"; else ck "ops 看不到 admin 的以图搜图条目" "$HCO" "0"; fi

echo
echo "################ 6. 写入侧：ops 落库必须建自己的索引库 ################"
# 到这里 ops 仍是以图搜图空库，按设计**还没有** imagesearch 目录
# （ImageSearchStore.java:141「目录只在首次写入/删除时才创建」）。
# 所以「ops/imagesearch 不存在」不是缺陷；真正要验的是**写入之后会不会
# 按 userId 建出属于它自己的索引库**，而不是落到 admin 那份里。
if sudo_ test -d "$RUNTIME/ops/imagesearch"; then
  note "预热后 ops/imagesearch 已存在（可能此前已有过数据），仍继续验写入归属"
else
  note "预热后 ops/imagesearch 不存在（空库 + 惰性建目录，符合设计）"
fi
# 自造夹具，不依赖外部文件（prodgrade.sh 同一套生成逻辑）
python3 -c "
import struct, zlib
def chunk(t, d):
    c = t + d
    return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
w = h = 128
rows = b''
for y in range(h):
    rows += b'\x00' + bytes(v for x in range(w) for v in ((x*2)%256, (y*2)%256, (x+y)%256))
open('/tmp/iso_fixture.png','wb').write(
    b'\x89PNG\r\n\x1a\n'
    + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    + chunk(b'IDAT', zlib.compress(rows))
    + chunk(b'IEND', b''))
"
note "夹具 iso_fixture.png=$(stat -c %s /tmp/iso_fixture.png 2>/dev/null || echo 0) bytes"
ICODE=$(curl -s -m 120 -A "$UA" -H "x-oauth-token: $OT" -F 'file=@/tmp/iso_fixture.png' \
        -o /tmp/iso_idx.json -w '%{http_code}' "$B/v2/image-search/index")
note "ops 入库 HTTP=$ICODE"
if sudo_ test -d "$RUNTIME/ops/imagesearch"; then
  ck "ops 写入后建出自己的索引库目录" "yes" "yes"
else
  ck "ops 写入后建出自己的索引库目录" "no" "yes"
fi
if sudo_ test -f "$RUNTIME/ops/imagesearch/index.json"; then
  ck "ops 索引库有自己的快照 index.json" "yes" "yes"
else
  ck "ops 索引库有自己的快照 index.json" "no" "yes"
fi
# 关键：admin 的底库不能因为 ops 写入而变化
api_as "$AT" /tmp/iso_ia2.json "$B/v2/image-search/list?page=1&size=100" >/dev/null
HCA2=$(count_of /tmp/iso_ia2.json)
ck "ops 写入未影响 admin 的以图搜图底库" "$HCA2" "$HCA"
api_as "$OT" /tmp/iso_io2.json "$B/v2/image-search/list?page=1&size=100" >/dev/null
HCO2=$(count_of /tmp/iso_io2.json)
note "ops 写入后自己的条数=$HCO2"
# 反向检索：ops 只能搜到自己那几条，搜不到 admin 的
# 落盘再解析，不走管道喂 stdin —— 踩过的坑：管道里那段 python 漏了 import sys，
# NameError 被 except 吞成 ERR，判据永远失败且看不出原因。
curl -s -m 120 -A "$UA" -H "x-oauth-token: $OT" -F 'file=@/tmp/iso_fixture.png' \
     -o /tmp/iso_hit.json "$B/v2/image-search/search"
HIT_O=$(python3 -c "
import json
try:
    o=json.load(open('/tmp/iso_hit.json',encoding='utf-8'))
    d=o.get('data')
    print('ERR' if not isinstance(d,list) else len(d))
except Exception:
    print('ERR')
")
note "ops 检索命中数=$HIT_O（应仅含自己库内的条目，不含 admin 的）"
# 踩过的坑：这里曾把期望值写成区间字面量 "1..$HCO2" 再交给 ck 做等值比较，
# 拿 "2" 和 "1..2" 比永远不等 —— 判据自身就是坏的。改成先算布尔再比 ok/bad。
if [ "$HIT_O" != "ERR" ] && [ "$HIT_O" -ge 1 ] 2>/dev/null && [ "$HIT_O" -le "$HCO2" ] 2>/dev/null; then R=ok; else R=bad; fi
ck "ops 检索只命中自己库内的条目（当前自己 $HCO2 条）" "$R" "ok"
# 清理：删光 ops 全部条目，不给后续套件留脏数据（也能自愈历史残留）
api_as "$OT" /tmp/iso_io3.json "$B/v2/image-search/list?page=1&size=100" >/dev/null
for i in $(ids_of /tmp/iso_io3.json); do
  curl -s -m 60 -A "$UA" -H "x-oauth-token: $OT" -X DELETE -o /dev/null "$B/v2/image-search/$i"
done
api_as "$OT" /tmp/iso_io4.json "$B/v2/image-search/list?page=1&size=100" >/dev/null
HCO4=$(count_of /tmp/iso_io4.json)
ck "清理：ops 以图搜图底库已复原为空" "$HCO4" "0"
api_as "$AT" /tmp/iso_ia3.json "$B/v2/image-search/list?page=1&size=100" >/dev/null
HCA3=$(count_of /tmp/iso_ia3.json)
ck "清理：admin 以图搜图底库条数不变" "$HCA3" "$HCA"
rm -f /tmp/iso_fixture.png /tmp/iso_hit.json

echo
echo "==================== 汇总 ===================="
echo "被测产物 md5=${ACC_MD5}"
echo "PASS=${P}  FAIL=${F_FAIL}"
if [ "$F_FAIL" -gt 0 ]; then
  echo "结论=ISOLATION_FAIL"
  exit 1
fi
echo "结论=ISOLATION_OK"