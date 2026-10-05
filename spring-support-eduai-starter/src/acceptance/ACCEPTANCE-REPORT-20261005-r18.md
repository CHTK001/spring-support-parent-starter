# eduai 学生端交付终验报告（2026-10-05 · r18，模型 space-bunny-alpha，产物前端 `28fcb8f1…`）

本报告绑定的产物指纹（`report-staleness.py` 按下面两行解析）：

```
eduai jar sha256 = 6f383dc865cf3b61c3579a460189780c29a870ad1aab08e0e89679d7170e2ffc
oauth jar sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
部署后线上 eduai jar md5 = ab75b7c1be567d344fbcc08b66112478
```

> 报告与产物指纹绑定；**换包即失效**。复核：
> ```bash
> python3 report-staleness.py --live-eduai \
>   6f383dc865cf3b61c3579a460189780c29a870ad1aab08e0e89679d7170e2ffc \
>   --live-oauth ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
> # 返回 [STALE] + 退出码 3 即表示本报告已作废，须重跑。
> ```
>
> 前端 `index.html` md5 **`28fcb8f1f6447eb4a3e45b4b21fd4c6a`**（`report-staleness.py`
> 只管 jar，前端另行核对）：
> ```bash
> md5sum /var/www/eduai/index.html /var/www/eduai-verify/index.html   # 两者都必须是 28fcb8f1…
> ```
>
> 编号：并发会话已占用 r12~r17，本报告用 **r18**（写前 `git ls-files` 查过占用）。
> 本轮**只改学生端 3 个视图**，管理后台与其他模块未动。

## 1. 绑定的产物与运行环境

| 项 | 值 |
|---|---|
| eduai jar | `/home/kali/eduai/backend/eduai-backend.jar` |
| **sha256** | `6f383dc865cf3b61c3579a460189780c29a870ad1aab08e0e89679d7170e2ffc` |
| **md5** | `ab75b7c1be567d344fbcc08b66112478` |
| 大小 / 落盘 | 837 224 290 B / 2026-10-05 09:01:53 |
| oauth jar sha256 | `ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470` |
| **生产前端** `/var/www/eduai` | `index.html` md5 **`28fcb8f1f6447eb4a3e45b4b21fd4c6a`**，1103 文件 |
| **验收前端** `/var/www/eduai-verify` | 同 md5，1103 文件（三份 CSS 逐字节同源，见 §6） |
| 部署包 | `eduai-web-r21.tar.gz` md5 `F3B320AD39FFB64DD6A913487C20E840`，13 747 125 B |
| chunk 拓扑门禁 | `vue-router-*.js` 计数 = **1**（19093 与 19096 都是 1） |

运行时：

| 项 | 值 |
|---|---|
| java pid | **796872**（实例数 1），启动于 Mon Oct 5 09:04:51 2026，`--server.port=19095` |
| 健康 | `{"status":"UP","groups":["liveness","readiness"]}` |
| hs_err 份数 | **4**（`170759` / `228606` / `75288` / `78102`），本轮**无新增** |
| 19093 | nginx → `/var/www/eduai`，`/v2/` 反代 `127.0.0.1:19095` |
| 19096 | nginx → `/var/www/eduai-verify`，`/v2/` 反代 `127.0.0.1:19095`（隔离验收实例） |
| 历史归档 | `/var/www/eduai.prev-20261005-{055851,074202,082433}` |

## 2. 本轮改了什么（只 3 个文件）

```
pages/eduai/src/views/portal/StudentRecordsView.vue   | 161 +++++++++-
pages/eduai/src/views/portal/StudentWrongbookView.vue |  52 ++++-
pages/eduai/src/views/portal/StudentPapersView.vue   |  61 ++++++
3 files changed, 270 insertions(+), 4 deletions(-)
```

起因是**真实数据到了之后才暴露的缺陷**：前一轮全站扫描时学生账号名下 0 条记录，
`我的答题` / `错题本` 渲染的是空态，缺陷根本不会出现在 DOM 里。跑完 OCR/阅卷全流程
（§5）后同一账号有了真答卷与真错题，扫描立刻打出 2 个 FAIL：

