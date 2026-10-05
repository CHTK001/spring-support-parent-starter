#!/bin/bash
# 验收：5 条「默认模型」规格的运行时路由判据（账号级）
#
# 与 verify-effective.sh 的分工：
#   verify-effective.sh 只验「face 组按账号隔离 + 共享单例只能在无身份线程重建」
#   本套件验 ②以图搜图 ④连接器>bot>默认 ⑤知识库rag 的**运行时解析**，并回归 ①隔离不变量
#
# ── 观测点（全部为线上真实可达路径，无模型调用、无注册表写入）─────────────────
#  ② GET /v2/image-search/status -> data.model 就是 ImageSearchStore.currentModel() 的返回值
#     （ImageSearchController:98），即「image_search 组 → 兜底 runtime 组」解析结果，按账号。
#  ⑤ GET /v2/rag/status -> data.config.llm_model 为 rag/rag_llm_model 的**原始生效值**（按账号），
#     data.needLlmConfig 为 true 正是 RagClientManager:725 走兜底分支的条件。
#     注意：兜底后解析出的**具体模型名**无法按普通账号观测 —— 触发点 resolveDelegate()
#     只在 RagController.query 上，而它带 @Permission("admin")（RagController:256）。
#     这一点如实记录为观测边界，不假装验过。
#  ④ POST /v2/chat/send -> ChatSendController:116 resolvedModel() -> resolve(null)
#     -> EffectiveChatModelResolver.candidates() 逐档 addCandidate
#     -> 每档都独立查 sys_ai_provider_model(enabled=1)，未命中就打一条
#        "[ModelResolver] 候选模型 X 未在本供应商注册"（EffectiveChatModelResolver:192）
#     ★ 关键：addCandidate 对每一档都是**急切**查表并**按调用顺序**打日志，
#       所以给两档各放一个**不同的假模型名**，两条日志的先后顺序 == 优先级顺序。
#       不需要真的调用模型，也不需要往注册表塞数据。
#       对照组：把两个假名换位后，顺序必须跟着翻转 —— 否则说明判据没有牙。
#
# ── 纪律 ──────────────────────────────────────────────────────────────────
#  * 只删 ops 自己���覆盖行（DELETE ... WHERE user_id='ops'），绝不全表删除；
#    tester 那一行是别人的，全程只读并断言其未被改动。
#  * 日志一律 grep -a（app-out.log 含 NUL/ANSI），匹配串只用 ASCII，
#    中文部分用 .* 跳过 —— 避免远端 locale 影响匹配。
#  * 日志窗口用行号水位线（时区无关）。
#  * ck 只做字面量等值比较，期望值必须是字面量；先算布尔再比，不写区间。
#  * 远端无 bc，求和用 awk。
#  * grep -c 匹配 0 条时「打印 0 且退出码 1」，故不使用 `grep -c ... || echo 0`
#    （那会得到 "0\n0" 并让 $(( )) 抛 arithmetic syntax error 而恒真）。
set -u
B=http://127.0.0.1:8787
UA="Mozilla/5.0"
LOG=/opt/ai-starter/app-out.log
S() { echo kali | sudo -S "$@" 2>/dev/null; }
Q() { S mysql --socket=/run/mysqld/mysqld.sock -uroot -proot -N ai_test -e "$1"; }
MARK=0
mark() { MARK=$(S bash -c "wc -l < $LOG" 2>/dev/null); MARK=${MARK:-0}; }
win() { S bash -c "tail -n +$((MARK+1)) $LOG" 2>/dev/null | sed -E 's/\x1b\[[0-9;]*m//g'; }
PASS=0; FAIL=0
ck() { # ck <标签> <实际> <期望字面量>
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf '  [PASS] %s = %s\n' "$1" "$2"
  else FAIL=$((FAIL+1)); printf '  [FAIL] %s : 实际=[%s] 期望=[%s]\n' "$1" "$2" "$3"; fi
}
ckorder() { # ckorder <标签> <应先出现> <应后出现>：用日志行号判先后
  local a b
  a=$(win | grep -anF "$2" | head -1 | cut -d: -f1)
  b=$(win | grep -anF "$3" | head -1 | cut -d: -f1)
  if [ -z "$a" ] || [ -z "$b" ]; then
    FAIL=$((FAIL+1)); printf '  [FAIL] %s : 标记缺失 应先=[%s](L%s) 应后=[%s](L%s)\n' "$1" "$2" "$a" "$3" "$b"; return
  fi
  if [ "$a" -lt "$b" ]; then PASS=$((PASS+1)); printf '  [PASS] %s : %s(L%s) 先于 %s(L%s)\n' "$1" "$2" "$a" "$3" "$b"
  else FAIL=$((FAIL+1)); printf '  [FAIL] %s : 顺序相反 %s(L%s) 晚于 %s(L%s)\n' "$1" "$2" "$a" "$3" "$b"; fi
}
ckge() { # ckge <标签> <实际> <下限>：存在性判据（至少 N 次）
  if [ -n "$2" ] && [ "$2" -ge "$3" ] 2>/dev/null; then PASS=$((PASS+1)); printf '  [PASS] %s = %s（≥%s）\n' "$1" "$2" "$3"
  else FAIL=$((FAIL+1)); printf '  [FAIL] %s : 实际=[%s] 要求=[≥%s]\n' "$1" "$2" "$3"; fi
}
jget() { # jget <token> <url路径> -> 整个 data 落盘，路径给下游
  curl -s -m 40 -A "$UA" -H "x-oauth-token: $1" "$B$2" -o /tmp/dmr.json -w '%{http_code}'
}
jfield() { # jfield <d路径> <字段>  从已落盘的 /tmp/dmr.json 取 data.<字段>；布尔按小写输出
  python3 - "$1" <<'PY' 2>/dev/null || echo ERR
import json,sys
try:
    o=json.load(open('/tmp/dmr.json'))
    d=o.get('data') or {}
    if isinstance(d.get('data'),dict): d=d['data']
    v=d.get(sys.argv[1])
    if isinstance(v,bool): v='true' if v else 'false'
    print('' if v is None else v)
except Exception:
    print('ERR')
PY
}
jcfg() { # jcfg <字段> 取 data.config.<字段>
  python3 - "$1" <<'PY' 2>/dev/null || echo ERR
import json,sys
try:
    o=json.load(open('/tmp/dmr.json'))
    d=(o.get('data') or {})
    c=d.get('config') or {}
    v=c.get(sys.argv[1])
    print('' if v is None else v)
except Exception:
    print('ERR')
PY
}
tok() {
  curl -s -m 25 -A "$UA" -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" "$B/v2/user/login" \
    -o /tmp/dmr_login.json -w '%{http_code}' >/dev/null
  python3 -c "import json;print((json.load(open('/tmp/dmr_login.json')).get('data') or {}).get('accessToken') or '')" 2>/dev/null
}
put() { # put <token> <group> <json>  写用户级覆盖
  curl -s -m 40 -A "$UA" -H "x-oauth-token: $1" -X PUT -H 'Content-Type: application/json' \
    -d "$3" -o /tmp/dmr.json -w '%{http_code}' "$B/v2/settings/$2"
}
clr() { # clr <token> <group>  清空该用户该组的全部覆盖（= 前端「清空（使用默认）」）
  curl -s -m 40 -A "$UA" -H "x-oauth-token: $1" -X DELETE -o /tmp/dmr.json -w '%{http_code}' \
    "$B/v2/settings/$2/overrides"
}
# rows 只数本套件触及的 5 个模型组。
#   注意：**不能**断言「某用户的覆盖行总数为 0」—— 共享环境里别的会话会合法地给
#   同一个账号写别的组（实测：并发会话给 ops 写了 session|ai_next_task_max=7，
#   因为他们把 "session" 加进了 USER_WRITABLE_GROUPS）。整用户计数会被这种
#   合法写入打破，进而把「别人的正常改动」报成产品缺陷。
#   本套件的 clr 也只清这 5 个组，绝不碰 session 等其它组。
#   （与「共享环境里不得全表删除」同源：不按用户/表整体下手，只按本套件的作用域。）
rows() { Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='$1' AND sys_ai_user_setting_group IN ('runtime','bot','image_search','face','rag');"; }
# 收尾时如实打印作用域之外的行，证明本套件既没删也没改它们
out_of_scope_rows() { Q "SELECT sys_ai_user_setting_user_id,sys_ai_user_setting_group,sys_ai_user_setting_field,sys_ai_user_setting_value FROM sys_ai_user_setting WHERE sys_ai_user_setting_group NOT IN ('runtime','bot','image_search','face','rag');" | tr '\t' '|'; }
allrows() { Q "SELECT sys_ai_user_setting_user_id,sys_ai_user_setting_group,sys_ai_user_setting_field,sys_ai_user_setting_value FROM sys_ai_user_setting;" | tr '\t' '|' ; }

echo "############ 0. 前置 ############"
APPJAR=${AI_JAR:-/opt/ai-starter/app.jar}
JAR_MD5=$(S md5sum "$APPJAR" | cut -c1-32)
ACTIVE=$(systemctl is-active ai-starter 2>/dev/null)
echo "  被测产物 md5=$JAR_MD5  is-active=$ACTIVE"
ck "服务处于 active" "$ACTIVE" "active"
OT=$(tok ops 'Ops@Guada2026'); AT=$(tok admin admin123)
if [ -z "$OT" ] || [ -z "$AT" ]; then echo "  [FATAL] 未取得令牌（ops=${#OT} admin=${#AT}）；拒绝继续"; exit 1; fi
echo "  令牌已取得 ops=${#OT}B admin=${#AT}B"

TESTER_BEFORE=$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='tester' AND sys_ai_user_setting_group IN ('runtime','bot','image_search','face','rag');")
echo "  tester 覆盖行数(基线，只读)=$TESTER_BEFORE"
echo "  ops 覆盖行数(将被自建基线清空)=$(rows ops)"
# 自建干净基线：只清 ops 自己，绝不碰 tester / 全表
for g in runtime bot image_search face rag; do clr "$OT" "$g" >/dev/null; done
sleep 2
ck "干净基线：ops 覆盖行数" "$(rows ops)" "0"
ck "干净基线：tester 行数未被动过" "$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='tester' AND sys_ai_user_setting_group IN ('runtime','bot','image_search','face','rag');")" "$TESTER_BEFORE"

