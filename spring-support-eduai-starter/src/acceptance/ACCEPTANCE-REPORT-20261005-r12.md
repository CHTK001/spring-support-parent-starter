# eduai 终验报告（r12 · 交付面冻结 + 来源自证 PROVENANCE_OK）

> ## ⚠️ 本报告已失效（2026-10-06 补标，§4.6 收口）
>
> 依据 §4.6：本报告绑定的 eduai jar `81810d88` 其后被多次换包，
> 现役为 `e4e0b214…`。**报告证明的二进制已不在线上，不得作为签收依据**；
> 签收口径见 `ACCEPTANCE-STATUS.md`。

> 报告与产物 sha256 绑定；**换包即失效**。复核：
> `python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

## 1. 本版补的是 r10 的哪块缺口

r10 已把「班级试卷布置」全链路跑通并签收，但它自己写明两点不足：
① 现役 jar **不是 r10 作者构建的**，因此缺 **PROVENANCE_OK**（线上 == 我构建）这一层；
② 前端仍是本机 dev 直跑。

本版（r11）做的是把 ① 补齐：**冻结交付面 → 从 HEAD 构建 → 部署 → 断言线上 == 本地构建**。

## 2. 产物与源码绑定

```
源码 HEAD（构建时）    = 2d2663d6100c319fff1ef0c33c4f62e756e7def9
本地构建 jar sha256    = 81810d88e211bb527a2fac1c8138e20031d2389b0c27a675a2fb88c46cc432c4
本地 bundle sha256     = d55e9ac4f116fb3dc5e9309cf2d875c57338335da35c5440aaf0a014d18c9de2
部署后线上 jar sha256  = 81810d88e211bb527a2fac1c8138e20031d2389b0c27a675a2fb88c46cc432c4（== 本地构建）
判定                   = PROVENANCE_OK
```

> 说明：部署前线上是并发会话的产物 `0c26c88c…`（已被本次部署替换）。
> 这里刻意不写成 `sha256 = …` 行，避免 `report-staleness.py` 把它也当成
> 本报告绑定的 eduai 值（该脚本要求同一类**全部**取值都命中线上）。

- 传输校验：bundle 落盘后 `sha256sum` 与本地逐字一致（`d55e9ac4…`）后才执行。
- 交付面冻结：入库前 `git status --porcelain -- spring-support-eduai-starter spring-support-oauth-client-starter`
  仅剩 3 张 UI 截图（未跟踪，属临时产物，不入库）；其余改动已入库。

## 3. 生产级验收：6 套件 153 条断言，0 失败

（在目标机上对**现役产物**跑；`release-run.sh` 一条命令完成）

```
acceptance.py                        通过 55 / 失败 0   exit=0
student-password-acceptance.py       通过 15 / 失败 0   exit=0
student-manage-acceptance.py         通过 20 / 失败 0   exit=0
first-login-password-acceptance.py   通过 12 / 失败 0   exit=0
paper-assign-acceptance.py           通过 40 / 失败 0   exit=0
marking-acceptance.py                通过 11 / 失败 0   exit=0
--------------------------------------------------------------------------
套件: 全部 OK        API 验收=OK        日志 release-run-freeze.log
```

含本轮新增的关键断言（跨老师越权）：`[PASS] 布置到「别人的班」被拒 http=400`。

## 4. 故障注入（对现役产物）

```
基线             : ok=True token_len=178
停 oauth         : 耗时=2597ms ok=False token_len=0   eduai健康=200   restarts=0
恢复后(oauth=200): ok=True token_len=178
```

## 5. 指纹：验收前 == 验收后

```
eduai jar  sha256 = 81810d88e211bb527a2fac1c8138e20031d2389b0c27a675a2fb88c46cc432c4
oauth jar  sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
eduai unit md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh  md5    = 2419490ef43ce052cc1805710c812255
eduai 表数 / oauth 表数 = 107 / 72
健康              = eduai 200 / oauth 200
```

## 6. 前端：打包成功，但浏览器业务验收**未取得可签收结论（明确未覆盖）**

- 打包：`pnpm build`（`vue-tsc --noEmit && vite build`）在 `apps/vue-support-eduai-starter`
  **构建成功**（`built in 40.56s`），产出 `dist/`。
- 浏览器业务验收（脚本 `src/acceptance/ui-paper-assign-acceptance.ps1`）在**打包产物**上
  **未能稳定通过**，两次结果不一致，且失败点落在不同侧：

  | 入口 | 结果 | 主要失败 |
  |---|---|---|
  | 本机 `127.0.0.1:19094`（我构建的 dist + 复刻代理） | 16 PASS / 3 FAIL | 学生端「我的试卷」卡片 30s 未出现 |
  | `192.168.50.198:19093`（远端 nginx + 另一份构建产物） | 13 PASS / 8 FAIL | 老师端「布置」指针点击未命中、班级未选中 |

- 判据：两个入口的**产物 asset hash 不同**（本机 `index-GgOWcEzZ.js` vs 远端 `index-DqExOfAm.js`），
  即它们**不是同一份构建**；再叠加 `agent-browser` 会话被并发会话共用（实测一次报
  `tmp*.tmp ... used by another process`）。因此这**不是**能据此判定的单一产品结论。
- 为不制造「假绿」，本报告**不声明前端 UI 已验收通过**；它需要一个**单一、冻结的前端产物 +
  独占浏览器会话**才能给出可信结论（见 §7）。

## 7. 已知边界 / 未覆盖

1. **前端 UI 未验收**（见 §6）：需先冻结前端交付面（`apps/vue-support-eduai-starter` +
   `pages/eduai` + 其依赖包），构建**唯一**产物并部署到**唯一**入口，再用独占的浏览器会话跑
   `ui-paper-assign-acceptance.ps1`。
2. 共享主机上 jar 持续被并发会话替换：**本报告在 2026-10-05 复核时线上仍为 `81810d88…`**；
   若 sha 变化，本报告立即失效。
3. 构建树成分：HEAD `2d2663d61` 之前的多轮提交来自不同会话（含汉字库 `EduHanzi`、
   成语接龙 `chain/*`、`EduPoem` 等）；本报告的验收对象是**现役产物**，对这些代码只做了
   「能编译 + 套件通过」层面的验证，未逐行评审。
4. 前端 SDK/入口存在多份并存（本机 dev `19093`、本机静态 `19094`、远端 nginx `192.168.50.198:19093`），
   验收时必须先确认「被测入口 serve 的是哪份产物」。
