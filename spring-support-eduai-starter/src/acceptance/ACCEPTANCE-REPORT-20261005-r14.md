# eduai 终验报告（2026-10-05 · r14，P0 学生统一登录：根因坐实 + 生产产物验收 + 敏感性对照）

> 报告与产物指纹绑定；**换包即失效**。

| 绑定项 | 值 |
| --- | --- |
| web `index.html` md5 | `6730b738ee89944840be055c3982781d`（31839 B） |
| web 文件数 | 1103 |
| 后端 jar sha256 | `81810d88e211bb527a2fac1c8138e20031d2389b0c27a675a2fb88c46cc432c4` |
| 入口 chunk | `/assets/index-DOZWyc6a.js` |
| 登录页 chunk | `StudentLoginView-DiOY3QqT.js` |
| 验收时点 | 后端 pid 90128 已连续运行 7h41m，无重启 |

跑前 / 跑后 / 清理后三次核对上述指纹**完全一致**，验收期间未被并发会话换包。
后端 jar sha256 与 r13 §3.1 绑定值相同 → r13 的 153 条后端断言仍然成立，无需重跑。

## 1. P0 根因：打包产物里有两份 vue-router

`Cannot read properties of undefined (reading 'push')` 的机理已定位到**构建期**，
不是运行期：

```
vite.config.ts 新增 runtimeDedupe() 插件
resolve.dedupe = ["vue","vue-router","vue-demi","pinia","@vue/runtime-core","@vue/runtime-dom"]
（与既有 threeDedupe() 同类问题，提交 abe407b54）
```

未 dedupe 时依赖树里存在两份 vue-router 实例。`StudentLoginView` 里的
`useRouter()` 由 A 实例注入，而 `router.push` 在 A 实例上取不到注入上下文 →
`router` 为 `undefined` → 读 `.push` 抛 `TypeError`。该异常被 `submit()` 的
`catch` 吞成密码错误位文案，`URL` 不变 → 表现为「登录成功但永久停在登录页」。
这一条同时解释了三个现象：dev 正常、prod 失败、页面不跳转。

### 结构性证据（不是推断）

对两版打包产物做 chunk 拓扑比对：

| | vue-router chunk | assets 文件数 |
| --- | --- | --- |
| 修复前 `eduai-web-backup-20261004-110007.tgz` | **两个**：`vue-router-D5hxb5HZ.js` + `vue-router-D-X7kEv7.js` | 1047 |
| 现役部署 | **一个**：`vue-router-D5hxb5HZ.js` | 1071 |

唯一存活的那份被 29 个 chunk 引用，是真正的实现体；多余那份已随 dedupe 修复消失。

## 2. 生产产物上的登录 E2E：21/21

```
python eduai-unified-login-e2e-prod.py      # EDUAI_BASE=http://192.168.50.198:19093
[PASS] A1 打到的是打包产物 /assets/*.js     status=200
[PASS] A2 非 dev server（无 @vite/client、无 /src/main.ts）
[PASS] A3 首页 HTTP 200
[PASS] B1-B5 学生登录 → /edu/student，错误位空，无致命 JS 错误，页面非「加载中」
[PASS] C1-C3 admin 登录 → /edu/teacher
[PASS] D1-D2 错口令 → 留在登录页并给出可读错误
[PASS] E1-E3 连点竞态 → /edu/student，0 致命 JS 错误
[PASS] F1-F3 带有效 token 重登 → /edu/student
[PASS] G1 重复 6 轮全部成功
[PASS] H1 致命 JS 错误 0
===== 21 / 21 =====   （耗时 119.5s）
```

A 组三项是**流程门禁**：历轮 E2E 都跑 dev 是流程漏洞，所以脚本先断言打到的确实是
打包产物，否则脚本自己就会造成假绿灯。

服务端独立佐证：nginx access log（`eduai-web.access.log`，eduai 是独立实例
`/etc/nginx/eduai-web.conf:19093`，**不是**系统 nginx 的 `access.log`）记录
19:29:01–19:30:09 共 8 次 `POST /v2/user/login` 全部 200，随后
`StudentPortalView.js` 与 `/v2/edu/student/me` 均 200。

## 3. 敏感性对照：证明这套断言不是瞎的

全绿的套件如果看不见缺陷，那 21/21 就只是一句没有信息量的话。所以把**同一套断言**
打到修复前那版产物上：

```
对照对象  /home/kali/eduai-web-backup-20261004-110007.tgz
          index.html md5 = 58f805bab5635606559df2c688b2c26b
做法      198 上静态服务暴露旧产物 :19096，Playwright 把 /v2/** 转发到真后端 :19095
结果      login 响应 = [('.../v2/user/login', 200)]        ← 登录接口确实成功了
          pageerror = []        ← 异常被 submit() 的 catch 吞掉，不上 console
          错误文案  = []
          最终 URL  = .../edu/student-login                 ← 登录成功却卡在登录页
判定      [CONTROL-OK] 修复前产物未能落到 /edu/student
```

对照成立的前提（登录接口必须真的成功，否则没走到 `router.push`）已显式断言并通过；
脚本在登录失败时会返回 `[对照无效]` 而不是给出结论。