| 页 | 视口 | 缺陷 |
|---|---|---|
| 我的答题 | desktop / laptop-short | `el-button is-link`「查看」高 **20px** |
| 错题本 | 全部 5 档 | 三个操作按钮各高 **20px** |

20px 低于 WCAG 2.2 **2.5.8 Target Size (Minimum, AA) 的 24×24 硬底线**。同页的
`el-tag` 也是 20px，但 tag 不是可点目标，2.5.8 不适用，未动（理由写进了代码注释，
避免下一个人「顺手一起改」）。

修法：

* `StudentRecordsView.vue` —— 宽屏表格的「查看」补到 28px（鼠标设备视觉平衡），
  `@media (pointer: coarse)` 下 44px；窄屏卡片版 `.mr-card__go` 本来就有 44px。
* `StudentWrongbookView.vue` —— 三个 link 按钮不再用 link 型，改成实心小药丸
  （与 `.mr-card__go` 同一套 token），40px / coarse 下 44px，480px 以下铺开。
* `StudentPapersView.vue` —— 这页原本**一条 `@media` 都没有**，卡片是 flex row，
  390px 下卷名和 chip 被挤成竖排一两个字。补 720px（竖排 + 通栏按钮）、
  480px（收紧内边距、分值换行独占）、700px 高（矮屏压缩头部）。

## 3. 用户 6 条投诉的复验（生产 19093 实走）

`eduai-r16-verify.py`，逐条打回原形重验，**6/6 PASS**：

| # | 投诉 | 判据 | 结果 |
|---|---|---|---|
| 1 | 学生页面高度无自适应 | 5 档视口量内容高 / 溢出 / 列数 / 卡高 | 1440×900 / 1280×620 / 834×1112 / 390×844 / 844×390 全 OK，列数 4→4→3→2→3 随宽变化，溢出 0 |
| 2 | 「诗词大会」直接进后台 | 9 个模块 `?m=<moduleCode>` 深链逐个进，检查是否落在后台布局路由 | 11 模块深链全对，**后台泄露 0**；未登记的趣味数学/英语单词只给「建设中」提示 |
| 3 | 「返回首页」也进后台 | 学生首页 7×5 网格 `document.elementFromPoint` 采样 35 点，逐点看 `elementFromPoint` 返回的祖先链 | 命中 **35/35 全在 `.portal` 内**，`stage=PRIMARY`，溢出 0 |
| 4 | 风格参考成语消消乐 + 响应式 | 见 §4 全站扫描 | 60 组合 0 缺陷 |
| 5 | 错题本等数字徽章造假 | 直接读徽章列表 | 实测徽章数组 `[]`（无真实数据时返回 0，不渲染） |
| 6 | 加载中显示「修改密码」原生按钮 | 在 loading 帧截图文本 + 找裸原生 button | 裸原生按钮 **无**，该帧文字为「加载中…」 |

登录失败文案不回显后端原始报文：用 Playwright `page.route` 在浏览器侧造 502
（不在生产改 admin 密码做复现），页面显示「登录失败，请检查网络后重试…」，
**泄露命中：无**；口令错误场景显示「密码错误，还剩4次尝试机会」，泄露命中同样为无。

## 4. 全站响应式扫描（12 页 × 5 档视口 = 60 组合）

`eduai-r17-sweep.py`，判据不是「看起来没坏」，而是量出来的：

* **横溢**：`scrollWidth > clientWidth`，且排除可滚动祖先内的正常内容。
* **裁切**：需 `overflow != visible` **且**带文字/图标子元素真越出容器边界。
* **幽灵滚**：容器能滚却只多出 < 24px（用户滚了半天什么都没发生）。
* **触摸目标**：按 WCAG 2.2 判 —— 2.5.8 (AA) 只要 24×24；44×44 是 2.5.5 (AAA)，
  24~44 之间只记 warning 不算缺陷。（上一版一刀切 44，在 1440 桌面视口上把 30px
  的筛选 chip 全判成缺陷，那是鼠标驱动的桌面，44px 要求不成立。）
* **首屏可见率**：主要内容的采样点可见比例。

结果：**60 组合 0 缺陷**，横溢 0、裁切 0、幽灵滚 0。全站最小触目标 24px，
**无任何元素低于 24px**。

