# eduai 终验报告（2026-10-04 · r7，最新代码 + 浏览器业务验收）

> ## ⚠️ 本报告已失效（2026-10-04，被 r8 替换）
>
> 本报告绑定的 eduai jar 是
> `d00d1c451144c8e15f4ef98f9ff12fa2dd403fff0d4284d801de1987e4830c49`，
> 其后线上被替换为 `df7b2ead…`（新增学生头像上传端点 + UI 统一）。
> 已由 `ACCEPTANCE-REPORT-20261004-r8.md` 接替。

> 报告与产物 sha256 绑定；换包即失效。复核：
> `python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

## 0. 本版内容

用最新 HEAD 重建发布，含 **dimension 越权修复** 与 **CORS 白名单修复**；
按要求核对紧凑对象头、非 H2、停端口后升级、前端本机 pnpm 直跑、单实例；
并做**浏览器业务验收**。修复了上一轮 `release-run.sh` 的一个**假阴性**。

## 1. 产物与源码绑定

```
源码 HEAD（构建时）   = b23b3373109f78529c1c7a9f4ea7eedc1b8a3d07
工作区（eduai/oauth） = 干净
本地构建 jar sha256   = d00d1c451144c8e15f4ef98f9ff12fa2dd403fff0d4284d801de1987e4830c49
发布包 bundle sha256  = 267d158c1061f39fe96ad0fcefd9207aaa68388a1f4dd06abc33d0517c075447
部署后线上 jar sha256 = d00d1c451144c8e15f4ef98f9ff12fa2dd403fff0d4284d801de1987e4830c49
判定                  = PROVENANCE_OK
部署前线上 jar        = a3c6dfd16c212578f60ec7dfa03f6cb3cf094751802d3da1bf777d2bd6ae5be3
```

## 2. 线上指纹（部署后 == 验收后）

```
eduai jar     sha256 = d00d1c451144c8e15f4ef98f9ff12fa2dd403fff0d4284d801de1987e4830c49
oauth jar     sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
eduai unit    md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit    md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh     md5    = 2419490ef43ce052cc1805710c812255
eduai 表数           = 105
oauth 表数           = 72
健康                 = eduai 200 / oauth 200
```

## 3. 用户点名要求

| 要求 | 实测 | |
|---|---|---|
| 发布到 198 | 见 §1/§2 | ✅ |
| **紧凑对象头** | 运行进程 cmdline 含 `-XX:+UseCompactObjectHeaders` | ✅ |
| **先停自己端口再升级** | 升级前 `systemctl stop`，确认 `:19095` 占用=0 才部署 | ✅ |
| **数据库不要 H2** | 全树 `jdbc:h2`/`h2database` 命中=0；数据源 `jdbc:mysql://127.0.0.1:3306/eduai` | ✅ |
| 前端本机 pnpm 直跑不打包 | `pnpm dev`（vite:19093） | ✅ |
| 自己项目只一个前端实例 | 19093 单实例（8096/8855/15689/19096 属 system/monitor/ai-guada/collect，非本应用） | ✅ |
| 本机不跑后台 java | 无后台 java 服务（仅他会话的 Maven 构建，未动） | ✅ |

## 4. 生产级验收（API）

```
主验收 acceptance.py                       通过 55 / 失败 0
  A6 前置（新鲜令牌有效）/ A6 管理端被拒 / A6+ 写管理端被拒    PASS
  G1 白名单内来源可登录 / G2 未列入白名单被拒                 PASS
  F1 20 并发 × 3 批 全部成功（60/60）  p50=316ms p95=506ms max=520ms
学生管理专项 student-manage-acceptance.py   通过 19 / 失败 0
学生改密三项 student-password-acceptance.py 通过 15 / 失败 0
```

## 5. 浏览器业务验收（真实浏览器，经本机前端 127.0.0.1:19093）

```
学生登录（学号 2026000001 / stu123456）
  -> /edu/student，标题「学生端」，出现「登录成功」                      PASS
管理员登录（admin / admin123） -> /home                                PASS
学生管理页 /edu/students
  -> 表格渲染「共 3 条」；点行「编辑」弹出左右结构弹窗
     （左侧学生画像 + 右侧「基本资料 / 修改密码」页签）                  PASS
```

> 浏览器能登录即证明 **CORS 修复生效**（改前该路径 403）。
> 本机前端 `pnpm dev`（vite 19093）→ 代理 → 198:19095，全程同源。

## 6. 故障注入（含一处**脚本缺陷修复**）

```
基线             : ok=True token_len=178
停 oauth-server  : 耗时=2811ms  ok=False  eduai健康=200  restarts=0
重新拉起 oauth   : 实测恢复健康用时 **120s**，恢复后 ok=True token_len=178
```

**修复的假阴性**：`release-run.sh` 原先等待窗口 20×5=100s，**小于实测 120s 的冷启动**，
于是本轮首次运行时 oauth 恢复其实成功、却被报成 `oauth=000`（假失败）。
已把窗口改为 48×5=240s，并写明原因。

## 7. 资源

```
available=7325MB (≥1536)   swap=0% (<80)   tmpfs=6% (<80)   oom-kill=0   java=8~9
```

## 8. 结论

| 维度 | 结果 |
|---|---|
| 来源自证 | **PROVENANCE_OK** |
| 功能/安全（55+19+15） | **PASS** |
| 越权（学生/代入令牌读写管理端被拒） | **PASS** |
| 跨域（浏览器登录路径） | **PASS** |
| **浏览器业务验收** | **PASS** |
| 紧凑对象头 / 非 H2 / 停端口后升级 | **PASS** |
| 并发 / 故障注入 / 资源 | **PASS** |

**可交付。** 绑定产物 `d00d1c451144c8e15f4ef98f9ff12fa2dd403fff0d4284d801de1987e4830c49`；
换包即失效，签收前须以 `report-staleness.py` 复核。

> 本会话同批还提交了前端 UI 改动（学生编辑改左右结构+画像、班级/教师弹窗统一、
> 操作列按钮组统一），属 vue 仓 `23abcaa8b` / `1ab3ea46d` / `b1b9818fa`，**不影响本 jar 绑定**。