echo
echo "--- 规格①的正面判据：默认模型设置**不属于管理员**，普通账号就能写 ---"
# 前面的判据都在验「隔离」，这条正面验「可写」：普通账号 PUT 必须 200，
# 且落库行必须带 user_id='ops'。若这些组被误设成 adminOnly，这里会 403 且不落库。
CK_HTTP=$(put "$OT" runtime '{"default_model":"agnes:agnes-3.0-flash"}')
ck "ops 普通账号 PUT 默认模型组的 HTTP" "$CK_HTTP" "200"
ck "落库行归属为 ops（userScoped 生效）" \
   "$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='ops' AND sys_ai_user_setting_group='runtime' AND sys_ai_user_setting_field='default_model';")" "1"
ck "tester 未被连带写入" \
   "$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='tester' AND sys_ai_user_setting_field='default_model' AND sys_ai_user_setting_value='agnes:agnes-3.0-flash';")" "0"
clr "$OT" runtime >/dev/null; sleep 2
ck "撤销后 ops 覆盖行归零" "$(rows ops)" "0"

echo
echo "############ 1. ② 以图搜图：image_search 组 -> 兜底 runtime 组 ############"
jget "$AT" "/v2/settings/image_search" >/dev/null
GLOB_IMG=$(jfield default_image_feature_model)
jget "$AT" "/v2/settings/runtime" >/dev/null
GLOB_IMG_FALLBACK=$(jfield default_image_feature_model)
echo "  全局生效 image_search/default_image_feature_model = [$GLOB_IMG]"
echo "  全局生效 runtime/default_image_feature_model     = [$GLOB_IMG_FALLBACK]  （兜底档全局为空）"
if [ -z "$GLOB_IMG" ] || [ "$GLOB_IMG" = "ERR" ]; then echo "  [FATAL] 读不到全局图像特征模型，停机"; exit 2; fi
jget "$OT" "/v2/image-search/status" >/dev/null
OPS_IMG=$(jfield model)
ck "ops 无覆盖时命中全局 image_search 组" "$OPS_IMG" "$GLOB_IMG"
ck "ops 库为空(自建基线)" "$(jfield count)" "0"
# 敏感性对照①：给 ops 写一个明显不同的值，status 必须跟着变 —— 证明它读的是 ops 自己的设置
put "$OT" image_search '{"default_image_feature_model":"zprobe-image-own"}' >/dev/null; sleep 4
jget "$OT" "/v2/image-search/status" >/dev/null
ck "对照①：ops 自配后 status.model 跟随 ops" "$(jfield model)" "zprobe-image-own"
# ★ 兜底分支证明：把 tier-1 写成**空值覆盖行**。
#   空值覆盖读出来就是空、**不会**回落全局 —— 该性质由 verify-effective.sh 判定①
#   （ops 清空 detector_model 后被判「未配置」而 admin 仍就绪）在 ad80df94 上全绿证实。
#   于是 tier-1 空 -> currentModel() 必走 runtime 档；再给 ops 的 runtime 档写特征值，
#   status.model 就必须是那个值。全程只动 ops 自己，不碰任何全局配置。
put "$OT" image_search '{"default_image_feature_model":""}' >/dev/null
put "$OT" runtime     '{"default_image_feature_model":"zprobe-img-fallback"}' >/dev/null
sleep 5
jget "$OT" "/v2/image-search/status" >/dev/null
ck "★②兜底分支：tier-1 置空后回落到 ops 的 runtime 档" "$(jfield model)" "zprobe-img-fallback"
# 敏感性对照②：把 runtime 档也清空 -> 兜底也为空 -> available 必须转 false
clr "$OT" runtime >/dev/null; sleep 5
jget "$OT" "/v2/image-search/status" >/dev/null
ck "对照②：两档皆空后 available=false（证明上面那次确实靠兜底撑住）" "$(jfield available)" "false"
clr "$OT" image_search >/dev/null; sleep 4
jget "$OT" "/v2/image-search/status" >/dev/null
ck "清空后回到全局默认" "$(jfield model)" "$GLOB_IMG"

