# eduai 交付现状（单一口径的真相文件）

> 本文件回答一个问题：**现在到底交付了什么、验了什么、还差什么。**
> 与那几份「终验报告」的区别：报告是**某一次运行的快照**，会随换包失效；
> 本文件区分「随产物绑定的结论」与「不随产物绑定的结论」，并给出闭合流程。

## 1. 交付面

- `spring-support-eduai-starter`（后端）
- `spring-support-oauth-client-starter`（只读拦截修复所在）
- 依赖服务：`spring-api-support-oauth-starter`（认证服务端）

## 2. 已入库（都在 HEAD 链上）

| 提交 | 内容 |
|---|---|
| `90c1999bf` / `f6eb4a97c` | eduai 部署交付物（package/deploy/unit/env 模板/README）|
| `b0c013d2b` | 部署时幂等收敛 schema（补框架永不重跑的缺表）|
| `b599b532e` | 部署时上报「记账里未成功」的脚本 |
| `db47c4315` | 生产级验收套件 `acceptance.py` + 发布冻结清单 |
| `c0729ddda` | 清单补资源基线与放行判据 |
| `14d4a5183` | 只读身份在未标注 @Permission 的路径上不再放行（oauth-client）|
| `ad354de6` | 去掉仓库里的硬编码数据源口令（api 仓）|
| `e8eeff28` | oauth 部署交付物入仓（api 仓）|

## 3. **不随产物绑定**、当前仍成立的事实（复核于 2026-10-02）

**库侧**（与 jar 无关）：

```
eduai.sys_ai_service_instance 存在 = 1（本轮补上，105 表）
eduai.sys_database_version 里 V1.1__initdata_all_eduai.sql = false
  （框架不会重跑；种子已人工幂等补跑，edu_question 120 → 154，无重复插入）
ngxacc / monitordb 的同名表本来就有（此前的报错命中是历史遗留）
```

**行为侧**（对现役 jar 实测，非某次快照）：

```
代入令牌读学生端 = 200      代入令牌写学生端 = 403（只读生效）
admin / 学号 / 家长手机号 登录 = ok=true token=178
oauth 日志线程 = aio-tcp-iocp（真走 tcp）
停 oauth → 登录秒级失败（ok=false）、eduai 不崩（health 200、restarts 0）
```

**修复是否还在现役产物里**（按类名核对，不依赖整包摘要）：

```
现役 jar 内嵌 oauth-client 含 writeReadOnlyDenied = 1
```

**部署可复现**：oauth 与 eduai 两侧脚本均经「删库 / 删表后从零重放」验证。

## 4. 随产物绑定、因此**已失效**的报告（换包时间线）

eduai jar 在本轮被反复替换（同一台共享主机上有多会话并发构建/部署）：

```
f79f8d78 → ec3e5afe → d2c4fc45        （本会话的部署）
→ a27b3ccf → 399da04b → 9a93b672     （并发会话）
→ a2d012b5                            （本会话从 HEAD 自建，PROVENANCE_OK）
→ 186767230c → 6f13188c → 95f3db7c    （并发会话，现役）
```

于是三份报告先后失效：

| 报告 | 绑定 | 失效原因 |
|---|---|---|
| `ACCEPTANCE-REPORT-20261001.md` | `9a93b672` | 换包 |
| `ACCEPTANCE-REPORT-20261002.md` | `a2d012b5` | 换包（10 分钟内）|
| `ACCEPTANCE-REPORT-20261002-r2.md` | `6f13188c` | 换包 |

**关键澄清**：换包抹掉的是「报告与二进制的绑定」，**不是修复本身**——
已复核现役 `95f3db7c` 仍含只读修复、库侧修复仍在、行为回归通过。

## 5. 还差什么

只差**一个真正停手的时间窗**（约 10 分钟），把「来源自证」做掉：

1. 窗口内 `git status --porcelain -- <交付面>` 为空（**当前不满足**：
   `EduImagePreprocessor.java` 有未提交改动 —— 并发会话正在改 eduai）；
2. 发布者用 `deploy/package.sh` 从 HEAD 重建 → 部署 → 比对线上 jar sha256 == 本地构建产物；
3. 跑 `src/acceptance/acceptance.py`（49 项）+ 故障注入；
4. 前后各取一次指纹，逐字相同 → 出报告签收。

流程细节见 `RELEASE-CHECKLIST.md`。**在此之前，任何"全绿报告"都会在几分钟内被下一次换包作废** —— 本文件第 4 节就是三次实证。
