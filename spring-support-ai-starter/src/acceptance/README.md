# spring-support-ai-starter 验收资产

本目录是 **ai-starter 的生产级验收脚本**的版本控制归属地。
**不要把这类脚本放在仓库外的 `.run/deploy/` 或 `/tmp`** —— 前者不在任何 git 仓库里
（`D:\ch\project\.run` 位于两个仓库的工作树之外），后者会被重启清空，两者都会让
「已验收」变成一句没有凭据的话（AGENTS「未提交的改动不构成交付物」）。

## 目录索引

| 文件 | 用途 |
|---|---|
| `prodgrade.sh` | **以图搜图**生产级验收，7 组（输入健壮性 / 并发与幂等 / 数据完整性 / 重启一致性 / 鉴权 / 配置热更新 / 资源与落盘） |
| `facegrade.sh` | **人脸识别**生产级验收，11 组（能力与输入健壮性 / 检测 / 特征 / 1:1 / 1:N / 快照持久化 / 列表与删除 / 鉴权 / **rebuild 回归** / 此前未覆盖的端点） |
| `verify-secfix.sh` | 越权修复的**敏感性对照**：凭据类字段必须 403、模型类与纯偏好组必须 200 |
| `verify-store-isolation.sh` | **账号级向量索引隔离**验收，6 组（目录归属 / 人脸列表跨账号不可见 / 索引文件归属与快照内容 / 删除不串号 / 以图搜图列表跨账号不可见 / **写入侧按 userId 建库**） |
| `verify-effective.sh` | 「用户改自己的默认模型后**立即生效**」的取证：事件是否发布、重建是否按账号绑定、共享单例是否被误重建 |
| `verify-default-model-routing.sh` | 五条「默认模型」规格的**运行时路由**：②以图搜图 ④连接器>bot>默认 ⑤知识库rag 的请求期解析 + ①隔离不变量回归。观测点零模型调用、零注册表写入；每条判据都带敏感性对照。**§3b 另用 `POST /v2/connectors/simulate/{id}` 判别「实际服务的模型」，把 ④ 的①连接器档补齐**（`48de2d40` 上 37/0） |
| `verify-model-fallback-tier2.sh` | 「默认模型」三处**兜底分支**的运行时取证：②以图搜图、③人脸识别、⑤知识库rag。**只造 ops 自己的夹具行、只动 `sys_ai_user_setting`，绝不整表删、不碰他人数据**（`48de2d40` 上 37/0 `T2_FALLBACK_OK`） |
| `cleanup-final-state.sh` | 测试残留清理 + 线上最终状态核对 |
| `验收报告-20261004.md` | 验收报告，**当前绑定 md5 `48de2d40`**（此前依次绑过 `97804124` / `c09c01d1` / `95fabe52`） |
| `KB-DELIVERY.md` / `kb-accept-backend.sh` | 知识库交付与验收（另一条线） |
| `jar-watch*.sh` / `test-jar-watch-evidence-keep.sh` | jar-watch 证据保留相关 |

浏览器侧用例不在这里，在 **`vue-support-parent-starter/apps/vue-support-ai-guada/src/acceptance/`**
（11 套，`run-browser-accept.ps1` 跑 dev，`run-all-against-dist.mjs` 跑产物）。

## 怎么跑

```bash
# 后端（每套内部会重启 ai-starter，跑之前先确认没有并发部署在飞）
AI_ACC=admin bash prodgrade.sh      # 期望 PASS=38  FAIL=0
bash facegrade.sh                   # 期望 PASS=N   FAIL=0（N 随用例增删变化，以实际为准）

# 行为复核（不重启服务）
bash verify-secfix.sh               # 期望四个凭据字段 403、模型字段 200
bash verify-store-isolation.sh      # 期望 PASS=22  FAIL=0  结论=ISOLATION_OK
bash verify-effective.sh            # 期望「人脸设置变更（用户级）」+ 只有 ops 未配置 + 共享单例重建增量 0
```

### `verify-store-isolation.sh` 的判据为什么长这样

隔离的判据不是「功能能用」，而是**A 账号的数据 B 账号拿不到**。改造前人脸与以图搜图
共用一份全局 jvector 索引，功能全绿却越权可见，只有跨账号交叉断言才抓得到。三处判据
与直觉相反，改动前务必先读：