echo
echo "############ 2. ⑤ 知识库 rag：rag 组按账号隔离 ############"
jget "$AT" "/v2/rag/status" >/dev/null
ADMIN_RAG=$(jcfg llm_model); ADMIN_NEED=$(jfield needLlmConfig)
# ★ 真正的全局值必须**从全局表直读**，不能拿 admin 的 status 当代理。
#   admin 是超级管理员，可能自己就有 rag 覆盖行；实测就出现过
#   admin=[agnes-3.0-flash] 而 sys_ai_global_setting 里是 [agnes-2.0-flash] 的情况。
#   拿 admin 当全局代理，会把「回落全局」这条判据的期望值写成 admin 的覆盖值 ——
#   产品行为是对的、判据却判 FAIL。判据的前提假设错，比判据失效更隐蔽。
GLOB_RAG=$(Q "SELECT sys_ai_global_setting_value FROM sys_ai_global_setting
             WHERE sys_ai_global_setting_group='rag'
               AND sys_ai_global_setting_field='rag_llm_model';")
echo "  admin 自己读到的 rag/rag_llm_model = [$ADMIN_RAG]  needLlmConfig=[$ADMIN_NEED]"
echo "  全局表 sys_ai_global_setting.rag/rag_llm_model = [$GLOB_RAG]   ← 回落判据的期望值取自这里"
if [ -z "$ADMIN_RAG" ] || [ "$ADMIN_RAG" = "ERR" ]; then echo "  [FATAL] 读不到 admin 的 rag 模型，停机"; exit 2; fi
# 守卫要把 mysql 的报错串一起挡住：Q() 失败时返回的是 `ERROR 1698 (28000): ...` 而非空值，
# 只判 -z 会让期望值变成一句报错，然后判出一条看不懂的 FAIL。
case "$GLOB_RAG" in
  ""|ERR|ERROR*) echo "  [FATAL] 读不到全局 rag 模型（GLOB_RAG=[$GLOB_RAG]），停机"; exit 2 ;;
