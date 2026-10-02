# eduai 终验报告（2026-10-02 r2）—— 资源判据已通过

> 承接 `ACCEPTANCE-REPORT-20261002.md`（那份已标失效）。本次两处进展：
> ① 资源判据由 FAIL 转 **PASS**；② 验收在指纹稳定窗口内完成。
> 仍保留一项保留意见（来源自证，见 §4）。

## 1. 产物指纹（验收前 = 验收后，逐字相同）

```
eduai jar  sha256 = 6f13188c506754bc759e370b800a9b7a
oauth jar  sha256 = ca4ce0b3d929c96e6f607950c24ac235
```

## 2. 资源判据（`RELEASE-CHECKLIST.md` §7）—— 全部 PASS

| 判据 | 实测 | 结论 |
|---|---|---|
| available ≥ 1536 MB | **5097 MB** | PASS |
| swap < 80% | **79%** | PASS |
| tmpfs < 80% | **20%** | PASS |
| dmesg 无 oom-kill | **0** | PASS |

**怎么做到的**：目标机上有一族 `-acc` 服务，Description 原文写着
`acceptance, temp`（`nacos-acc` / `neo4j-acc` / `zk-acc` / `etcd-acc` /
`influxdb-acc`）——属一次性验收基建，跑完即应拆除。经授权停掉后：
java 进程 **13 → 9**、available **2613 → 5097 MB**、swap **100% → 79%**。

**未动**的：`ngx-backend` / `collect-*`（3 个）/ `system-api` / `ai-starter` /
`ms-monitor-platform` —— 这些是其它业务系统，不属本次交付面，停它们会破坏他人工作。

恢复命令（如需）：

```bash
sudo systemctl start nacos-acc.service neo4j-acc.service zk-acc.service \
                     etcd-acc.service influxdb-acc.service
```

## 3. 验收结果

| 项 | 结果 |
|---|---|
| API 生产级验收 | **通过 49 / 失败 0** |
| 并发 | 20 × 3 = **60/60**；p50 86ms / p95 135ms / max 144ms |
| 指纹复核 | 前 = 后，逐字相同 |

## 4. 保留意见（唯一未闭合项）

本次绑定的 `eduai jar = 6f13188c` 由**并发会话**构建（本轮该 jar 先后是
`a2d012b5` → `18676723` → `6f13188c`）。因此本报告证明的是
「**验收绑定的就是这个产物、且窗口内未被替换**」，**不证明**「该 jar ≡ HEAD 构建」。
要消掉这条保留，仍需一个**真正停手**的窗口，由发布者用 `deploy/package.sh`
重建后再跑一次。

## 5. 结论

- **功能 / 安全验收：通过**（49/49，指纹稳定）。
- **资源判据：通过**（四条全 PASS）。
- **来源自证：未达成**（环境里产物仍在被替换）。
- 即：**除「来源自证」外的全部签收条件已满足**；该项属环境纪律问题，非技术缺陷。
