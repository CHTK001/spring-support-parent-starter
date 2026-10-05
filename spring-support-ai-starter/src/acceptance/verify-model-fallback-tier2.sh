#!/bin/bash
# 五条「默认模型」规格的 **兜底档取值** 运行时验收（补 verify-default-model-routing.sh 未验的两处）
#
# 为什么单独一个套件：
#   verify-default-model-routing.sh（PASS=27）验的是「按账号隔离」与「优先级顺序」，
#   对 ③ 人脸、⑤ 知识库只证明了「兜底分支条件成立」，**没有证明兜底档真的被取用**。
#   原因不是产品缺陷，而是当时选的观测点看不到解析结果：
#     - ③ 的 runtime/default_face_model 是**全局**且为空，tier-1 空与 tier-2 空落同一个分支；
#     - ⑤ 的 resolveDelegate() 只挂在 @Permission("admin") 的 RagController.query 上。
#
# 本套件的关键突破：**GlobalSettingReader 是按 userId 取值的**，所以「tier-2 兜底档」本身
#   也可以用**本账号自己的覆盖行**造出来，完全不必改共享的全局配置。观测点也因此变成
#   零成本、请求期、按账号的：
#     ③ GET /v2/face/models      —— 管线建得起来返回 200，建不起来返回 400
#     ⑤ [RagClientManager] 日志  —— 直接回显兜底后解析出的模型名
#
# 判据纪律（与主套件一致，见 README）：
#   1. 每个正向判据都配一个**敏感性对照**：换掉被验的那个变量，结论必须翻转。
#      没有对照的判据不算判据 —— 它可能只是在 echo 我自己写的字符串。
#   2. 判据只删 ops 自己在 5 个模型组内建的行，绝不整表删；tester / admin / 全局一律只读。
#   3. 共享环境里不改任何全局配置行。
#   4. 不硬编码被验产物 md5，运行时自读 md5sum 并自报，由报告的绑定值来对。
#
# 用法（远端 192.168.50.198 上跑，需 root 读 /opt/ai-starter/app-out.log）：
#   bash verify-model-fallback-tier2.sh
# 退出码：0 = 全部 PASS；1 = 有 FAIL 或前置失败。

set +e