esac
put "$OT" rag '{"rag_llm_model":"agnes:agnes-3.0-flash"}' >/dev/null; sleep 4
jget "$OT" "/v2/rag/status" >/dev/null
ck "ops 自配 rag 模型后按账号生效" "$(jcfg llm_model)" "agnes:agnes-3.0-flash"
ck "ops 已配置故 needLlmConfig=false" "$(jfield needLlmConfig)" "false"
jget "$AT" "/v2/rag/status" >/dev/null
ck "★admin 未受影响（隔离不变量）" "$(jcfg llm_model)" "$ADMIN_RAG"
# 兜底分支证明：同样用**空值覆盖行**把 tier-1 读成空（注意不是 clr——clr 是删行，删了会回落全局，
#   那样就测不到兜底了；put "" 留的是空值行，getStrAs 不会回落全局）。
put "$OT" rag '{"rag_llm_model":""}' >/dev/null; sleep 5
jget "$OT" "/v2/rag/status" >/dev/null
ck "⑤兜底分支：tier-1 空值覆盖 -> needLlmConfig=true" "$(jfield needLlmConfig)" "true"
ck "⑤兜底分支：tier-1 原始值确实为空" "$(jcfg llm_model)" ""
# 语义对照：clr 删行 -> 回落全局。这一条把「空值行」与「删行」两种语义分开钉死。
# 期望值是 GLOB_RAG（全局表的真值），不是 ADMIN_RAG（admin 的覆盖行）。
clr "$OT" rag >/dev/null; sleep 5
jget "$OT" "/v2/rag/status" >/dev/null
ck "对照：clr 删行后回落全局（与空值行语义不同）" "$(jcfg llm_model)" "$GLOB_RAG"
ck "对照：回落全局后 needLlmConfig=false" "$(jfield needLlmConfig)" "false"
echo "  [边界] 兜底后解析出的具体模型名无法按普通账号观测：触发点 resolveDelegate()"
echo "         只挂在 RagController.query 上，该方法带 @Permission(\"admin\")（RagController:256）。"
echo "         故本套件只证明「按账号隔离」与「兜底分支条件成立」，不谎称验过兜底后的取值。"

