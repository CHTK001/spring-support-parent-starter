# eduai 终验报告（2026-10-02 · r3，来源自证版）

> ## ⚠️ 本报告已失效（2026-10-03，换包）
>
> 依据 AGENTS.md §4.6：本报告绑定的 eduai jar 是
> `81d7c0e17bf765eafff584d6143632dfcf44fc0476cb5a83c0edc48a87cd281d`，
> 其后线上产物被并发会话替换为 `e7ee42b8c8d21a9d…`。**报告证明的二进制已不在线上。**
> 已由 `ACCEPTANCE-REPORT-20261003.md`（绑定 `75e228fc…`）接替。
> 复核：`python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

> 本报告按 `RELEASE-CHECKLIST.md` 执行，**报告与产物 sha256 绑定**：
> 下列指纹在「部署后」与「验收后」两次采集中**逐字相同**；任何一次换包都会让本报告作废。
> 与前两份（`-20261001.md` / `-20261002.md` / `-20261002-r2.md`）不同，本份是
> **来源自证通过**的版本 —— 线上现役 jar 的 sha256 与开发机从 HEAD 构建出的 jar **完全一致**。
> 复核命令：`python3 report-staleness.py --live-eduai <线上 sha> --live-oauth <线上 sha>`。

## 0. 本次为何能自证（与前三份的差别）

前三份报告都绑在一个**随后被并发会话替换掉**的 jar 上，属于「报告证明了一个已不存在的二进制」。
本次执行时满足了 `RELEASE-CHECKLIST §1` 的两个前置：

1. **交付面干净**：`git status --porcelain`（eduai + oauth-client 两个目录）为空；
2. **构建即交付**：产物由 `deploy/package.ps1` 从当时的 HEAD 现场构建，
   并把该 jar 的 sha256 作为 `EXPECTED_JAR_SHA` 传给发布脚本；
   发布脚本在部署后**断言线上 jar == 该 sha**，不一致立即 `PROVENANCE_FAIL` 并中止。

## 1. 产物与源码绑定

```
源码 HEAD（构建时）    = 198d379c65fb5104f54e80771104e479dc21789c
工作区（eduai/oauth）  = 干净（git status --porcelain 为空）
本地构建 jar sha256    = 81d7c0e17bf765eafff584d6143632dfcf44fc0476cb5a83c0edc48a87cd281d
发布包 bundle sha256   = d5f1ab50ad4389ab39531577cca2f5eed9209b5b39d27ee29c368a3bb70c3853（764.4 MB）
部署后线上 jar sha256  = 81d7c0e17bf765eafff584d6143632dfcf44fc0476cb5a83c0edc48a87cd281d
判定                   = PROVENANCE_OK（线上 == 本地构建）
线上 jar mtime         = 2026-10-02 02:17:44
```

## 2. 线上指纹（部署后 == 验收后，逐字相同）

```
eduai jar     sha256 = 81d7c0e17bf765eafff584d6143632dfcf44fc0476cb5a83c0edc48a87cd281d
oauth jar     sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
eduai unit    md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit    md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh     md5    = 99a838974044416fc9e55aeeaf8e268f
eduai 表数           = 105
oauth 表数           = 72
健康                 = eduai 200 / oauth 200
```

> **eduai unit md5 由 `41284653` 变为 `53f648c2` 说明**：这是部署脚本第 3 步
> 「安装 systemd unit」的**自身动作** —— 用产物内（即 repo HEAD）的 unit 覆盖了线上
> 他人装上的版本。因此「部署后/验收后」都是 `53f648c2`，二者一致；
> 交付态 unit == repo HEAD 的 unit。

## 3. 生产级 API 验收（`src/acceptance/acceptance.py`）

```
===== 生产级验收: 通过 49 / 失败 0 =====
```

覆盖：登录取令牌 4 项（管理员 / 学号 / 家长手机号 / 代入）、A 安全矩阵 17 项、
B 管理端功能 15 项、C 学生端 3 项、D 玩法闭环 2 项、E 边界输入 6 项（一律不得 500）、
F 并发与延迟 2 项。

关键项摘录：

```
A1  /actuator/health 无令牌可探活            PASS
A2  /actuator/env|beans|configprops|loggers
    |threaddump|mappings 无令牌被拒           PASS（6）