- **删除的期望是 `200 + data=false`，不是 403/404。** `FaceStores.remove` 走当前账号的库，
  `ops` 的库里没有 `admin` 的 id → 空操作。契约上「删不到别人的东西」就是成功；
  断言成 403 才是错的。若底层是共享索引，这条会回 `data=true` 并把 `admin` 的条目删掉。
- **目录要求按「该账号该功能是否真有数据」区分。** 人脸库**急**创建（打开账号 store
  即建目录），以图搜图**惰**创建（`ImageSearchStore.java:141`「目录只在首次写入/删除时
  才创建」），空库账号没有目录是设计如此。而且目录一旦建过就**常驻**（清理只删条目、
  不删目录），所以「空库账号不得有目录」同样不成立 —— 空库账号的判据是
  「目录若在，必须是自己的、且不含 `admin` 的任何 id」。
- **写入侧必须正面验。** `ops` 落库后要出现 `<runtime>/ops/imagesearch/index.json`，
  且 `admin` 的底库条数不变。改造前两者共用一份索引，这条会直接失败。

该套件**自建干净基线**：开头先把 `ops` 的以图搜图底库清空再计数。上一轮探针或中断
留下的脏数据会让「`ops` 应为 0 条」的判据因历史残留而非隔离缺陷失败。

反向对照（证明判据有牙，不改动线上真实快照，只在副本上做）：
未植入时扫描结果 `0`；把 `admin` 的快照追加进 `ops` 快照的**副本**后扫描结果 `2`。

人脸套件需要一张**含人脸**的夹具，装在**持久目录**（不要放 `/tmp`，2026-10-01 重启清空过 `/tmp`）：

```bash
src=vue-support-parent-starter/apps/vue-support-ai-guada/public/gen-style/photo.jpg
# 期望 md5=3b3742a673d6648e3ff2c1e19c303132  size=12131（装完回读校验）
sudo mkdir -p /opt/ai-starter/acceptance-20261001/fixtures
sudo install -m 644 "$src" /opt/ai-starter/acceptance-20261001/fixtures/face_probe.jpg
```
可用 `AI_FACE_IMG` 指向别的图。

## 产物绑定（**必读**）

- 两套件都会在**输出里自报被测产物 md5**：`被测产物 /opt/ai-starter/app.jar md5=<8位>`。
- **报告必须与该 md5 绑定；换包即失效，须重跑并重新绑定。** 报告里手写的 md5 迟早过期
  （2026-10-04 实测：报告绑 `f3bd9c5b`，而现役已过五代到 `97804124`；
  2026-10-05 再过一次到 `95fabe52`，又一次到 **`48de2d40`**）。
- **⚠️ 换包是静默发生的，必须主动核对。** 2026-10-05 实测：我在为需求④补判据做只读探测时，
  才发现 `md5sum` 已变、`MainPID` 已从 `575743` 变成 `726409`、`NRestarts=0`、服务 `etime=03:46`
  —— **jar 是在我探测前约 4 分钟刚被并发会话换上去的**。
  上一轮那套绑在旧 md5 上的全绿证据，对当前运行态**一律失效**。
  **每轮跑判据前 / 跑完都必须核对 md5 一次**（跑中再核一次，防中途被换），
  驱动脚本里写成硬守卫：变了就 `exit 4`，而不是继续跑完输出一份看着很绿的报告。
- 可覆盖：`AI_JAR`（产物路径）、`AI_BASE`（服务基址）、`AI_ACC`/`AI_STORE`（以图搜图账号与底库目录）、
  `AI_APP_LOG`（应用日志路径）、`AI_EMPTY_USER`/`AI_EMPTY_PASS`（空库样本账号）。

## 写/改用例时必须遵守的判据纪律（都是踩出来的）

1. **探针坏了会产出「假红」，也会产出「假绿」——先确认判据本身是对的，再看 FAIL。**
   - `facegrade.sh` 曾因 `sudo_()` 定义在使用点**之后**（L174 vs L129/L139），
     探「库是否为空」时命令不存在、输出为空，`${BEFORE_N:-0}` 退化成 `"0"`，
     把**非空库判成空库** → 在 2 条记录的库上断言「空库应 matched=false」→ 假红 1 条。
   - 现在判空改走 **API**（`GET /v2/face/list`，应用自己的权威视图），
     且**读不到一律回 `ERR`、绝不回 `0`** —— 「读不到」和「空库」必须分开。
   - admin 库非空时**不跳过**，改用另一个确实为空的账号验「新账号第一次识别」；
     跳过等于这条防 NPE 的覆盖在真实环境里**永远不跑**。