echo
echo "############ 3. ④ 连接器 > bot > 默认：优先级顺序（按日志先后） ############"
BM=zprobe-bogus-bot-1; RM=zprobe-bogus-run-1
put "$OT" bot    "{\"default_model\":\"$BM\"}" >/dev/null
put "$OT" runtime "{\"default_model\":\"$RM\"}" >/dev/null
sleep 5
mark
curl -s -m 60 -A "$UA" -H "x-oauth-token: $OT" -H 'Content-Type: application/json' \
  -d '{"content":"dmr-probe"}' "$B/v2/chat/send" -o /tmp/dmr_send.json -w '  chat/send HTTP=%{http_code}\n'
sleep 6
echo "  --- 窗口内 ModelResolver/ChatSend 日志 ---"
win | grep -aE 'ModelResolver|ChatSend' | grep -aE 'zprobe|\[ChatSend\]' | cut -c1-170 | sed 's/^/    /'
ckorder "bot 档先于 runtime 档（= 连接器未配时 bot 优先于默认）" "$BM" "$RM"
# 这条同时钉住一个前提：解析发生在**无身份异步线程**上（日志可见 *Pool-worker-*），
# 却依然读到了 ops 自己写的覆盖值 —— 说明 AiUserContext 跨线程传递有效。
# 若上下文不传递，userOverride() 会拿到 null 而回落全局，两档就都是全局值、
# 这两个只存在于 ops 的标记根本不会出现在日志里，ckorder 会因「标记缺失」直接 FAIL。
TH=$(win | grep -aF "$BM" | head -1 | grep -aoE '\[[^]]*[Pp]ool-worker[^]]*\]' | head -1)
if [ -n "$TH" ]; then PASS=$((PASS+1)); printf '  [PASS] 解析发生在无身份异步线程 %s 且仍读到 ops 覆盖\n' "$TH"
else echo "  [info] 未在异步线程标记上匹配到（若解析发生在请求线程，覆盖可见性由 ckorder 隐含保证）"; fi
# 仅供参考，不计入判据：两档都被拒后是否最终解析为空，取决于「兼容旧键」里是否还有可用项，
# 与本条要证的「先后顺序」无关，故不做硬断言（否则会因无关原因误判失败）。
if win | grep -aF '[ChatSend]' | grep -aq .; then echo "  [info] ChatSend 打出告警（本次两档均被拒后解析为空）"
else echo "  [info] 未见 ChatSend 告警（兼容旧键仍提供了可用模型，不影响顺序判据）"; fi