B=${BASE:-http://127.0.0.1:8787}
LOG=${APPLOG:-/opt/ai-starter/app-out.log}
DB=${DBNAME:-ai_test}
APPJAR=${APPJAR:-/opt/ai-starter/app.jar}

# 本套件自造的假名。代码路径里不可能偶然出现这两个串，所以它们出现在日志里
# 就是「这个值真的被解析出来并使用了」的证据。
FAKE_FACE_A='zprobe-face-A'
FAKE_FACE_B='zprobe-face-B'
FAKE_RAG_A='zprobe-rag-A'
FAKE_RAG_B='zprobe-rag-B'

PASS=0; FAIL=0
ck() { # ck <名称> <实际> <期望>
  if [ "$2" = "$3" ]; then
    PASS=$((PASS+1)); printf '  [PASS] %s = %s\n' "$1" "$2"
  else
    FAIL=$((FAIL+1)); printf '  [FAIL] %s\n         实际 = %s\n         期望 = %s\n' "$1" "$2" "$3"
  fi
}
ckge() { # ckge <名称> <实际> <下限>：用于「至少出现 N 次」的存在性判据
  if [ -n "$2" ] && [ "$2" -ge "$3" ] 2>/dev/null; then
    PASS=$((PASS+1)); printf '  [PASS] %s = %s（≥%s）\n' "$1" "$2" "$3"
  else
    FAIL=$((FAIL+1)); printf '  [FAIL] %s\n         实际 = %s\n         要求 = ≥%s\n' "$1" "$2" "$3"
  fi
}

Q() { echo kali | sudo -S mysql -N -B "$DB" -e "$1" 2>/dev/null; }
J() { python3 -c "import json,sys;d=json.load(open(sys.argv[1]));
def dig(o,p):
    for k in p.split('.'):
        if not isinstance(o,dict): return None
        o=o.get(k)
    return o
v=dig(d.get('data'),sys.argv[2]);
print('' if v is None else v)" "$1" "$2" 2>/dev/null; }

tok() {
  curl -s -m 20 -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" "$B/v2/user/login" \
    -o /tmp/t2_login.json -w '%{http_code}' >/dev/null
  python3 -c "import json;print((json.load(open('/tmp/t2_login.json')).get('data') or {}).get('accessToken') or '')" 2>/dev/null
}
put() { # put <tok> <group> <json> -> http code
  curl -s -m 40 -o /tmp/t2_put.json -w '%{http_code}' -X PUT \
    -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "$3" "$B/v2/settings/$2"
}
get() { # get <tok> <path> <outfile> -> http code
  curl -s -m 60 -o "$3" -w '%{http_code}' -H "Authorization: Bearer $1" "$B$2"
}
WL() { wc -l < "$LOG"; }
# 某一水位线之后、匹配某个串的行数。grep -c 匹配 0 条时打印 0 但退出码 1，
# 管道到 head 后退出码变成 0，所以不会触发 set -e 分支，也不会多打一个 0。
cnt() { tail -n "+$1" "$LOG" | grep -a -c -- "$2" | head -1; }
# 有界等待某条日志出现。
# 必须有这个：RAG 客户端重建是**异步**的（日志线程与写入请求线程不同），
# 写完立刻取水位线会把那条日志挡在窗口外 —— 这是实测踩到过的坑，不是假想。
# 取水位线要在**触发动作之前**，等待要有上界，不能靠 sleep 赌时序。
waitlog() { # waitlog <水位线> <正则> <超时秒> -> 命中次数（超时返回当时值）
  local w=$1 pat=$2 t=$3 n=0 i=0 lim=$(( t * 2 ))
  while [ "$i" -lt "$lim" ]; do
    n=$(cnt "$w" "$pat")
    if [ -n "$n" ] && [ "$n" -ge 1 ] 2>/dev/null; then printf '%s' "$n"; return 0; fi
    sleep 0.5
    i=$((i + 1))
  done
  cnt "$w" "$pat"
}
# ops 在这 5 个模型组内的行数（本套件的作用域；清干净应为 0）
rows_ops() {
  Q "SELECT COUNT(*) FROM sys_ai_user_setting
     WHERE sys_ai_user_setting_user_id='ops'
       AND sys_ai_user_setting_group IN ('runtime','bot','image_search','face','rag');"
}

echo "############ 0. 前置 ############"
JAR_MD5=$(md5sum "$APPJAR" 2>/dev/null | cut -c1-32)
ACTIVE=$(echo kali | sudo -S systemctl is-active ai-starter 2>/dev/null)
echo "  被测产物 md5=$JAR_MD5  is-active=$ACTIVE"
ck "服务处于 active" "$ACTIVE" "active"

OT=$(tok ops 'Ops@Guada2026'); AT=$(tok admin admin123)
if [ -z "$OT" ] || [ -z "$AT" ]; then
  echo "  [FATAL] 未取得令牌（ops=${#OT}B admin=${#AT}B），无法继续，拒绝在无身份下断言"
  echo "RESULT=T2_FATAL"; exit 1
fi
echo "  令牌已取得 ops=${#OT}B admin=${#AT}B"

# 自建干净基线：只删 ops 自己在 5 个模型组内的行。tester / admin / 全局一行不碰。
TESTER_BEFORE=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='tester';")
GLOBAL_FACE_BEFORE=$(Q "SELECT sys_ai_global_setting_value FROM sys_ai_global_setting
                        WHERE sys_ai_global_setting_group='runtime'
                          AND sys_ai_global_setting_field='default_face_model';")
GLOBAL_MODEL_BEFORE=$(Q "SELECT sys_ai_global_setting_value FROM sys_ai_global_setting
                        WHERE sys_ai_global_setting_group='runtime'
                          AND sys_ai_global_setting_field='default_model';")
ADMIN_RAG_BEFORE=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting
                      WHERE sys_ai_user_setting_user_id='admin'
                        AND sys_ai_user_setting_group='rag';")
# admin 的 rag_llm_model 观测基线：必须在任何写入动作**之前**取。
# 注意不能拿它去比全局值：admin 自己在 rag 组有覆盖行（实测 2 行，
# rag_llm_model=agnes:agnes-3.0-flash），所以 /v2/rag/status 的 config.llm_model
# 返回的是 admin 自己的覆盖值而非全局的（agnes:agnes-2.0-flash）。
# 真正的不变量是「admin 的取值与套件运行前一致」，不是「等于全局值」。
get "$AT" /v2/rag/status /tmp/t2_rag_status_admin_base.json >/dev/null
ADMIN_RAG_LLM_BEFORE=$(J /tmp/t2_rag_status_admin_base.json config.llm_model)
echo "  tester 行数(基线，只读)=$TESTER_BEFORE  admin rag 行数(基线，只读)=$ADMIN_RAG_BEFORE"
echo "  admin /v2/rag/status config.llm_model(基线，只读)=[$ADMIN_RAG_LLM_BEFORE]"
echo "  （该值不等于全局 rag_llm_model 是正常的：admin 有自己的 rag 覆盖行）"
echo "  全局 runtime/default_face_model=[$GLOBAL_FACE_BEFORE]（本套件断言它全程不变）"
echo "  全局 runtime/default_model=[$GLOBAL_MODEL_BEFORE]（本套件断言它全程不变）"

Q "DELETE FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops'
   AND sys_ai_user_setting_group IN ('runtime','bot','image_search','face','rag');"
ck "干净基线：ops 在 5 个模型组的行数" "$(rows_ops)" "0"
ck "干净基线：tester 行数未被动过" \
   "$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='tester';")" "$TESTER_BEFORE"

echo
echo "############ 1. 规格③ 人脸识别：tier-2 兜底档在请求期被取用 ############"
echo "  观测点：GET /v2/face/models —— 管线建得起来 200，建不起来 400"
echo "  tier-1 = face/detector_model（空值覆盖行，**不回落全局**）"
echo "  tier-2 = runtime/default_face_model 的 **ops 自己**的覆盖行"

echo "-- 写入 ops 自己的 tier-2 假名（ops PUT runtime.default_face_model）"
# 水位线必须取在**触发动作之前**：人脸库的重建日志是设置写入触发的，
# 若先 PUT 再取水位线，那条日志已落在窗口之外，cnt 恒为 0（实测踩过）。
W=$(WL)
H=$(put "$OT" runtime "{\"default_face_model\":\"$FAKE_FACE_A\"}")
ck "ops PUT runtime.default_face_model HTTP" "$H" "200"
echo "-- 把 tier-1 置空（空值覆盖行，读时不回落全局）"
H=$(put "$OT" face '{"detector_model":""}')
ck "ops PUT face.detector_model=\"\" HTTP" "$H" "200"
H=$(put "$OT" face '{"feature_model":""}')
ck "ops PUT face.feature_model=\"\" HTTP" "$H" "200"

H=$(get "$OT" /v2/face/models /tmp/t2_models_face_a.json)
ck "★③ tier-2 非空时 /v2/face/models 拿到 200（管线按 tier-2 建起来了）" "$H" "200"
# 「0 次」类判据必须先确认这次重建真的发生了，否则日志还没落地就数会假通过。
waitlog "$W" "重建账号 'ops' 的人脸库" 30 >/dev/null
ckge "③ 该次重建确实按账号触发" "$(cnt "$W" "重建账号 'ops' 的人脸库")" 1
ck "③ 该次重建未落入「检测模型未配置」分支" "$(cnt "$W" '人脸检测模型未配置')" "0"

echo "-- ★敏感性对照 1：只把 tier-2 换成**空值行**，其余不动，结论必须翻转"
W=$(WL)
H=$(put "$OT" runtime '{"default_face_model":""}')
ck "对照 ops PUT runtime.default_face_model=\"\" HTTP" "$H" "200"
H=$(put "$OT" face '{"detector_model":""}')
ck "对照 ops 再 PUT face.detector_model=\"\" 触发重建 HTTP" "$H" "200"
H=$(get "$OT" /v2/face/models /tmp/t2_models_face_blank.json)
ck "★对照 tier-2 为空时 /v2/face/models 变成 400" "$H" "400"
ckge "对照 该次重建落入「检测模型未配置」分支" "$(waitlog "$W" '人脸检测模型未配置' 30)" 1
H=$(get "$AT" /v2/face/models /tmp/t2_models_admin.json)
ck "★对照 同一时刻 admin 仍拿到 200（400 是账号局部的，不是全局故障）" "$H" "200"

echo "-- ★敏感性对照 2：tier-2 换成**另一个**假名，必须重新变回 200"
W=$(WL)
H=$(put "$OT" runtime "{\"default_face_model\":\"$FAKE_FACE_B\"}")
ck "对照 ops PUT runtime.default_face_model=$FAKE_FACE_B HTTP" "$H" "200"
H=$(put "$OT" face '{"detector_model":""}')
ck "对照 ops 再 PUT face.detector_model=\"\" 触发重建 HTTP" "$H" "200"
H=$(get "$OT" /v2/face/models /tmp/t2_models_face_b.json)
ck "★对照 tier-2 换成 $FAKE_FACE_B 后又变回 200" "$H" "200"
waitlog "$W" "重建账号 'ops' 的人脸库" 30 >/dev/null
ck "对照 该次重建未落入「检测模型未配置」分支" "$(cnt "$W" '人脸检测模型未配置')" "0"
H=$(get "$AT" /v2/face/models /tmp/t2_models_admin2.json)
ck "★对照 同一时刻 admin 仍拿到 200（ops 的 tier-2 不污染 admin）" "$H" "200"

echo
echo "############ 2. 规格⑤ 知识库 rag：tier-2 兜底取值被直接观测 ############"
echo "  观测点：[RagClientManager] 日志 —— 直接回显兜底后解析出的模型名"
echo "  tier-1 = rag/rag_llm_model（空值覆盖行，**不回落全局**）"
echo "  tier-2 = runtime/default_model 的 **ops 自己**的覆盖行"

# 水位线同样必须取在**触发动作之前**：RAG 客户端的重建是写 rag 覆盖行时触发的，
# 那条「回落默认模型」日志就在本次 PUT 之内同步落地。实测踩过：先 PUT 再取
# 水位线，日志已在窗口之外，cnt 恒为 0（而紧邻的敏感性对照因为 W 在 PUT 前就 PASS，
# 这种「先 FAIL 后 PASS」的不对称正是水位线位置错位的指纹）。
W=$(WL)
H=$(put "$OT" runtime "{\"default_model\":\"$FAKE_RAG_A\"}")
ck "ops PUT runtime.default_model=$FAKE_RAG_A HTTP" "$H" "200"
H=$(put "$OT" rag '{"rag_llm_model":""}')
ck "ops PUT rag.rag_llm_model=\"\" HTTP" "$H" "200"

H=$(get "$OT" /v2/rag/status /tmp/t2_rag_status.json)
ck "ops GET /v2/rag/status HTTP" "$H" "200"
ck "★⑤ 兜底分支条件成立：tier-1 空值 → needLlmConfig" "$(J /tmp/t2_rag_status.json needLlmConfig)" "True"
# RAG 客户端重建是**异步**的（日志线程 cat-handler-N 与发起请求的线程不同），
# 所以 get 返回后立刻 cnt 会在日志落地前读到 0 而误判 FAIL。
# 必须用 waitlog 有界等待，且水位线 W 取在触发动作之前。
ckge "★⑤ 日志回显的兜底取值就是 ops 自己的假名" "$(waitlog "$W" "$FAKE_RAG_A" 30)" 1
ckge "⑤ 兜底日志来自 RagClientManager（不是别处 echo 的）" "$(waitlog "$W" 'RagClientManager.*回落' 5)" 1
# 原文案过期：源码现在输出 `RAG 客户端构建完成: account=ops, ...`（无引号、无「已为账号 …」），
# 旧 pattern `已为账号 .ops. 构建独立` 是照着带引号的旧文案写的，实测全文件命中 0。
# 换成与现文案一致、且能把**账号**钉在同一条日志上的写法。
ckge "⑤ ops 的 RAG 客户端按该兜底值建成" "$(waitlog "$W" 'RAG 客户端构建完成: account=ops' 5)" 1

echo "-- ★敏感性对照：只把 tier-2 换成**另一个**假名，回显的名字必须跟着换"
W=$(WL)
H=$(put "$OT" runtime "{\"default_model\":\"$FAKE_RAG_B\"}")
ck "对照 ops PUT runtime.default_model=$FAKE_RAG_B HTTP" "$H" "200"
H=$(put "$OT" rag '{"rag_llm_model":""}')
ck "对照 ops 再 PUT rag.rag_llm_model=\"\" HTTP" "$H" "200"
H=$(get "$OT" /v2/rag/status /tmp/t2_rag_status2.json)
ckge "★对照 兜底取值换成 $FAKE_RAG_B" "$(waitlog "$W" "$FAKE_RAG_B" 30)" 1
# 旧假名必须一条都不再出现。先 waitlog 等新假名落地（证明这次重建已经发生），
# 再数旧假名，否则「重建还没发生」会被误当成「旧假名已消失」而假通过。
ck "★对照 旧假名 $FAKE_RAG_A 不再出现（证明回显跟的是当前 tier-2，不是残留）" \
   "$(waitlog "$W" "$FAKE_RAG_B" 30 >/dev/null; cnt "$W" "$FAKE_RAG_A")" "0"

echo "-- 隔离不变量：admin 的 rag 档未被 ops 的空值行污染"
get "$AT" /v2/rag/status /tmp/t2_rag_status_admin.json >/dev/null
GLOBAL_RAG=$(Q "SELECT sys_ai_global_setting_value FROM sys_ai_global_setting
                 WHERE sys_ai_global_setting_group='rag'
                   AND sys_ai_global_setting_field='rag_llm_model';")
# 与「套件运行前的观测值」比对，而不是与全局表比对：admin 自己有 rag 覆盖行，
# 它读到的是自己的值。判据只声明「ops 的写入没有改变 admin 的解析结果」。
ck "★admin 的 rag_llm_model 与套件运行前一致（ops 的空值行未污染 admin）" \
   "$(J /tmp/t2_rag_status_admin.json config.llm_model)" "$ADMIN_RAG_LLM_BEFORE"
# 附带的敏感性说明：admin 取值本就不同于全局值，故「等于全局」是错的判据。
echo "  参照：admin 当前=[$(J /tmp/t2_rag_status_admin.json config.llm_model)]  全局=[$GLOBAL_RAG]"
echo "        两者不同属正常（admin 有自己的 rag 覆盖行），故此处不断言等于全局值。"
ck "admin 的 rag 组未被本套件写入任何行" \
   "$(Q "SELECT COUNT(*) FROM sys_ai_user_setting
         WHERE sys_ai_user_setting_user_id='admin'
           AND sys_ai_user_setting_group='rag';")" "$ADMIN_RAG_BEFORE"

echo
echo "############ 3. 收尾 ############"
Q "DELETE FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops'
   AND sys_ai_user_setting_group IN ('runtime','bot','image_search','face','rag');"
ck "收尾：ops 在 5 个模型组的行已归零" "$(rows_ops)" "0"
ck "收尾：tester 行数未被本套件改动" \
   "$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='tester';")" "$TESTER_BEFORE"
ck "收尾：全局 runtime/default_face_model 未被本套件改动" \
   "$(Q "SELECT sys_ai_global_setting_value FROM sys_ai_global_setting
         WHERE sys_ai_global_setting_group='runtime'
           AND sys_ai_global_setting_field='default_face_model';")" "$GLOBAL_FACE_BEFORE"
ck "收尾：全局 runtime/default_model 未被本套件改动" \
   "$(Q "SELECT sys_ai_global_setting_value FROM sys_ai_global_setting
         WHERE sys_ai_global_setting_group='runtime'
           AND sys_ai_global_setting_field='default_model';")" "$GLOBAL_MODEL_BEFORE"
echo "--- 作用域之外的覆盖行（别的会话的合法写入，本套件既没删也没改）---"
# 单行书写：Q() 用 mysql -e 传参且 2>/dev/null 吞掉报错，多行 SQL 一旦出错
# 会静默伪装成「零行」，让这份取证看起来像「作用域外没有别人的数据」。
Q "SELECT CONCAT(sys_ai_user_setting_user_id,'|',sys_ai_user_setting_group,'|',sys_ai_user_setting_field,'|',sys_ai_user_setting_value) FROM sys_ai_user_setting ORDER BY sys_ai_user_setting_user_id,sys_ai_user_setting_group,sys_ai_user_setting_field;"

echo
echo "############ 汇总 ############"
echo "  被验产物 md5 = $JAR_MD5"
echo "  PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=T2_FALLBACK_OK"
  exit 0
fi
echo "RESULT=T2_FALLBACK_FAIL"
exit 1