2. **不要按绝对阈值判定**，要拿「改动前」做基线（BOM / U+FFFD / 覆盖行数都踩过假红）。
3. **不要断言存量数据**。先取真实数据，再断言 UI/接口与它一致（`prodgrade.sh` 的基线与 `STORE` 都不写死路径）。
4. **不要硬编码后端会变的返回值**（模型能力、时长档位等）。
5. **比对数据用「id 集合」而不是「条数」** —— 曾用「id > 条数」判断新增，误删两条真实记录。
6. **`ck` 只能做等值比较，期望值必须是字面量。** 曾把期望写成区间 `"1..$N"` 再交给 `ck`
   比 `2` 和 `1..2`，**永远不等** → 恒 FAIL。要区间就先算布尔、再比 `ok`/`bad`。
7. **包装函数要透传参数，也要确认落盘内容。** `api_as` 写死三个形参时，第 4 个参数
   `'-X DELETE'` 被**静默丢弃**，实际发出 GET → 恒 200 → 判「被拒」必假绿。
   同一函数改用 `"$@"` 透传后又漏了 `"$url"`，`curl` 报 `no URL specified`。
   **改动包装函数后，先用一个已知输入验证它，而不是直接看 FAIL 数。**
8. **`grep -c` 匹配 0 条时会「打印 0 且退出码为 1」。** 写 `grep -c ... || echo 0` 会得到
   `"0\n0"`，`$((x + n))` 报 `arithmetic syntax error`、累加值停在 0 → **断言蒙对**。
   只取 `grep` 自己的输出，并用 `case` 兜住非数字。
9. **别让异常被 `except` 吞成 `ERR`。** 一段管道里的 python 漏 `import sys`，
   `NameError` 被 `except Exception: print('ERR')` 捕获，判据永远失败且看不出原因。
   解析响应的代码**必须落盘再解析**（`curl -o file` + `json.load(open(...))`），
   这样出错时能看到响应体本身。
10. **依赖外部命令前先确认存在。** 远端**没有 `bc`**，用 `bc` 做求和会静默退化成 0（假绿）；
    一律改用 `awk '{s+=$1} END{print s+0}'`。

11. **别拿自己和自己比。** 写 `ck "…" "$(grep -c X)" "$(grep -c X)"` 这类「同时打计数」的写法时，
    一旦两边是同一条命令，就是**恒 PASS 的假判据**。断言的两端必须来自不同来源。
12. **布尔值的期望值要跟取值口径对齐。** 用 python `print()` 取 JSON 布尔会得到 `False`/`True`
    （首字母大写），而断言习惯写 JSON 的 `false`/`true` → 连续恒 FAIL。
    修法是**在取值函数里把 bool 统一小写化**，而不是把期望值改成 `False`
    （后者把「取值口径」和「断言口径」搅在一起，以后改取值就会静默失效）。
13. **「空值覆盖行」与「删行」语义不同，别混用。**
    `PUT /v2/settings/{group}` 传 `""` → 库里留一个**空值行**，`GlobalSettingReader.getStrAs`
    返回 `""` 且**不回落全局**；`DELETE /v2/settings/{group}/overrides` → **删行**，无行才回落全局。
    要在**不改全局配置**的前提下验「兜底分支」，必须用前者；用后者会把全局值当成兜底结果，
    测到的根本不是兜底。

## `verify-default-model-routing.sh`：怎么在零成本下证「优先级顺序」

验「连接器 > bot > 默认」这类**顺序**规格，直觉做法是「让某一档可用、看解析结果等于谁」。
问题：这样只能证明**某一档被选中**，证不出**顺序**，而且依赖那一档恰好可用。

本套件换了个思路。`EffectiveChatModelResolver.addCandidate` 对每一档都是**急切**查
`sys_ai_provider_model(enabled=1)`，未命中就打一条
`[ModelResolver] 候选模型 X 未在本供应商注册`（`EffectiveChatModelResolver:192`），
**并按调用顺序打**。于是：