echo
echo "  --- 敏感性对照：把两个假名换位，顺序必须跟着翻转 ---"
BM2=zprobe-bogus-run-2; RM2=zprobe-bogus-bot-2
put "$OT" bot    "{\"default_model\":\"$BM2\"}" >/dev/null
put "$OT" runtime "{\"default_model\":\"$RM2\"}" >/dev/null
sleep 5
mark
curl -s -m 60 -A "$UA" -H "x-oauth-token: $OT" -H 'Content-Type: application/json' \
  -d '{"content":"dmr-probe"}' "$B/v2/chat/send" -o /tmp/dmr_send2.json -w '  chat/send HTTP=%{http_code}\n'
sleep 6
win | grep -aE 'ModelResolver' | grep -aF 'zprobe' | cut -c1-170 | sed 's/^/    /'
ckorder "★换位后顺序翻转（证明上一条不是巧合）" "$BM2" "$RM2"

echo
echo "############ 3b. ④ 连接器档 > bot 档 > 默认档：判别「实际服务的模型」 ############"
# §3 只证了 bot 档 > 默认档，且当时的判据原文就是「连接器未配时」——
#   连接器那一档（①，调用方显式指定）压根没配、也没验。这里补上。
# ① 档从哪来：EffectiveChatModelResolver.candidates(explicitModel) 的第一个入参，
#   全项目只有两处传了非 null：AiBotChatService.resolveModelName(bot)/aiReply 传 bot.getModel()，
#   SessionNextTaskService 传 session.getModelId()。
#   其中 bot.getModel() 就是 sys_ai_bot_binding.model —— 连接器自己绑的模型，即需求里的「连接器的模型」。
# 触发口：POST /v2/connectors/simulate/<id>（BotSimulateController，@Profile("local")，
#   部署态 ExecStart 实测 -Dspring.profiles.active=local，且无令牌打该路径返回 403 而非 404，路径已注册）
#   -> simulateInbound -> handleMessage -> aiReply(bot,...) -> candidates(bot.getModel())
#   -> 私聊（fromGroup=false）必走「直接回复」分支，必调 aiReply。
# 观测点：SysAiUsageServiceImpl 的 "[Usage] 未找到定价: provider=openai, model=<X>"
#   —— 回显的是**这次调用真正用的模型**。这比 §3 的「候选求值先后」更强：
#   先后只能说明遍历次序，这条能说明谁真的答了话。
# 纪律：连接器绑定是 admin 的在线 wechat 连接器，本套件**只读、绝不改**；
#   写入只发生在 ops 自己的 5 个模型组内。
BND=$(Q "SELECT sys_ai_bot_binding_id FROM sys_ai_bot_binding WHERE ifnull(sys_ai_bot_binding_model,'')<>'' ORDER BY sys_ai_bot_binding_id LIMIT 1;" | tr -d '\r')
BM1=$(Q "SELECT sys_ai_bot_binding_model FROM sys_ai_bot_binding WHERE sys_ai_bot_binding_id=$BND;" | tr -d '\r')
if [ -z "$BND" ] || [ -z "$BM1" ]; then
  echo "  [SKIP] 库里没有「配了模型的连接器绑定」，本段无法在当前环境取证 —— 不谎称验过 ① 档"
  echo "  [SKIP] 需求④的「连接器 > bot」这一腿因此仍未取证"
