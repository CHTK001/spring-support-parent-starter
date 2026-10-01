# eduai 终验报告（2026-10-01）

> 按 `RELEASE-CHECKLIST.md` 执行。**报告与产物 md5 绑定**：下列指纹在本次验收的
> 「前 / 后」两次采集中**逐字相同**；任何一次换包都会让本报告作废。

## 1. 产物指纹（验收前 = 验收后）

```
eduai jar     sha256 = 9a93b672fec1380cec3e935ca5318858
oauth jar     sha256 = ca4ce0b3d929c96e6f607950c24ac235
eduai unit    md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit    md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh     md5    = 99a838974044416fc9e55aeeaf8e268f
```

## 2. 源码

```
spring-support-parent-starter HEAD = 645e1c028ba010665a8f4dccf8d24fbd93ac3d4b
spring-support-api-starter  HEAD = ad354de6a835f6accb6fba954b5e7368d834494c
本交付面（eduai + oauth-client）未提交改动 = 0
```

> 全仓另有 3 个**未跟踪**文件，属 `spring-support-ai-starter/src/acceptance/`
> （他人本轮新建），**不在本交付面内**。

## 3. 库

```
eduai 表数 = 105      oauth 表数 = 72
迁移记账未成功脚本 = V1.1__initdata_all_eduai.sql(false)
```

关于该条：eduai 用 `data-scan-mode: ONCE`，库非空时 initdata 直接跳过，
这条记录因此不会被重试。本轮已**手工幂等补跑**（`INSERT IGNORE`，
唯一键 `uk_edu_question_stem`，实测重复题干数 = 0），`edu_question` 120 → 154。
记录本身**保持 false**（框架确实没成功执行过它，如实保留）；`deploy.sh` 的
记账上报会持续提示它，供后续人工确认。

## 4. 结果

| 项 | 结果 |
|---|---|
| API 生产级验收 | **通过 49 / 失败 0**（退出码 0）|
| 并发 | 20 × 3 = **60/60**；p50 **93ms** / p95 **140ms** / max 146ms |
| 故障注入 | 停 oauth → **2537ms** 返回 `ok=false`；eduai **健康 200、restarts 0**；恢复后 `ok=true` |
| 指纹复核 | 前 = 后，**逐字相同** |

## 5. 必须写进交付说明的两条事实（非阻断，但不得省略）

1. **运行产物的来源不可自证**。`eduai jar = 9a93b672` 由**并发会话**构建；
   本轮期间它先后是 `a27b3ccf` → `399da04b` → `9a93b672`（换了 3 次）。
   本报告能证明的是「**交付面已提交** + **验收绑定的就是这个产物、且窗口内未被替换**」，
   **不能**证明「该 jar ≡ 源码 HEAD 的构建」。若要严格自证，须在冻结窗口内
   由发布者用 `deploy/package.sh` 重建后再跑一次本报告。
2. **资源判据需放行或豁免**：本次实测 `available ≈ 2.9 GB`、`swap 845/953`、
   java 进程 12 个；`RELEASE-CHECKLIST.md` §7 的三条判据需由发布决定人给结论。

## 6. 结论

以「功能 / 安全验收通过 + 部署可复现」为口径：**本快照可交付**。
以「生产级签收」为口径：还需 ① 冻结窗口内由发布者重建一次以自证来源；
② 对 §5.2 的资源判据给出放行或书面豁免。
