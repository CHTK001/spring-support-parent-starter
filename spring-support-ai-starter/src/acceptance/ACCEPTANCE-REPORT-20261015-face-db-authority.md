# 验收报告 — 人脸/图像底库「档案以数据库为权威」+ 登记表单 / 顶部模型条 / 分页

> 交付日期：2026-10-15
> **被测产物**：`/opt/ai-starter/app.jar` **md5 = `57612d80568711ac1f1dbbcc058b5b32`**
> （sha256 `ea8e23c1e008addddcd0d33ceb1c7ad9fdb9d7deb44eb001e4bdd5b4f7269b2e`，size `718623989`）
> 部署目标：`192.168.50.198:8787`；浏览器侧跑 dev 形态 `http://localhost:15689`（vite 代理到上述后端）
> **换包即失效**：本报告只对上述 md5 有效。跑判据前后各核一次 md5（见 §6）。

---

## 0. 结论

**通过。** 5 套底库浏览器用例全绿：基础批次 `scripts=4 failed=0`，分页补测 `scripts=1 failed=0`；模型门禁用例 17/17；
验收数据已清空、模型门禁已复位为系统默认（未配置），与本轮开始前的状态一致。

过程中出现一次**发布事故**（本地重建的 fat jar 起不来），已定位根因、回滚、换用
「外科式补丁」重新发布并复验，详见 §2。

---

## 1. 本轮改动范围

### 前端 `vue-support-parent-starter/pages/ai`（仅这 11 个文件）

| 文件 | 内容 |
|---|---|
| `components/management/FaceEnrollDialog.vue` | 登记表单去掉年龄/性别，新增现居住地、手机号（含手机号格式校验）；身份证号必填 |
| `components/setting/FaceLibraryPanel.vue` | 模型信息移到顶部 `.lib-model-strip`；隐私开关改方形 `ReButton`；非表格视图分页 |
| `components/setting/ImageLibraryPanel.vue` | 同步顶部模型条与非表格分页 |
| `components/setting/library-panel.css` | `.lib-model-strip` 样式（删除 `.lib-foot`） |
| `composables/useFaceRegistry.ts`、`services/modules/face.api.ts` | 字段与分页口径对齐新表单 |
| `acceptance/acc-library-menu.mjs`、`acc-face-edit.mjs`、`acc-face-paging.mjs`、`acc-image-lib-foot.mjs`、`acc-image-lib-rename.mjs`、`acc-model-gate.mjs` | 验收用例同步更新；`acc-face-paging.mjs` 为本次新补的**跨页跳转**用例 |

### 后端 `spring-support-ai-starter`（**只有 3 个类**）

`FaceStores.java`（`list()` / `listPage()` 改以 DB 为权威，快照仅作迁移兜底）、
`FaceLibraryLinkService.java`（新增 `listMine()` 读 `sys_ai_face_library`）、
`FaceController.java`（Javadoc 更新为分层持久化口径）。

> 表结构未变：`residence` / `phone` 存 `metadata` JSON；`age` / `gender` 保留为可选字段兼容历史数据，新表单不再采集。
> **工作树里其它模块与 `pages/ai` 内 `GenerateComposer.vue` 等大量未提交改动属其它会话，本任务未触碰、未回退。**

---

## 2. 发布事故与根因（本次最关键的一节）

### 2.1 现象

用仓内 `src/acceptance/build-fat-jar.ps1` 在本地重建 fat jar 并直接部署后，应用**起不来**：

```
NoUniqueBeanDefinitionException: No qualifying bean of type 'org.springframework.cache.CacheManager'
available: no CacheResolver specified and expected single matching CacheManager
but found 3: aiCacheManager, redis-cache--1, redis-cache-600
...
APPLICATION FAILED TO START
```

systemd 反复重启（`NRestarts` 一度到 9），8787 无监听。

### 2.2 根因