1. 给 bot / runtime 两档各写一个**不同的假模型名**；
2. 触发一次 `resolve(null)`（`POST /v2/chat/send` 即可，`ChatSendController:116`）；
3. 断言两条日志的**行号先后**。

这样**不需要真的调用模型，也不需要往注册表塞数据**，而且不依赖任何一档恰好可用。
**敏感性对照**：把两个假名对调后重跑，顺序必须跟着翻转 —— 否则这条判据没有牙。

> 附带钉住一个前提：解析发生在无身份异步线程（`*Pool-worker-*`）上，**却仍读到了用户的覆盖值**
> （`AiUserContext` 跨线程传递有效）。若上下文不传递，两个只存在于该用户的标记根本不会出现
> 在日志里，判据会因「标记缺失」直接 FAIL。

14. **交付的 `.sh` 必须是 LF 换行。** CRLF 会让 bash **每一行**都报 `command not found`
    （`\r` 会被当成命令名的一部分）。本轮实测踩到：写入时用了会写 CRLF 的
    `[IO.File]::WriteAllLines`，提交后工作树与 blob 都是 `CR=267 LF=267`。
    **它当时没暴露**，因为发送到远端时剥掉了 `\r` —— 传输侧的兜底掩盖了文件本身的缺陷。
    验证必须用**字节统计**（`[IO.File]::ReadAllBytes` 数 CR/LF）；
    **不要**用 PowerShell 读 `git cat-file` 的输出来判断 —— PowerShell 按行读取时
    已经把 CR 吃掉，会得出「CRLF 数 = 0」的假结论。
15. **查库先做连通性自检，再把查询结果当证据。** 查错库 / 写错列名 / sudo 口令错，
    三者都表现为**空输出**；空输出极易被读成「表不存在」「残留 0 行」——
    而这恰恰是判据最容易产出**假绿**的地方。
    任何查库判据的前置必须是：先断言 `SELECT DATABASE()` 与表总数非零，
    再解读业务查询的结果。**`2>/dev/null` 只可用于已自检过的路径**，
    探查阶段一律保留 stderr（实测 `echo ai | sudo -S mysql …` 会把库用户名当 sudo 口令，
    叠加 `2>/dev/null` 后彻底静默）。

## 环境与工具链的坑（都实测过）