提醒项（不算缺陷，如实列出）：

| 元素 | 尺寸 | 说明 |
|---|---|---|
| 成语消消乐 chip ×9 | 24~36px | 用户明确要求保留成语消消乐外观，达 AA 未达 AAA |
| 昆虫图鉴格 ×28 | ≥26px | 同上，达 AA |
| 成语接龙 / 汉字识字 | 24px | 正好卡在 AA 底线 |

`1280×620` 笔记本档的学生登录页内容高 664px（1.07 屏），是有意的——登录表单
不该被压扁；其余各页在矮屏下都是 1.00 屏。

## 5. OCR / 阅卷全流程浏览器实走（`eduai-ocr-e2e.py`，EXIT=0）

用真实答题卡素材（小学语文四年级，3 页 PNG）从教师端一路走到学生端，**0 缺陷**：

| 步 | 动作 | 证据 |
|---|---|---|
| T1 | 教师登录 `admin/admin123` | 通过 |
| T2 | 打开扫描阅卷对话框 | 通过 |
| T3 | 选「四年级语文单元测试（真实答题卡形态）」+ 学号 `2026000287` + 上传 3 张 | 通过（**填学号即完成归属**，否则记 `anonymous`、学生端看不到） |
| T4 | 提交并等判分 | taskId `6e8b82803ab645bdb4491e9d5f5a1476`，**43s** 完成，总分 **65 / 15 题** |
| T5 | 教师确认批改 | 通过 |
| T6 | 学生端登录看「我的答题」 | 表格 2 行 / 卡片 2 张（含早前那次 `b2a3dc…`） |
| T7 | 窄屏 + 详情弹窗 | 390×844 与 844×390 表格不可见、卡片可见、按钮高 44、详情弹窗可开 |

T7 弹窗实测文本：`得分 65 · 共 15 题 · 已批改`，题目 9401/9402/9403 各 `5/5 与标准答案一致`。

宽窄互斥与卡片内容单独再验一遍（`eduai-r20-cards.py`）：

| 视口 | 表格可见 | 卡片可见 | 判定 | 按钮高 |
|---|---|---|---|---|
| 1440×900 | True（2 行） | False | 走 table ✓ | — |
| 834×1112 | False | True（2 张） | 走 cards ✓ | 44 |
| 390×844 | False | True（2 张） | 走 cards ✓ | 44 |
| 844×390 | False | True（2 张） | 走 cards ✓ | 44 |

每张卡片 3 个元数据字段（类型/阶段/时间）、时间戳不撑破卡片、点击均能开出详情弹窗。

窄屏走卡片、宽屏走表格用的是**两套 DOM 都渲染 + 纯 CSS 媒体查询切换**——
不引 viewport 状态，所以没有 resize 监听、没有首帧闪一下表格。

## 6. 产物内容级取证（不靠文件名，靠真实字符串）

`StudentRecordsView` 的断点是 `width<=880px`。从 760px 改到 880px 的原因：
6 列最小宽度合计 800px，加两侧 40px padding = 840px，834 与 844 两档仍会被裁。

线上产物 CSS 里的真实判据（esbuild 把 `(max-width:720px)` 改写成现代区间语法
`(width<=720px)`，所以 grep 必须两种写法都认，否则会**误报 MISS**）：

| 文件 | 媒体查询原文 | 关键判据 |
|---|---|---|
| `StudentRecordsView-kGUMlC6z.css` | `@media (width<=880px)`、`@media (pointer:coarse)` | `el-button.is-link`、`min-height:28px`、`min-height:44px` |
| `StudentWrongbookView-D7Za6NE5.css` | `@media (pointer:coarse)`、`@media (width<=480px)` | `wb-card__act`、`min-height:40px`、`min-height:44px` |
| `StudentPapersView-DKpEIzgX.css` | `@media (width<=720px)`、`(width<=480px)`、`(height<=700px)` | `flex-direction:column` |

三个 chunk 都被真实引用（每个被 `assets/*.js` 引用 2 次），不是孤儿产物。
与 r20 归档的字节数对比证明确实只增不减：`StudentRecordsView` 2427→2652、
`StudentWrongbookView` 1276→1980、`StudentPapersView` 2107→2790。
19093 与 19096 三份 CSS 逐字节同源。