else
  echo "  连接器绑定 id=$BND  ①档模型=$BM1（只读，全程不改这行）"
  echo
  echo "-- ★主判据：三档全部设为**在册启用**模型，此时胜出者只能由优先级决定 --"
  # ②/③ 取两个与 ① 名字前缀相近但不同的在册模型，grep -F "model=<X>" 不会互相误命中
  B2=agnes-3.0-flash; R2=agnes-2.0-flash
  put "$OT" bot    "{\"default_model\":\"$B2\"}" >/dev/null
  put "$OT" runtime "{\"default_model\":\"$R2\"}" >/dev/null
  sleep 5
  mark
  MID="vmdr-conn-$$-$(date +%s)"
  curl -s -m 120 -A "$UA" -H "x-oauth-token: $OT" -H 'Content-Type: application/json' \
    -d "{\"content\":\"say one word: ok\",\"fromUser\":\"vmdr-probe\",\"fromGroup\":false,\"msgId\":\"$MID\"}" \
    "$B/v2/connectors/simulate/$BND" -o /tmp/dmr_sim.json -w '  simulate HTTP=%{http_code}\n'
  sleep 8
  echo "  --- 响应（outcome 与 sentText） ---"
  python3 -c "import json;d=json.load(open('/tmp/dmr_sim.json'));r=(d.get('data') or {});print('    outcome  =',r.get('outcome'));t=r.get('sentText');print('    sentText =',repr(t)[:120] if t else t)" 2>/dev/null || head -c 300 /tmp/dmr_sim.json
  echo "  --- 窗口内 Usage / ModelResolver / BotChat ---"
  win | grep -aE '\[Usage\]|ModelResolver|BotChat' | cut -c1-170 | sed 's/^/    /'
  ck "④ 连接器模拟入站拿到回复（私聊必回复分支确实走通）" \
     "$(python3 -c "import json;print((json.load(open('/tmp/dmr_sim.json')).get('data') or {}).get('outcome'))" 2>/dev/null)" "REPLIED"
  ckge "★④ 实际服务的模型 = 连接器自配的 ① 档" "$(win | grep -acF "model=$BM1" | head -1)" 1
  ck "★④ bot 档 ②（$B2，在册启用）根本没被用过" "$(win | grep -acF "model=$B2" | head -1)" 0
  ck "★④ 默认档 ③（$R2，在册启用）根本没被用过" "$(win | grep -acF "model=$R2" | head -1)" 0
  # 关键反证：窗口内一条 ModelResolver 都没有 = 三档全被接受、没有任何一档被「不在模型管理中」过滤掉。
  #   有了这条，才能断言胜出者是**优先级**决定的，而不是「只有 ① 合法」被动造成的。
  ck "★④ 三档全合法（窗口内无 ModelResolver 过滤行，胜出靠优先级不靠过滤）" \
     "$(win | grep -acF '[ModelResolver]' | head -1)" 0

  echo
  echo "-- ★敏感性对照：②③ 换成**假名**（必然被过滤），① 仍须胜出，且这次要看到过滤行 --"
  CB=zprobe-conn-bot; CR=zprobe-conn-run
  put "$OT" bot    "{\"default_model\":\"$CB\"}" >/dev/null
  put "$OT" runtime "{\"default_model\":\"$CR\"}" >/dev/null
  sleep 5
  mark
  MID2="vmdr-conn2-$$-$(date +%s)"
  curl -s -m 120 -A "$UA" -H "x-oauth-token: $OT" -H 'Content-Type: application/json' \
    -d "{\"content\":\"say one word: ok\",\"fromUser\":\"vmdr-probe2\",\"fromGroup\":false,\"msgId\":\"$MID2\"}" \
    "$B/v2/connectors/simulate/$BND" -o /tmp/dmr_sim2.json -w '  simulate HTTP=%{http_code}\n'
  sleep 8
  win | grep -aE '\[Usage\]|ModelResolver' | cut -c1-170 | sed 's/^/    /'
  ck "对照 连接器模拟入站仍拿到回复" \
     "$(python3 -c "import json;print((json.load(open('/tmp/dmr_sim2.json')).get('data') or {}).get('outcome'))" 2>/dev/null)" "REPLIED"
  ckge "对照 ②③ 被过滤时，实际服务的仍是 ① 档" "$(win | grep -acF "model=$BM1" | head -1)" 1
  ckge "对照 本次确实出现了过滤行（与主判据的差别就在 ②③ 合法性）" \
      "$(win | grep -acF '[ModelResolver]' | head -1)" 1
  ck "对照 假名 ② 出现在候选里（证明 ② 档确实被读到了，不是压根没读）" \
     "$(win | grep -acF "$CB" | head -1 | awk '{print ($1>0)?"1":"0"}')" "1"
  ck "收尾前：连接器绑定未被本套件改动" \
     "$(Q "SELECT ifnull(group_concat(distinct sys_ai_bot_binding_model),'-') FROM sys_ai_bot_binding;" | tr -d '\r')" "$BM1"