| 坑 | 事实与对策 |
|---|---|
| 业务库是 `ai_test`，且**不在 root 的库清单里** | 连法 `mysql -N -B -h 127.0.0.1 -P 3306 -u ai -pai ai_test -e '…'`，**不需要 sudo**。root 走 unix_socket 的 `SHOW DATABASES` **根本不含 `ai_test`**（`oauth` 是用户域），照它判断会误判「表不存在」。库的权威答案在服务启动参数里：`systemctl cat ai-starter \| grep -o 'spring\.datasource\.url=[^ ]*'`。 |
| 本项目所有列名都带表名前缀 | `sys_database_version_success`、`sys_ai_global_setting_value`、`sys_ai_user_setting_user_id`… 写 SQL 时按 `sys_<表>_<列>` 拼，别按通用命名猜。 |
| `mysql -N -B -- "$DB" -e …` 会打 3000 行用法 | `--` **不是** mysql 的合法参数。正确写法 `mysql -N -B "$DB" -e …`。 |
| `sys_ai_user_setting.user_id` 存**用户名**不是数字 id | 判据按用户名查即可（现存 admin 7 行 + 他人 tester 1 行）。 |
| 应用日志位置 | 在 **`/opt/ai-starter/app-out.log`**（systemd `StandardOutput=append`），**不在 journalctl**。按 journalctl 取日志会得到**全空的证据段落却照样跑完** —— 空证据不是通过。 |
| 日志文件含 NUL/ANSI | 裸 `grep` 会报 `binary file matches` 并**停止输出** → 假空结果。**必须 `grep -a`**。 |
| 按时间过滤日志 | 日志自带 `-04:00` 时区，而本机系统时区**重启前后变过**（UTC+8 → EDT），按本地时间比对会错位 12 小时。**改用行号水位线**（`verify-effective.sh` 的做法）。 |
| `/tmp` 会被清空 | 机器重启后 `/tmp` 全空，`bash /tmp/xx.sh` 报 `No such file or directory`。**上传后先确认存在**，且**不要把过滤后的空输出当成"跑完了"** —— 失败信息也会被过滤器吃掉，务必**无条件打印末尾几行**。 |
| 输出过滤吃掉失败 | 实测：`| Select-String 'PASS=|PRODGRADE_DONE'` 把 `No such file or directory` 全滤掉，看起来是"无输出"。 |
| 共享实例串号 | 默认模型按用户隔离后，**共享单例**（`FaceClientManager` 的检测器/分类器、`OcrClientManager`、`RagClientManager` 的嵌入实例）只承载**全局**配置；重建时若不绑定身份，会把触发者的配置烘进去串掉其他用户。按账号构建的一方（`FaceStores`）才该响应用户级写入。 |
| 并发部署 | 本服务常有多会话并发部署/重启。跑重启类用例前先确认没有并发部署在飞，否则会互相打断，表现为「服务反复起停」。**换包是静默的，跑前必须自己核 md5（见「产物绑定」）。** |
| 判断端点「是否注册」别读代码推断，**直接打一发** | 无令牌 `curl -X POST <路径> -d '{}'`：**404 = 路径不存在/未注册**，**401/403 = 已注册、只是缺鉴权**。2026-10-05 靠这一枪确认了 `POST /v2/connectors/simulate/1` 确实存在（403），才敢用它当触发口。 |
| `POST /v2/connectors/simulate/{id}` 是 `@Profile("local")` | 部署态实际 profile 看 `systemctl cat ai-starter \| grep -a ExecStart` 里的 `-Dspring.profiles.active=`（2026-10-05 实测 = `local`）。换非 `local` profile 时该端点不存在，判据必须自动 `[SKIP]` 并明说「不谎称验过」，不能悄悄跳过。 |
| 要判别「**实际用了哪个模型**」，别只看候选求值先后 | `[ModelResolver] 候选模型 X …跳过` 的**先后**只证明**遍历次序**，不证明谁真的答了话（假名走的是 `addCandidate` 跳过分支）。真正的判别点是 `SysAiUsageServiceImpl` 的 `[Usage] 未找到定价: provider=openai, model=<X>` —— 它回显**这次调用真正使用的模型**。拿到它之后，「三档全合法时①仍胜出、且②③ 一次都没被调用」才成为可能。 |
| 「重建」类日志只打在**需要重建**时 | `[Face] 人脸设置变更（用户级），重建账号 'X' 的人脸库` **只在管线不可用、要重建时才打**；tier-2 生效、管线直接建起时**不打**。此时该看的正面证据是同窗口的 `[Face] 账号 'X' 人脸库就绪: n/m 条`。2026-10-05 实测：新产物 `48de2d40` 改成了「只在需要重建时打」，绑死前者的判据必红 —— 定性为**判据缺陷**（非产品回归），详见 `验收报告-20261004.md` §6.10。 |
| 数实例别用 `pgrep -f app.jar` | 同一台机上还有别的项目的 java 进程，`pgrep -f app.jar` 会把它们数进来。口径按 **jar 绝对路径**：`ps -eo args \| grep -a '/opt/ai-starter/app.jar' \| grep -av grep \| wc -l`，再配 `ss -ltn \| grep -c ':8787'` 交叉验证。 |

## 已知未通过/已记录的缺口

- `POST /v2/face/super-resolution` 依赖的默认回退模型权重未上传 → 该端点可能 500，
  第 9 组会据此报警（**不是用例缺陷**，属已记录的产品缺口）。
- 产物侧 `dist/app.yaml` 缺失（落在 `dist/apps/<app>/app.yaml`）→ 运行时取不到配置、
  品牌名退化。属打包侧问题，详见 `验收报告-20261004.md` §7.1。
- **需求④「连接器 > bot > 默认」已三档全验（`48de2d40`，37/0），但仍留两条腿未单独取证**，
  如实记录在 `验收报告-20261004.md` §5.5.12(4)：
  ① 档只经 `POST /v2/connectors/simulate/{id}` 取证，
  **真实发送路径 `/v2/connectors/{id}/ai-send` 没有单独取证**（代码上二者最终汇到同一个 `aiReply`）；
  `SessionNextTaskService:265` 那条 `session.getModelId()` 入口**也未单独取证**。
- **`sys_ai_model` 目录表由 8 行变为 0 行**：**只记现象、不定性为缺陷**（无审计轨迹，
  无法排除他人按自己的夹具清理）。详见 `验收报告-20261004.md` §7.6。
