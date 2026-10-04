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
| `verify-effective.sh` | 「用户改自己的默认模型后**立即生效**」的取证：事件是否发布、重建是否按账号绑定、共享单例是否被误重建 |
| `cleanup-final-state.sh` | 测试残留清理 + 线上最终状态核对 |
| `验收报告-20261004.md` | 绑定 md5 `97804124` 的验收报告 |
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
bash verify-effective.sh            # 期望「人脸设置变更（用户级）」+ 只有 ops 未配置 + 共享单例重建增量 0
```

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
  （2026-10-04 实测：报告绑 `f3bd9c5b`，而现役已过五代到 `97804124`）。
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

## 环境与工具链的坑（都实测过）

| 坑 | 事实与对策 |
|---|---|
| 应用日志位置 | 在 **`/opt/ai-starter/app-out.log`**（systemd `StandardOutput=append`），**不在 journalctl**。按 journalctl 取日志会得到**全空的证据段落却照样跑完** —— 空证据不是通过。 |
| 日志文件含 NUL/ANSI | 裸 `grep` 会报 `binary file matches` 并**停止输出** → 假空结果。**必须 `grep -a`**。 |
| 按时间过滤日志 | 日志自带 `-04:00` 时区，而本机系统时区**重启前后变过**（UTC+8 → EDT），按本地时间比对会错位 12 小时。**改用行号水位线**（`verify-effective.sh` 的做法）。 |
| `/tmp` 会被清空 | 机器重启后 `/tmp` 全空，`bash /tmp/xx.sh` 报 `No such file or directory`。**上传后先确认存在**，且**不要把过滤后的空输出当成"跑完了"** —— 失败信息也会被过滤器吃掉，务必**无条件打印末尾几行**。 |
| 输出过滤吃掉失败 | 实测：`| Select-String 'PASS=|PRODGRADE_DONE'` 把 `No such file or directory` 全滤掉，看起来是"无输出"。 |
| 共享实例串号 | 默认模型按用户隔离后，**共享单例**（`FaceClientManager` 的检测器/分类器、`OcrClientManager`、`RagClientManager` 的嵌入实例）只承载**全局**配置；重建时若不绑定身份，会把触发者的配置烘进去串掉其他用户。按账号构建的一方（`FaceStores`）才该响应用户级写入。 |
| 并发部署 | 本服务常有多会话并发部署/重启。跑重启类用例前先确认没有并发部署在飞，否则会互相打断，表现为「服务反复起停」。 |

## 已知未通过/已记录的缺口

- `POST /v2/face/super-resolution` 依赖的默认回退模型权重未上传 → 该端点可能 500，
  第 9 组会据此报警（**不是用例缺陷**，属已记录的产品缺口）。
- 产物侧 `dist/app.yaml` 缺失（落在 `dist/apps/<app>/app.yaml`）→ 运行时取不到配置、
  品牌名退化。属打包侧问题，详见 `验收报告-20261004.md` §7.1。
