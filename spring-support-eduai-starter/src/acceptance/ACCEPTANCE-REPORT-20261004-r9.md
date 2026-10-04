# eduai 终验报告（2026-10-04 · r9，班级试卷布置 + 学生可看题 + 验收门禁自修）

> 报告与产物 sha256 绑定；**换包即失效**。复核：
> `python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

## 1. 本版内容

| # | 需求/缺陷 | 处置 |
|---|---|---|
| 1 | 老师给班级组卷、班内学生能看到 | 新增 `edu_paper_assign` 表 + `/v2/edu/paper-assign` 接口组；老师端「布置给班级」+「我布置的试卷」；学生端「我的试卷」 |
| 2 | （验收抓到的真缺陷）布置/撤回 500 | 建表漏 `create_time`，实体走 MyBatis-Plus 自动映射 → `Unknown column`；补齐建表 + 兼容 ALTER |
| 3 | （验收抓到的真缺陷）学生看不了题目 | 试卷详情按**归属人**鉴权，学生被 400 `B0500S0001` 拒；改为按**布置关系**授权的新接口 |
| 4 | （验收门禁自身的缺陷）失败被吞 | 4 个套件只 print 不 `sys.exit` → 退出码恒 0，「19/失败1」却报 API=OK；已补非 0 退出 |
| 5 | （验收门禁自身的缺陷）中文筛选假失败 | 中文参数未百分号编码，请求异常被吞成「0 行」；已改 `urllib.parse.quote` |

## 2. 产物与源码绑定

```
源码 HEAD（构建时）    = c50fcbaa2aeb545760b29989bf4e1dd01d4ceb92
本地构建 jar sha256    = 8ad6d0bfdbe675d3afed9a8a1249b86ca09c08339ade3ca3fb2eaf1572c6bdbc
本地 bundle sha256     = ed6cdd590f8a8c0d5809ed3562442ad14e2cd5b54716e6f8acfb5dfa2c43820c
部署后线上 jar sha256  = 8ad6d0bfdbe675d3afed9a8a1249b86ca09c08339ade3ca3fb2eaf1572c6bdbc
签收时现场复核         = 8ad6d0bfdbe675d3afed9a8a1249b86ca09c08339ade3ca3fb2eaf1572c6bdbc（一致）
判定                   = PROVENANCE_OK
```

> ⚠ **构建树的成分说明（共享工作区事实，必须写清）**：本 jar 在 c50fcbaa2 的工作树上构建，
> 但当时工作区还含有**另一会话未提交**的 eduai 改动（`EduIdiom*` 修改、
> 新增 `EduHanzi*` 与 `V1.8/V1.9__init_all_*.sql` 等）。这些**一并进了产物**，
> 并被部署脚本的 schema 收敛重放（eduai 表数 106 → 107）。
> 也就是说：jar 里有我的全部改动，也**不止**我的改动。

## 3. 线上指纹（部署后 == 验收后）

```
eduai jar  sha256 = 8ad6d0bfdbe675d3afed9a8a1249b86ca09c08339ade3ca3fb2eaf1572c6bdbc
oauth jar  sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
eduai unit md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh  md5    = 2419490ef43ce052cc1805710c812255
表数 eduai/oauth  = 107 / 72
健康              = eduai 200 / oauth 200
紧凑对象头        = 运行 cmdline 含 UseCompactObjectHeaders
```

## 4. 生产级验收：6 套件 152 条断言，0 失败

`release-run.sh` 单命令跑完（含资源门禁、来源自证、鉴权热就绪、故障注入）：

```
acceptance.py                        通过 55 / 失败 0   exit=0
student-password-acceptance.py       通过 15 / 失败 0   exit=0
student-manage-acceptance.py         通过 20 / 失败 0   exit=0
first-login-password-acceptance.py   通过 12 / 失败 0   exit=0
paper-assign-acceptance.py           通过 39 / 失败 0   exit=0
marking-acceptance.py                通过 11 / 失败 0   exit=0
--------------------------------------------------------------------------
套件: 全部 OK        API 验收=OK        PROVENANCE_OK / API=OK        可签收
```

`paper-assign-acceptance.py`（本轮新增）覆盖：

```
建班 + 建生(带初始口令) -> 学生登录
  基线：布置前学生 /my-class 不含该卷
  老师布置 -> 学生 /my-class 立刻含该卷，且带出 paperName / className
  **班间隔离**：发给 A 班的卷，B 班学生看不到（且 B 班 /my-class 调用本身正常）
  重复布置不堆行（(paper_id,class_id) 唯一，仍 1 行）
  撤回后学生立刻看不到 -> 再次布置可复活
  未发布的卷布置了学生也看不到（只下发已发布）
  学生看详情：**带出 10 道题**；未布置给本班的卷看不到；无令牌看不到
  负向：学生令牌/无令牌越权被拒；不存在的班级/试卷/记录一律 400 不 500
  [SKIP] 跨老师越权（环境只有一个教师账号，构造不出他人班级，不计入通过）
```

## 5. 故障注入

```
基线           : ok=True token_len=178
停 oauth       : 耗时=2542ms ok=False token_len=0   eduai健康=200   restarts=0
恢复后(oauth=200): ok=True token_len=178
```

## 6. 浏览器业务验收（用户要求：验收必须结合浏览器）

夹具：班级「浏览器验收班」（classId 61）+ 班内学生 2026000198（口令 `Init#12345`）。
前端本机 dev（127.0.0.1:19093）→ 代理到 198 后端。

```
A 老师端（真指针点击，非 JS 派发）
  A1 点「布置给班级」按钮命中   assignClicks=1
  A2 对话框打开                 title=布置试卷到班级 paper=验收试卷-1791095133
  A3 选班级（方向键 9 步 + 回车）选中=浏览器验收班-1791113318
  A4 提交后「我布置的试卷」首行  验收试卷-1791095133 | 浏览器验收班-… | 小学 | 已布置

B 学生端（学生自己登录 2026000198）
  B1 进入学生门户 /edu/student
  B2 「我的试卷」卡片            验收试卷-1791095133 数学 小学·三年级 总分100 10题 布置时间 2026-10-04 08:54:12
  B3 点「查看试卷」              **qCount=10**，题目与分值全部渲染
```

截图：`biz-A-teacher-done.png`、`biz-B2-papers.png`、`biz-B3-detail.png`。

## 7. 明确未覆盖 / 已知边界

1. **跨老师越权**（布置到别人的班）未端到端覆盖：环境只有 1 个教师账号、
   所有班级 owner 都是它，构造不出「他人的班」。脚本里显式 `[SKIP]`，不冒充通过。
2. 前端仍是**本机 dev 直跑**（未打包、未发布静态产物）；本报告的浏览器结论建立在
   dev 前端 + 198 后端之上。
3. 共享主机上 jar 被并发会话反复替换（本小时内至少 3 次）。本报告的绑定以
   §2 的现场复核为准；**签收后若线上 sha 变化，本报告立即失效**。
4. 构建树含他会话未提交的 eduai 改动（见 §2 说明），这些代码未随本报告被评审。
