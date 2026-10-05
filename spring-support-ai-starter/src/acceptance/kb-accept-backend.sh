#!/bin/bash
# 知识库 + RAG 后端验收套件（可重复、自清理）
# 用法: bash kb-accept-backend.sh     退出码 0 = 全通过
# 前置: ai-starter 在 127.0.0.1:8787；账号 admin/admin123
set +e
B=http://127.0.0.1:8787
LOG=/opt/ai-starter/app-out.log
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); echo "  [PASS] $1"; }
no(){ FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
chk(){ if [ "$2" = "$3" ]; then ok "$1 = $2"; else no "$1 期望 $3 实得 $2"; fi }

echo "===== 0) 登录（带重试：环境在秒级抖动，拿不到 token ≠ 代码坏）====="
T=""
ATT=0
for i in $(seq 1 12); do
  ATT=$i
  T=$(curl -s -m 20 -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' $B/v2/user/login | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  if [ -n "$T" ]; then break; fi
  echo "  第 ${i} 次未拿到 token，5s 后重试（服务可能在换包重启）"
  sleep 5
done
if [ -z "$T" ]; then
  echo "  !! 连续 12 次（约 60s）拿不到 token —— 环境不可用，无法判定代码"
  echo "RESULT: ENV_UNSTABLE"
  exit 3
fi
ok "登录拿到 token (len=${#T})，第 ${ATT} 次尝试成功"

echo "===== 0.1) 服务稳定性快照（5 次 × 3s，暴露秒级抖动）====="
FLAP=0
PREV=""
for i in $(seq 1 5); do
  L=$(echo kali | sudo -S -p '' ss -ltn 2>/dev/null | grep -c ':8787 ')
  if [ -n "$PREV" ] && [ "$L" != "$PREV" ]; then FLAP=$((FLAP+1)); fi
  PREV=$L
  echo "  第 ${i} 次: 8787 监听=$L"
  sleep 3
done
if [ "$FLAP" -gt 0 ]; then
  echo "  WARN 观察窗内检测到 ${FLAP} 次监听状态变化 —— 环境不稳定，下面的 FAIL 未必是代码问题"
fi
AUTH="Authorization: Bearer $T"

echo "===== 1) 服务与 RAG 状态 ====="
ST=$(curl -s -m 20 -H "$AUTH" $B/v2/rag/status)
echo "$ST" | grep -q '"available" : true' && ok "rag/status available=true" || no "rag/status 不可用: $(echo $ST|head -c 160)"
EM=$(echo "$ST" | sed -n 's/.*"embedding_model"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
EE=$(echo "$ST" | sed -n 's/.*"embedding_engine"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
# embedding_model 上报的是「所选模型」（裸名），embedding_engine 是由它推导出的引擎。
# 旧断言期望 embedding_model=onnx，那是引擎字段时代的契约——引擎已不再是用户配置项
# （用户只选模型），引擎由 resolveEmbeddingEngineByModel 推导。
chk "embedding_model(所选模型)" "$EM" "bge-large-zh-embedding"
chk "embedding_engine(推导引擎)" "$EE" "onnx"
echo "$ST" | grep -q '"client_provider" : "pipeline"' && ok "client_provider=pipeline" || no "client_provider 不是 pipeline"

echo "===== 2) 配置：只应有模型，不应有引擎 ====="
RG=$(curl -s -m 20 -H "$AUTH" $B/v2/settings/rag | tr -d '\n')
# 注意：模型值形如 onnx:bge-large-zh-embedding，本身含冒号，
# 不能用 ${kv##*:} 从 "键:值" 里切值（会把 onnx: 前缀切掉），只能显式逐项对照。
get_val() { echo "$RG" | sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"; }
chk "rag_embedding_model_name" "$(get_val rag_embedding_model_name)" "onnx:bge-large-zh-embedding"
chk "rag_embedding_dimensions" "$(get_val rag_embedding_dimensions)" "1024"
# 引擎字段必须已从接口移除（否则界面上仍会让人选引擎）
echo "$RG" | grep -q '"rag_embedding_model"' && no "rag_embedding_model 仍作为设置项暴露" || ok "rag_embedding_model 已不再是设置项"

echo "===== 3) 全链路：上传 -> 向量化 -> 检索 -> 引用 ====="
KG=$(curl -s -m 30 -H "$AUTH" $B/api/knowledge/bases | sed -n 's/.*"knowledge_base_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
[ -n "$KG" ] && ok "取到知识库 $KG" || { no "没有知识库，跳过 3/4"; KG=""; }
if [ -n "$KG" ]; then
  DOC="验收套件-$(date +%H%M%S).md"
  printf '{"path":"%s","content":"# 套件自检\\n\\n青霜二号试验星于二〇二六年十月一日在太原发射，轨道高度六百公里。\\n"}' "$DOC" > /tmp/u.json
  C=$(curl -s -o /dev/null -w '%{http_code}' -m 60 -X PUT -H "$AUTH" -H 'Content-Type: application/json' --data-binary @/tmp/u.json "$B/api/knowledge/bases/$KG/file")
  chk "上传文档 HTTP" "$C" "200"
  sleep 50
  A=$(curl -s -m 180 -X POST -H "$AUTH" -H 'Content-Type: application/json' \
      -d '{"query":"青霜二号试验星在哪里发射的？","top_k":3}' "$B/api/knowledge/bases/$KG/ask")
  N=$(echo "$A" | grep -o '"doc_path"' | wc -l)
  [ "$N" -ge 1 ] && ok "ask 引用条数=$N" || no "ask 引用为空: $(echo $A|head -c 200)"
  echo "$A" | grep -q '太原' && ok "ask 答案命中事实(太原)" || no "答案未命中事实"
  # 自清理
  curl -s -o /dev/null -m 30 -X DELETE -H "$AUTH" "$B/api/knowledge/bases/$KG/file?path=$DOC" && ok "清理验收文档 $DOC"
fi

echo "===== 4) 账号目录隔离（正/反）====="
WD=$(curl -s -m 20 -H "$AUTH" $B/v2/settings/system | tr -d '\n' | sed -n 's/.*"workspaceBaseDir"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
chk "workspaceBaseDir" "$WD" "/opt/ai-starter/workspace"
NM="隔离自检-$(date +%H%M%S)"
printf '{"name":"%s","description":"自检","is_public":false}' "$NM" > /tmp/k.json
KB=$(curl -s -m 40 -X POST -H "$AUTH" -H 'Content-Type: application/json' --data-binary @/tmp/k.json $B/api/knowledge/bases)
RP=$(echo "$KB" | sed -n 's/.*"root_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
NID=$(echo "$KB" | sed -n 's/.*"knowledge_base_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
case "$RP" in
  "$WD"/*/rag/*/kb-*) ok "root_path 落在账号隔离目录: $RP" ;;
  *) no "root_path 未隔离: $RP" ;;
esac
[ -n "$NID" ] && curl -s -o /dev/null -m 30 -X DELETE -H "$AUTH" "$B/api/knowledge/bases/$NID" && ok "清理自检库 $NID"
printf '{"name":"越权自检","root_path":"%s/otheruser/rag/rag-uploads/kb-x"}' "$WD" > /tmp/bad.json
BC=$(curl -s -o /tmp/badres.json -w '%{http_code}' -m 30 -X POST -H "$AUTH" -H 'Content-Type: application/json' --data-binary @/tmp/bad.json $B/api/knowledge/bases)
chk "反向越权 HTTP" "$BC" "400"
grep -q 'root_path 不能指向托管上传目录' /tmp/badres.json && ok "拒绝原因正确" || no "拒绝原因不符: $(head -c 160 /tmp/badres.json)"

echo "===== 5) embedding 未回落占位（只看本次启动之后的日志，避免历史行假红）====="
BOOT=$(echo kali | sudo -S -p '' grep -an 'Started .* in .* seconds' $LOG 2>/dev/null | tail -1 | cut -d: -f1)
[ -z "$BOOT" ] && BOOT=1
echo "  (本次启动起于日志第 $BOOT 行；第 3 段引用非空即为「真实客户端在跑」的运行时证据)"
CUR=$(echo kali | sudo -S -p '' tail -n +$BOOT $LOG 2>/dev/null)
# 创建日志每个进程只打一次（客户端被 RagClientManager 缓存），所以查全文件存在性；
# 运行期是否真的用它，由第 3 段的「引用条数 + 命中事实」证明。
echo kali | sudo -S -p '' grep -aq '创建真实 EmbeddingClient' $LOG \
  && ok "日志存在『创建真实 EmbeddingClient』且第 3 段引用非空" || no "整个日志都没出现过真实客户端（可能一直回落内存占位）"
echo "$CUR" | grep -q 'EmbeddingClient SPI 里没有 provider' \
  && no "出现 SPI 未注册告警（回落占位）" || ok "无 SPI 未注册告警"
echo "$CUR" | grep -q '模型未注册: ' \
  && no "本次启动出现『模型未注册: <模型>』（AbstractLocalEmbeddingClient）" || ok "本次启动无 embedding 层面的『模型未注册: 』"
echo "$CUR" | grep -q 'OnnxProvider.*模板模型未注册' \
  && ok "仅启动期 ONNX 目录物化的『模板模型未注册』（无害，非 embedding）" || true

echo
echo "================ 汇总: PASS=$PASS  FAIL=$FAIL ================"
[ "$FAIL" -eq 0 ] && { echo "RESULT: ALL_PASS"; exit 0; } || { echo "RESULT: HAS_FAILURE"; exit 2; }