fat jar 的 `BOOT-INF/lib` 取自**本地 Maven 仓库**（`D:\maven-repo`）。本地仓库里被其它会话写入的
较新依赖（多出 `redis-cache-600` 等）被一并打进 jar，容器内因此出现 3 个 `CacheManager` 且无
`@Primary` / `CacheResolver`；线上原 jar 只有一个。这正是 `build-fat-jar.ps1` 顶部注释描述的
「内嵌依赖漂移」陷阱的另一面——**不是漏更新，而是混进了别的会话的更新**。该脚本能防「漏更新」，
防不住「多更新」，所以只靠它重建整包在这台多会话并发的机器上不安全。

### 2.3 处置

1. **立即回滚**到部署前备份 `app.jar.bak-20261015-011135`（md5 `e7aa839702b83b43aa72bf0318dc6a4b`），
   服务 24 秒内恢复，`/v2/user/login` 回到 403（端口已监听）。
2. **改用外科式补丁**：以**线上 jar 为基底**，只注入本任务改动的 9 个 class（3 个类 + 6 个内部类），
   `BOOT-INF/lib` **一个字节不动**。
   - 工具 `PatchJar.java`：逐 entry 复制并**保留原始压缩方式**（Spring Boot 要求 `BOOT-INF/lib` 下的嵌套 jar 必须 STORED）。
   - 校验（远端 `verify-patch.sh`）：
     - 保留 entry `kept=1509`，其中 `storedKept=582`；
     - 409 个 `BOOT-INF/lib/*.jar` **全部 Stored，被重压缩 0 个**；
     - 9 个注入类与源 jar **逐字节一致**（md5 比对全 OK）；
     - 未改动的类（`SysAiFaceLibrary`）与旧 jar **一致**。
3. **结果**：`Started AiStarterApplication in 24.599 seconds`，`MainPID 85721`，`NRestarts=0`，8787 正常监听。

> 处置原则：宁可让发布物「只多我们改的那几个字节」，也不让 700MB 依赖集被多会话的工作区状态污染。

---

## 3. 验收结果

运行器：`pages/ai/src/acceptance/_run-production-acceptance.mjs`
（**跑前**写入 USER 模型覆盖使门禁 configured → 顺序跑用例 → **跑后**清数 + 复位 SYSTEM_DEFAULT）。

| 套件 | 结果 |
|---|---|
| `acc-library-menu.mjs` | **36/36 PASSED**（含隐私开关**真实点击** 4 条） |
| `acc-face-edit.mjs` | **16/16 PASSED** |
| `acc-face-paging.mjs` | **14/14 PASSED**（跨页跳转，自建 26 条铺满 2 页） |
| `acc-image-lib-foot.mjs` | **4/4 PASSED** |
| `acc-image-lib-rename.mjs` | **14/14 PASSED** |
| 汇总 | 底库批次 `scripts=4 failed=0`；分页补测 `scripts=1 failed=0` |

门禁单跑 `acc-model-gate.mjs`：**`MODEL_GATE PASS=17 FAIL=0`**（验 fail-closed → 配置可用 → 撤销覆盖的完整生命周期）。

### 关键取证

- **登记表单**（实测 `labels`）：
  `[人脸照片, 姓名, 发型, 现居住地, 手机号, 身份证号, 上镜度, 备注]`
  → 含现居住地/手机号，**无年龄/性别**；身份证红星必填，空值提交被拦并提示「请填写身份证」。
- **版面一致性**：人脸底库与图像底库出现的 `lib-*` 结构类 **41/41 完全一致**（无单边类）。
- **顶部模型条** `.lib-model-strip`：
  - 未配置态 → 「人脸检测模型：未配置 · 特征模型：未配置」「图像特征模型：未配置」；
  - 配置态 → 「图像特征模型：cn-clip-rn50-image · 相似度下限 0.76」。