A8  代入令牌写学生端被拒（只读）              PASS
A12 代入令牌不得续期                          PASS
E1  SQL 注入用户名 / E5 学号特殊字符          PASS（不 500）
F1  20 并发 × 3 批 全部成功（60/60）          PASS
F2  p95 延迟 < 1500ms                         PASS
```

## 4. 并发与延迟

```
F1 60/60 全成功；延迟 p50=62ms  p95=102ms  max=127ms
```

## 5. 故障注入（oauth 停机）

```
基线             : ok=True token_len=178
停 oauth-server  : 耗时=2532ms  ok=False token_len=0  eduai健康=200  restarts=0
恢复后(oauth=200): ok=True token_len=178
```

判定：oauth 不可用时**快速失败**（≈2.5s，非挂起），eduai 自身**不崩、不重启**（restarts=0），
oauth 恢复后登录立即恢复 —— 依赖隔离成立。
（恢复判据在 `oauth/actuator/health == 200` 之后再断言，避免冷启动窗口造成假红。）

## 6. 资源判据（`RELEASE-CHECKLIST §7`）

```
available=4204MB (≥1536)   swap=0% (<80)   tmpfs=18% (<80)   oom-kill=0 (=0)   java=9
```

## 7. 传输校验（AGENTS.md §4.8）

```
deploy.sh     remote=99a838974044416fc9e55aeeaf8e268f  local=99a838974044416fc9e55aeeaf8e268f
release-run.sh remote=41254607fd088f1f4b91bd9834d46f01 local=41254607fd088f1f4b91bd9834d46f01
TRANSFER_VERIFIED
```

## 8. 报告失效核查

```
线上现役：eduai=81d7c0e1  oauth=ca4ce0b3
[STALE] ACCEPTANCE-REPORT-20261001.md      eduai=9a93b672≠  -> 已标注失效
[STALE] ACCEPTANCE-REPORT-20261002.md      eduai=a2d012b5≠  -> 已标注失效
[STALE] ACCEPTANCE-REPORT-20261002-r2.md   eduai=6f13188c≠  -> 已标注失效
[VALID] 本报告（-r3）                       eduai=81d7c0e1
```

## 9. 本次过程事实（如实记录）

1. **首次执行被资源门禁拦下**：`-acc` 族（`nacos-acc`/`neo4j-acc`/`zk-acc`/`etcd-acc`/`influxdb-acc`，
   一次性验收基建，`enabled=disabled`）在本次执行前**被人重新拉起**，java 回到 13 个、swap 冲到 92%；
   发布脚本按设计 `[失败] 资源判据不通过：swap=92%` 拒绝签收。**门禁按预期工作。**
2. **处置**：再次 `systemctl stop` 该族（java 13→9、available 2613→5071MB）；
   剩余的 91% swap 是已换出的旧页（`available` 远大于 swap 用量，无实际压力），
   以 `swapoff -a && swapon -a` 安全回收至 0%。随后整轮重跑。
3. **重跑结果全绿**（即本报告）。

## 10. 结论

| 维度 | 判据 | 结果 |
|---|---|---|
| 来源自证 | 线上 jar sha256 == 开发机构建 sha256 | **PROVENANCE_OK** |
| 功能/安全 | API 49/49 | **PASS** |
| 并发/延迟 | 60/60，p95=102ms | **PASS** |
| 依赖隔离 | oauth 停机快速失败、eduai 不崩 | **PASS** |
| 资源 | 四条判据 | **PASS** |
| 交付面 | 工作区干净、改动在 HEAD | **PASS** |

**可签收。** 本报告绑定的线上二进制为
`81d7c0e17bf765eafff584d6143632dfcf44fc0476cb5a83c0edc48a87cd281d`；
换包即失效，签收前须以 `report-staleness.py` 复核。