fi

echo
echo "############ 4. 回归 ① 隔离不变量 + 收尾 ############"
for g in runtime bot image_search face rag; do clr "$OT" "$g" >/dev/null; done
sleep 4
ck "收尾：ops 覆盖行数归零" "$(rows ops)" "0"
ck "收尾：tester 行数未被改动" "$(Q "SELECT COUNT(*) FROM sys_ai_user_setting WHERE sys_ai_user_setting_user_id='tester' AND sys_ai_user_setting_group IN ('runtime','bot','image_search','face','rag');")" "$TESTER_BEFORE"
jget "$AT" "/v2/rag/status" >/dev/null
ck "收尾：admin rag 模型仍为全局值" "$(jcfg llm_model)" "$ADMIN_RAG"
jget "$AT" "/v2/image-search/status" >/dev/null
ck "收尾：admin 以图搜图模型未被 ops 污染" "$(jfield model)" "$GLOB_IMG"
echo "  --- 作用域之外的覆盖行（别的会话的合法写入，本套件既没删也没改）---"
out_of_scope_rows | sed 's/^/    /'
echo "  --- sys_ai_user_setting 全表 ---"
allrows | sed 's/^/    /'
echo
echo "############ 汇总 ############"
echo "  被验产物 md5 = $JAR_MD5"
echo "  PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then echo "  RESULT=DMR_ROUTING_OK"; else echo "  RESULT=DMR_ROUTING_FAIL"; fi
echo "DMR_ROUTING_DONE"
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
