# eduai 终验报告（2026-10-04 · r10，班级试卷布置全链路 + 浏览器验收入库）

> 报告与产物 sha256 绑定；**换包即失效**。复核：
> `python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

## 1. 为什么有 r10（r9 已失效）

r9 绑定的是**我的构建** `8ad6d0bf…`。发布后约 1 小时，并发会话又把线上 jar 换成了
`0e663904…`（该仓共享主机上 jar 一小时内被替换至少 4 次）。按纪律 r9 立即失效，
本报告改为绑定**当前现役产物**，并对它重跑了全套验收（而不是沿用旧结论）。

## 2. 产物与源码绑定

```
源码 HEAD（本次核对）  = 6f6da6136（含本轮全部提交）
现役 jar sha256        = 0e663904e4ff1dd3f5d5f6eeb37466f2dae78c706d06458b891203fd3a9db2ce
本次实测前 sha         = 0e663904e4ff1dd3f5d5f6eeb37466f2dae78c706d06458b891203fd3a9db2ce
本次实测后 sha         = 0e663904e4ff1dd3f5d5f6eeb37466f2dae78c706d06458b891203fd3a9db2ce
故障注入后 sha         = 0e663904e4ff1dd3f5d5f6eeb37466f2dae78c706d06458b891203fd3a9db2ce
判定                   = ARTIFACT_STABLE_OK / ARTIFACT_STABLE_FINAL_OK
```

> **这个 jar 不是我构建的**：它来自另一会话从「包含我全部提交的 HEAD」构建的产物。
> 因此不适用 PROVENANCE（那要求「线上 == 我的构建」），改用**产物内代码核对**证明
> 交付内容确实在里边：
>
> ```
> EduPaperAssignController.class 在产物内 = 1
> 类内 studentPaperDetail 方法           = 2 处
> 内嵌 DDL 的 edu_paper_assign 建表含 create_time = 3 处（列 + 兼容 ALTER）
> 线上库 edu_paper_assign.create_time 存在 = 是
> ```
>
> 三个都是本轮的关键改动点（功能类 / 按布置授权的详情接口 / 500 的根因修复）。

## 3. 生产级验收：6 套件 153 条断言，0 失败

（在目标机上对**现役产物**跑，跑前跑后 sha 一致）

```
acceptance.py                        通过 55 / 失败 0   exit=0
student-password-acceptance.py       通过 15 / 失败 0   exit=0
student-manage-acceptance.py         通过 20 / 失败 0   exit=0
first-login-password-acceptance.py   通过 12 / 失败 0   exit=0
paper-assign-acceptance.py           通过 40 / 失败 0   exit=0
marking-acceptance.py                通过 11 / 失败 0   exit=0
--------------------------------------------------------------------------
SUITES=OK      表数 eduai/oauth = 107 / 72      健康 eduai 200 / oauth 200
```

`paper-assign-acceptance.py` 本轮由 39 → **40** 条：把原先显式 `[SKIP]` 的
**跨老师越权**（布置到「别人的班」）改成真验证 —— 用一次**夹具内**的 SQL 把自建班级的
归属改成别的账号（当前环境只有一个教师账号，靠接口造不出他人班级），实测：

```
[PASS] 布置到「别人的班」被拒（老师只能布置给自己名下的班级）
       http=400 msg=只能给自己名下的班级布置试卷
```

（mariadb 不可用时该条仍判 SKIP 而非 FAIL，避免把环境问题算成产品缺陷。）

## 4. 故障注入（对现役产物）

```
基线             : ok=True token_len=178
停 oauth         : 耗时=2533ms ok=False token_len=0   eduai健康=200   restarts=0
恢复后(oauth=200): ok=True token_len=178
```

## 5. 浏览器业务验收：脚本已入库并可复现

原先的浏览器验收只存在于我的本机 `.local-run/`（**不在任何仓库内**），换台机器就复现不了，
等于没交付。现已入库为：

```
src/acceptance/ui-paper-assign-acceptance.ps1     （ASCII-only，pwsh 7）
```

本机实跑（对现役后端 + 本机 dev 前端 19093）：**20/20，退出码 0**

```
[PASS] admin login over the dev proxy            http=200
[PASS] create class / student / attached         classId=83 studentId=851 studentNo=2026000220
[PASS] student initial password changed (clears the blocking first-login guide)
[PASS] teacher (admin) logged in via the login page
[PASS] viewport applied after navigation         {"w":1440,"h":1000}
[PASS] paper tab rendered (assign button has a real size)
[PASS] found a PUBLISHED paper to assign         idx=0 of 20
[PASS] pointer click reached the assign button
[PASS] assign dialog opened
[PASS] class dropdown opened with the fixture class
[PASS] fixture class selected in the dialog      arrowSteps=22
[PASS] assignment list shows the fixture class as assigned
[PASS] assign record exposes a question count    paperQuestionCount=10
[PASS] student logged in and reached the portal
[PASS] exactly the one assigned paper is listed for the student   cards=1
[PASS] view paper: questions rendered            qCount=10
[PASS] rendered question count equals the paper question count    ui=10 api=10
[PASS] cleanup: revoked assignId / deleted student / deleted class
```

脚本工程要求（都是实测踩出来的，已写进文件头注释）：

- 每次浏览器调用**90s 超时**。实测一次 `open` 挂死 30 分钟，验收卡在中间、
  连 `finally` 清理都没跑到 —— **会挂死的门禁比会失败的门禁更糟**。
- 断言里的中文比较**放回 JS**（`\uXXXX`）。中文经控制台/stdout 往返会被重编码，
  在 PowerShell 侧 `-match` 会稳定假红。
- 关键交叉校验用**数字**：`UI 题数 == 接口 paperQuestionCount`，不依赖任何中文字符串。
- 视口在导航**之后**设置并回读；页签就绪判据是「按钮 rect 有真实尺寸」而不是「元素存在」
  （隐藏面板里的按钮 rect 是 0×0）；`el-select` 用键盘（方向键步数按目标所在列表内、
  相对当前选中项的偏移算）。
- 夹具学生**先经 API 改一次口令**：否则首次登录的强制改密弹窗会挡住门户，
  失败原因与「试卷布置」毫无关系。
- 失败非 0 退出（发布脚本只看退出码）。

## 6. 明确未覆盖 / 已知边界

1. **产物归属**：现役 jar 由并发会话构建。它含我的全部改动（已核对），
   但**不含**「线上 == 我的构建」这一层证明；我的构建 `8ad6d0bf…` 的那份
   `PROVENANCE_OK` 记录见 r9（已失效，仅作历史）。
2. 前端仍是**本机 dev 直跑**（未打包静态产物）；UI 结论建立在 dev 前端 + 198 后端上。
3. 共享主机 jar 持续被替换。**签收后若线上 sha 变化，本报告立即失效**，
   重跑成本：目标机 6 套件约 6 分钟；UI 验收约 3 分钟。
4. 构建树的成分说明见 r9 §2（当时工作区含他会话未提交的 eduai 改动）；
   本报告的验证对象是**现役产物**，同样不评审那部分代码。