**结论**：同一套断言在修复前产物上失败、在现役产物上通过 →
① 断言确实能看见这个缺陷；② 现役产物确实已修复。

### 对照的诚实边界

对照证明「现役产物相对修复前产物已修好」。它**不能单独隔离**是哪一个改动修好的 ——
两版相差两个构建，`abe407b54` 是该窗口内唯一相关改动，这是归因，不是证明。

## 4. 交付阻断项：libonnxruntime 原生 SIGSEGV（未解决）

**这是当前不能判「可交付」的唯一硬阻断。**

10-04 单日 4 次 JVM 崩溃转储，全部落在同一个原生库：

```
hs_err_pid228606.log  10-04 01:56  # C  [libonnxruntime.so+0xb66403]  SIGSEGV SEGV_MAPERR
hs_err_pid75288.log  10-04 11:39  # C  [libonnxruntime.so+0xb66403]  SIGSEGV SEGV_MAPERR
hs_err_pid78102.log  10-04 11:50  # C  [libonnxruntime.so+0xb66403]  SIGSEGV SEGV_MAPERR
hs_err_pid170759.log  10-04 00:20  # C  [libonnxruntime.so+0xb6640a]  SIGSEGV SEGV_MAPERR
```

三个 `+0xb66403`、一个相邻偏移 `+0xb6640a` → 同一函数内、同一缺陷。
这是 ONNX Runtime（OCR / 版面分析管线）**打穿整个 JVM**：不是请求失败，是
整个后端进程消失，全部在服用户一起掉线。

当前状态与判断：

- 现役进程 12:01 启动，此后 7h41m 无崩溃、`OutOfMemoryError` 0 次 —— 崩溃**已停止**。
- 但不能据此认定已修复：最后一次崩溃（11:50）早于当前进程启动，而同期并发会话
  在跑 OCR 阅卷任务。崩溃停止更可能是**触发源停止**，而不是缺陷消失。
- 该 jar 未更换（sha256 与 r13 相同），原生库大概率也没换。

要做生产级判定，必须**在受控条件下主动跑一次 OCR/阅卷管线**，观察是否复现。
本轮没做：并发会话正在占用该路径，主动压测会与它抢同一个后端进程。
**这一项未验证前，OCR/阅卷相关功能不应判为可交付。**

## 5. 顺带处理的部署卫生

删除了 `/var/www/eduai/eduai-web.tar.gz` —— 0 字节、属主 `www-data`、
时间戳与部署同刻，是传输失败的残留，且位于**公开可下载的 web root**。

这解释了文件数从 1104 → **1103**：r13 记录的「文件数 1104」本身就含这个残留包。
`verify.sh` 只 `log` 文件数、不做比对（已确认第 30 行无 EXPECTED 比对），
所以删除不会让来源自证脚本误判。

## 6. 我自己造成的一处影响（如实记录）

排查登录失败计数时，我用 `curl` 手工打了一次 `POST /v2/user/login`，**漏传
`authType: "STUDENT"`**，走了错误的认证实现，在 `2026000281` 上记了一次失败：

```
19:37:27  登录失败 username=2026000281  原因=账号/密码不正确，还剩4次尝试机会
19:37:54  [Login] 状态=200, 用户=2026000281, 类型=STUDENT   ← 紧接着正确载荷即成功
```

影响评估：该账号**未被锁定**，正确口令立即登录成功；`oauth` 库无失败次数字段、
Redis 无对应键 → 该计数为进程内状态，重启即清零。仅是剩余次数 5→4 的软节流。
`UserAuthController` 打印该提示即走默认认证实现，本身与学生登录链路无关。

## 7. 本轮产出物

| 文件 | 作用 |
| --- | --- |
| `eduai-unified-login-e2e-prod.py` | 生产产物统一登录 E2E（A 产物身份 / B 学生 / C 管理员 / D 错口令 / E 连点竞态 / F 带令牌重登 / G 重复 N 轮 / H 致命 JS 错误为 0） |
| `eduai-unified-login-e2e-old.py` | 敏感性对照：同断言打修复前产物，含「登录必须真成功」的公平性前提 |
| `eduai-old-render-diag.py` | 对照失败时区分「旧产物坏了」与「对照架子坏了」 |

## 8. 明确边界

1. **本报告绑定的是「产物行为 + 结构」自证，不是构建来源自证。** 前端产物
   （`index.html md5=6730b738…`）**不是我的构建**（并发会话 12:47 部署），
   与 r13 §5.1 同一状况。工作区含他人未提交改动，一并进了产物。
2. **共享主机上 jar 与 web root 都会被并发会话替换**，上述指纹仅在当次成立。
3. **前端部署未接入自动门禁**：`eduai-unified-login-e2e-prod.py` 需真实浏览器，
   只能在开发机跑；目标机上能自动跑的只有 `verify.sh` 与 HTTP 检查。
4. 配套建议（未做，非本次范围）：OCR/阅卷走独立进程池并配
   `-XX:+CrashOnOutOfMemory` 之外的重启策略，或对 ONNX 推理加进程级隔离 ——
   原生库崩溃不应具备打穿整个后端的权限。