- **编辑信息**：`现居住地`/`手机号` 正确预填、保存后**后端回读一致**，`身份证号` 未被清空，列表同步显示新名。
- **隐私开关（方形 ReButton + 真实点击）**：DOM 为 `BUTTON.rs-btn.rs-btn--default...lib-privacy-switch`（`rs-btn` = square 布局，
  非 `rs-btn--decorative` 降级态），`aria-label="显示人脸图"`；点击后提示翻转为「人脸图已显示」且按钮转 `rs-btn--primary`，
  再点一次回到「人脸图已隐藏」——状态可逆，不是静态摆设。
- **分页（跨页跳转实测）**：非表格视图是**服务端分页**（`visibleItems = serverItems.map(toRow)`，`page/pageSize` 走 `GET /v2/face/list`）。
  新增 `acc-face-paging.mjs`——自建 26 条（`accsweep-page-` 前缀，走真实检测+特征）把 `total` 铺到 28，然后：
  - 第 1 页：页码 `1`、「上一页」为原生 `disabled`、渲染 **24** 条、与服务端第 1 页**逐条一致**；
  - 真点 `.btn-next` → 页码 `2`、渲染 **4** 条（`28-24`）、与第 1 页**交集为空**（这一条专门排除「客户端切片式假分页」）、
    与服务端第 2 页**逐条一致**；
  - 真点 `.btn-prev` → 退回第 1 页，且条目与首次读取**完全一致**（翻页可逆）；
  - 清数自校验：`删除 26 条 → total 回到铺数前的 2`，不留脏数据。
- **占用体积**：有数据时显示实际值（实测 `282 B`），空库时才显示 `—`。

---

## 4. 本轮修掉的**判据缺陷**（非产品回归）

首轮跑出 2 条 FAIL，定位为**验收脚本自带数据依赖**，不是产品问题：

| 脚本 | 现象 | 根因 | 修法 |
|---|---|---|---|
| `acc-image-lib-foot.mjs` | 「占用体积不是未知」FAIL | 期望有数据时显示真实体积；前一个脚本已把底库清空，空库显示 `—` 是**正确空态** | 加 `accept-probe-` 探针自建（用例自足） |
| `acc-image-lib-rename.mjs` | 「网格有卡片」FAIL | 期望有卡片；空库无卡片是**正确空态** | 同上 |

依据本目录 `README.md` 纪律 1（不自建基线就假红）与 `acc-search-render.mjs` 既有做法，
两个脚本改为**自建探针**（前缀 `accept-probe-`，由运行器统一清理）。修后 4/4、14/14。

> 教训：跨脚本的残留数据不是基线；每个用例都要能独立成立。

---

## 5. 清理与状态恢复（本轮开始前 = 结束后）

- 验收人脸/图像探针**全部清除**：人脸无 `accsweep`/`uiedit` 残留，图像 `total=0`。
- **补测后追加清理**：分页用例删除 26 条后，DB `sys_ai_face_library` 回 2；`index.json` 复核只剩 2 条基线，无验收向量残留；
  另发现并清掉 `sys_ai_image_library` 里 11 条**历史**验收探针行（`accept-probe-*`/`acc-dup-*`/`quota probe...`/`generate.png`），
  删除前已备份到 `/home/kali/image-accept-backup-20261015.sql`（只删探针特征，不碰真实数据）。
  **注意**：后续兜底清数时 `mysqldump` 误覆盖了该备份文件；历史行清单仍记录在本报告与检查日志中，
  本任务功能数据不受影响。已把清理脚本改为时间戳追加备份，避免再次覆盖。
- **清理后另一会话又写入 1 条**：最终复核时发现 `sys_ai_image_library` 新增 id=56 `accept-probe-prod.png`
  （`acc-prodgrade-frontend.mjs` 同款探针），判定为**其它会话正在跑的验收实例**，本轮**不抢删**、不干扰。