## 7. 后端 6 套件（按**当前线上 jar** 重跑）

```
re-acceptance                            PASS=55   FAIL=0
re-student-password-acceptance           PASS=15   FAIL=0
re-student-manage-acceptance             PASS=20   FAIL=0
re-first-login-password-acceptance       PASS=12   FAIL=0
re-paper-assign-acceptance               PASS=40   FAIL=0
re-marking-acceptance                    PASS=11   FAIL=0
---- 6 套件合计 PASS=153 FAIL=0
```

覆盖越权与归属隔离（学生令牌重置他人密码 403）、并发竞态、边界与异常输入、
存量功能回归、迁移幂等、重启持久化。逐条 `[PASS]` 已核，**无一条 `[FAIL]`**。

## 8. ONNX / DJL 原生 SIGSEGV 修复的产物级验证

修法在源码且已提交：`051787278 fix(deeplearning): 串行化共享 ONNX/DJL 会话的推理调用`，
改了两个文件 —— `PpWordExtractorTranslator.java`、`DjlModelFactory.java`。

**关键插曲：一轮「产物里零个 synchronized」的结论是假结论，已推翻。**
那台机器上 `javap: command not found`，grep 全部静默返回空，看起来像「修法不在
产物里」。改用两条不依赖 JDK 工具的路径重验：

1. 内嵌 jar 里两个被修 class 的落盘时间 = **2026-10-05 07:57**，而同目录未改的
   `PpOcrDetTranslator.class` = 2026-10-04 07:29 —— 时间戳差异正好对应提交改的那两个文件。
2. 从**线上现役 fat jar** 里解出内嵌 starter（逐字节原样，sha256
   `3b876a00…` / `70495025…`）拉到本机，用 Corretto 21 的 `javap` 看真实签名：

```
public class ...PpWordExtractorTranslator implements ITranslator<byte[], String> {
  private synchronized void prepare() throws java.lang.Exception;
  public  synchronized java.lang.String translate(byte[]);      ← 加锁覆盖 prepare + run 整体
  public  synchronized void close();
}
public class ...DjlModelFactory implements AutoCloseable {
  public synchronized <I, O> O predict(I);
  public synchronized void close();
}
```

外层 jar 之后又被并发会话换过一次（`2c9c1d66…` → `6f383dc8…`），但两个内嵌
starter 的 sha256 **完全没变**，所以修法在新包里同样在。§7/§9 的全部结果都是按
`6f383dc8…` 重跑的。

加锁用 `synchronized` 而不是 `ThreadLocal`：虚拟线程无界，`ThreadLocal` 会为每个
虚拟线程各建一个原生 session，内存直接被吃光。

## 9. OCR 并发压测（N=16 × 3 轮 = 48 份，按当前线上 jar）

```
wall_total=400.1s  submitted=48/48  done=48  bad=0
real_q_total=144   scored_total=96
  [PASS] all 48 submits returned a taskId
  [PASS] no submit/poll error (process must stay alive)  errors=[]
  [PASS] all tasks reached stage DONE  done=48 bad=0
  [PASS] ONNX inference really ran (question-level results > 0)  real_q_total=144
  [PASS] inference produced scored items  scored_total=96
===== passed 7 / failed 0 =====
```

判据第 4 条是关键：断言推理**真的执行了**（拿到题目级结果），否则并发了个寂寞，
就是假绿灯。

前后基线：jar 指纹未变、java pid 未变、hs_err 仍 4 份（无新增崩溃）、health UP。

**关于墙钟，如实说清楚**：修法生效后的三次测量是 463.6s / 584.9s / 400.1s，
波动 46%。旧包单次测得 303.6s，据此算出的「+53%」是**单样本对比，不构成结论**，
只能说明加锁有代价、量级在几百毫秒到一秒级别，不能拿它当回归门禁。
真正的判据是 48/48 完成 + 推理真跑了 + 进程活着 + 无新 hs_err。

