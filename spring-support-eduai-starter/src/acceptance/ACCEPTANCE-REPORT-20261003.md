# eduai 终验报告（2026-10-03 · r4，来源自证版）

> ## ⚠️ 本报告已失效（2026-10-03，同日，被鉴权修复版替换）
>
> 依据 AGENTS.md §4.6：本报告绑定的 eduai jar 是
> `75e228fc6e068ad6070181d6f02da7607335d7f01495d26e04a6472cd3b2865b`，
> 其后线上产物被替换为 `563dce9195bc865f…`（修复 dimension 管理端越权）。
> **报告证明的二进制已不在线上**，且该 jar **含越权漏洞**，不得作为签收依据。
> 已由 `ACCEPTANCE-REPORT-20261003-r5.md` 接替。
> 复核：`python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

> 本报告按 `RELEASE-CHECKLIST.md` 执行，**报告与产物 sha256 绑定**：
> 下列指纹在「部署后」与「验收后」两次采集中**逐字相同**；任何一次换包都会让本报告作废。
> 本份是**来源自证通过**的版本 —— 线上现役 jar 的 sha256 与开发机从 HEAD 构建出的 jar **完全一致**。
> 复核命令：`python3 report-staleness.py --live-eduai <线上 sha> --live-oauth <线上 sha>`。

## 0. 本次为何能自证

执行时满足 `RELEASE-CHECKLIST §1` 的两个前置：

1. **交付面干净**：`git status --porcelain`（eduai + oauth-client）为空；
2. **构建即交付**：产物由 `deploy/package.ps1` 从当时的 HEAD 现场构建，
   把该 jar 的 sha256 作为 `EXPECTED_JAR_SHA` 传给 `release-run.sh`；
   发布脚本在部署后**断言线上 jar == 该 sha**，不一致立即 `PROVENANCE_FAIL` 中止。

> 这是继 r3（2026-10-02，绑定 `81d7c0e1`）之后**第二次**成功自证。
> r3 绑定的 jar 随后被并发会话替换为 `e7ee42b8`，故 r3 已按 §4.6 失效；
> 本次重新构建并部署，绑定到新的现役 `75e228fc`。

## 1. 产物与源码绑定

```
源码 HEAD（构建时）    = 2bc2fe52b3644cc8d4ac4348db9638378233babf
工作区（eduai/oauth）  = 干净（git status --porcelain 为空）
本地构建 jar sha256    = 75e228fc6e068ad6070181d6f02da7607335d7f01495d26e04a6472cd3b2865b
发布包 bundle sha256   = c76c00a00775e2f78d3091e27fc5aa715a06f65bf3a7c515d4c2164f43b16904（764MB）
部署后线上 jar sha256  = 75e228fc6e068ad6070181d6f02da7607335d7f01495d26e04a6472cd3b2865b
判定                   = PROVENANCE_OK（线上 == 本地构建）
部署前线上 jar（被替换）= e7ee42b8c8d21a9da7edfa733c51556a3dd15ca8f30ab5e9fa76b27ecd7944b6
```

## 2. 线上指纹（部署后 == 验收后，逐字相同）

```
eduai jar     sha256 = 75e228fc6e068ad6070181d6f02da7607335d7f01495d26e04a6472cd3b2865b
oauth jar     sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
eduai unit    md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit    md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh     md5    = 2419490ef43ce052cc1805710c812255
eduai 表数           = 105
oauth 表数           = 72
健康                 = eduai 200 / oauth 200
```

> **deploy.sh md5 由 `99a83897` 变为 `2419490e` 说明**：本会话为 deploy.sh 新增了
> 5.5 步「清理旧回滚包」（防无限增长吃满磁盘）。本次运行执行的仍是**上一版**
> deploy.sh（`$APP_DIR/deploy/deploy.sh` 在新脚本被装入前就已被调用），
> 新版在部署第 3 步装入 → **5.5 步于下次部署首次生效**。详见 §9。

## 3. 生产级 API 验收（`src/acceptance/acceptance.py`）

```
===== 生产级验收: 通过 49 / 失败 0 =====
```

覆盖：登录取令牌 4 项（管理员 / 学号 / 家长手机号 / 代入）、A 安全矩阵 17 项、
B 管理端功能 15 项、C 学生端 3 项、D 玩法闭环 2 项、E 边界输入 6 项（一律不得 500）、
F 并发与延迟 2 项。关键项：A8 代入令牌写学生端被拒（只读）、A12 代入令牌不得续期、
E1 SQL 注入不 500、E5 学号特殊字符不 500。

## 4. 并发与延迟

```
F1 20 并发 × 3 批 全部成功（60/60）
延迟 p50=91ms  p95=134ms  max=148ms
```

## 5. 故障注入（oauth 停机）

```
基线             : ok=True token_len=178
停 oauth-server  : 耗时=2540ms  ok=False token_len=0  eduai健康=200  restarts=0
恢复后(oauth=200): ok=True token_len=178
```

判定：oauth 不可用时**快速失败**（≈2.5s，非挂起），eduai 自身**不崩、不重启**，
oauth 恢复后登录立即恢复 —— 依赖隔离成立。（恢复判据在 oauth health 200 后断言。）

## 6. 资源判据（`RELEASE-CHECKLIST §7`）

```
available=2712MB (≥1536)   swap=0% (<80)   tmpfs=21% (<80)   oom-kill=0 (=0)   java=9
```

## 7. 传输校验（AGENTS.md §4.8）

```
bundle        sha256  VERIFIED
deploy.sh     md5     VERIFIED (2419490ef43ce052cc1805710c812255)
release-run.sh md5    VERIFIED (41254607fd088f1f4b91bd9834d46f01)
```

## 8. 报告失效核查

```
旧报告 20261001 / 20261002 / 20261002-r2 / 20261002-r3 均已失效（换包）
本报告（20261003）eduai=75e228fc  oauth=ca4ce0b3  -> VALID
```

## 9. 本次同期完成的周边修复（不影响 jar 绑定，但属交付环境）

本报告窗口前后，另修了共享主机 198 上几处会间接影响交付的问题，均已入仓：

| 问题 | 处置 | 提交 |
|---|---|---|
| `logrotate` 全局失败（collect 侧重复条目 + `su` 属主不符） | 入 `collect-starter/deploy/logrotate/`，已应用主机 | api `a1b1166b` |
| `collect-exec/sched` 的 `StandardOutput` 落到隐藏 `.log`（两节点共用） | 入 `collect-starter/deploy/systemd/`，已 `daemon-reload` | api `2e05cf8f` |
| 日志轮转 | — | — |
| **磁盘被 26 份旧 jar 回滚包（21G）吃满（/ 66%→72%）** | deploy.sh 加 5.5 保留策略；主机已清 20 份，`/` 72%→51% | `94619aaad` |
| `dedupe-identical-jars.sh` 排除示例是子串会自排除全部候选 | 改为锚定写法 | `2bc2fe52b` |

## 10. 结论

| 维度 | 判据 | 结果 |
|---|---|---|
| 来源自证 | 线上 jar sha256 == 开发机构建 sha256 | **PROVENANCE_OK** |
| 功能/安全 | API 49/49 | **PASS** |
| 并发/延迟 | 60/60，p95=134ms | **PASS** |
| 依赖隔离 | oauth 停机快速失败、eduai 不崩 | **PASS** |
| 资源 | 四条判据 | **PASS** |
| 交付面 | 工作区干净、改动在 HEAD | **PASS** |

**可签收。** 本报告绑定的线上二进制为
`75e228fc6e068ad6070181d6f02da7607335d7f01495d26e04a6472cd3b2865b`；
换包即失效，签收前须以 `report-staleness.py` 复核。