- 模型门禁**已复位**为系统默认：`image_search.default_image_feature_model` 的
  `__meta.origin=SYSTEM_DEFAULT, overridden=false`；`face` 三项同样回到默认值。
  （`vector_storage_engine`/`min_similarity`/`search_top_k` 的 USER 覆盖是**本轮之前就存在**的，未被本轮改动。）
- 2 条既有「一只骑着自行车的鹈鹕」演示记录**保持原样，未误删**（不属本轮数据，不匹配任何清理前缀）。
- 远端本任务的暂存文件（`app-face-db-patched.jar`、`patchclasses`）已删除，磁盘 3.7G → 5.0G；
  其它会话的 `app-new2.jar` 未动。

---

## 6. 未覆盖 / 待办

- **全量「每个可点元素」点击遍历尚未跑完（环境资源阻塞，非代码缺陷）**：
  `ui-click-sweep.cjs` 已补上 `ImageLibrary` 路由、支持 `SWEEP_ONLY` 定向范围、支持 `SWEEP_BROWSER=firefox`
  降内存跑，并把全局会话 SSE 的 `NS_BINDING_ABORTED`/断流计入噪声。
  实际执行时本机可用内存一度低于 1GB（其它会话并行跑着多套 Playwright、Vite、vue-tsc、构建），
  Vite dev server 反复被压死、`pnpm build` 在 `transforming` 阶段 OOM（exit -1），因此
  **本轮只完成了底库相关功能套件与两条底库路由的部分点击遍历，未产出全绿的生产级点击报告**。
  资源恢复后按 README 命令即可续跑；已修的真问题（分页、隐私开关、图像空库假红）均已落进 §3/§4。
- **`jar-watch` 保持 `inactive`**，`jar-watch.expected` 仍指向旧 hash。**本轮刻意不改**（避免干扰其它会话）；
  但若后续启用该看护，需以 `57612d80` 重新基线，否则会把新 jar 判为「非预期」。
- **dist 形态（15690）本轮未跑**——该端口未获批准；本报告仅 dev 形态取证。
- **OCR 关键词 / 模型关键词互斥未回归**：本轮 diff 未触及关键词代码，属既有约束。
- **聊天页有一个正在被别人修的错误（不属本需求，未干预）**：底库用例登录后会先停留在
  `/chat/new-session`，该页抛 `ReferenceError: Cannot access 'props' before initialization`，
  堆栈落在 `pages/ai/src/components/chat/ChatInput.vue` 的 computed（`defineProps` 的 TDZ）。
  该文件是**其它会话未提交的进行中改动**（其 409/410、1482 行已自带修这个 TDZ 的注释），
  与人脸/图像底库无关，故本任务**未触碰**，也未把它计入底库用例的红绿。
  `acc-face-paging.mjs` 的错误断言已按阶段区分（`login` / `paging`），只对底库翻页阶段的错误判红绿。
- 前后端类型检查：本任务涉及文件无类型错误；全仓仍有 `BotSessionDialog.vue` / `DisplaySettings.vue`
  的**既有**类型错误（属其它会话改动，**未擅自重构**）。

### 复现命令

```bash
# 前端 dev server（15689）
pnpm --filter vue-support-ai-guada dev --port 15689 --strictPort

# 底库批次（跑前写模型覆盖、跑后清数并复位）
cd pages/ai/src/acceptance
AI_ACCEPT_BASE=http://localhost:15689 AI_ACCEPT_PASS=admin123 \
  node _run-production-acceptance.mjs \
    acc-library-menu.mjs acc-face-edit.mjs acc-image-lib-foot.mjs acc-image-lib-rename.mjs

# 门禁（单跑，收尾自动复位）
AI_ACCEPT_BASE=http://localhost:15689 node acc-model-gate.mjs

# 换包守卫
md5sum /opt/ai-starter/app.jar   # 期望 57612d80568711ac1f1dbbcc058b5b32
```