**红侧没复现，如实记**：拿旧包跑 48 次并发也没复现崩溃。所以本报告只声明
「绿侧成立 + 因果链与机制论证成立」（崩溃帧 `libonnxruntime.so+0xb66403`
对应 `OrtSession.run`，崩溃线程均为 ForkJoinPool 平台线程上的虚拟线程 continuation，
即真并发），**不声称红→绿**。

## 10. 本轮推翻/纠正的自己的错误

| # | 错误结论 | 根因 | 纠正 |
|---|---|---|---|
| 1 | 「线上产物里零个 synchronized，修法不在包里」 | 服务器上 `javap` 不存在，grep 静默返回空被当成「查过了，没有」 | 改用字节/时间戳 + 本机 JDK `javap` 双重取证，确认修法在包里（§8） |
| 2 | 「压测后 jar 指纹不变，结果有效」 | 结果确实有效，但**中途 jar 被换过**，绑的是旧包 `2c9c1d66…` | 发现后按新包 `6f383dc8…` 把 6 套件 + 压测**全部重跑**（§7/§9） |
| 3 | 「媒体查询没进产物」 | esbuild 把 `(max-width:720px)` 改写成 `(width<=720px)`，grep 只认旧写法 | 两种写法都认后确认断点全在；并把产物 CSS 全文拉下来人工过了一遍（§6） |
| 4 | 上一轮 60 组合 0 缺陷 | 当时学生账号 0 条记录，空态下缺陷不在 DOM 里 | 有真数据后重跑才打出 2 个 20px 触目标 FAIL（§2） |

## 11. 遗留事项（如实列出，均不阻断本轮交付判定）

| 项 | 性质 | 说明 |
|---|---|---|
| `edu_student` 默认口令 = 学号，`MUST_CHANGE=1` | **生产安全问题** | 首个登录强制改密能兜住，但库里明文默认口令本身是风险，建议改为随机初始口令 + 邮件/短信下发 |
| 采集任务 98 已停用 | 已处置 | 启用中的采集任务 0，成语 7028 条全唯一 |
| 后台 layout DOM 挂在学生端遮罩之下 | 已知残留 | Ctrl+F 能搜到后台标签栏文字，**视觉不可见**（35/35 采样点判据已覆盖）。彻底清掉要改框架侧路由策略，超出本轮范围 |
| 成语消消乐 / 昆虫图鉴 chip 24~36px | 达 AA 未达 AAA | 用户明确要求保留成语消消乐外观，44px（AAA）会让游戏网格变形 |
| `14×14` 的 `layout-tabs-bar__close` | 不在学生端 | 共享框架后台标签栏的关闭钮，挂在遮罩之下，扫描判据不含它 |
| SIGSEGV 红侧不可复现 | 客观限制 | 见 §9，只能靠崩溃栈帧 + 机制论证 |
| 登录限流 40 次 | 验收约束 | 每档视口只登录一次，扫描脚本需注意 |
| `/v2/setting/default` 返回 403 | 正常 | 非授权接口，符合预期 |

## 12. 交付判定

| 门禁 | 判据 | 结果 |
|---|---|---|
| 前端产物 | 解包校验指纹 → 两次 `mv` 原子切换 → reload | `28fcb8f1…` / 1103 文件 / `vue-router` chunk=1 / 19093 与 19096 一致 |
| 用户 6 条投诉 | 浏览器实走逐条复验 | **6/6 PASS** |
| 响应式 | 12 页 × 5 档视口，横溢/裁切/幽灵滚/触目标 | **60 组合 0 缺陷** |
| 业务闭环 | 教师扫描 → ONNX 判分 → 确认 → 学生端可见 | T1~T7 全通过，65 分 / 15 题 |
| 后端门禁 | 6 套件 | **153 断言 0 失败** |
| ONNX 并发 | N=16×3 压测 | **7/7 PASS**，48/48 完成，无新崩溃 |
| 修复入产物 | `javap` 验线上字节 | `translate`/`predict`/`close` 均 `synchronized` |
| 运行态 | health / pid / hs_err | UP / 单实例 / 仍 4 份无新增 |

**结论：学生端可交付。** 上面 11 条遗留项均已在 §11 逐条定性，其中只有
「默认口令 = 学号」需要产品侧决策，其余为已知残留或客观限制。
