# eduai 终验报告（2026-10-03 · r5，鉴权修复版）

> ## ⚠️ 本报告已失效（2026-10-04，被最新代码版替换）
>
> 依据 AGENTS.md §4.6：本报告绑定的 eduai jar 是
> `563dce9195bc865f6ffa1cb6c75251c79b07c9d8a88eda9aad0ccadcf6c115db`，
> 其后线上产物被替换为 `e7824e44483798e2…`（补齐 CORS 白名单 + 用最新 HEAD 重建）。
> 该 jar 仍有「浏览器登录 403 Invalid CORS request」缺陷，不得作为签收依据。
> 已由 `ACCEPTANCE-REPORT-20261004.md` 接替。

> 报告与产物 sha256 绑定：下列指纹在「部署后」与「验收后」两次采集中**逐字相同**；
> 任何一次换包都会让本报告作废。复核：
> `python3 report-staleness.py --live-eduai <线上 sha> --live-oauth <线上 sha>`。

## 0. 本版解决什么

本版修掉一个**真实的越权漏洞**（非推演，实测学生令牌 INSERT 出了学生记录），
并修掉原验收里一条**假绿**用例。上一版 `ACCEPTANCE-REPORT-20261003.md`（绑定
`75e228fc`）的 jar 已被本版替换，故已失效。

## 1. 发现并修复的越权漏洞

**现象**：任何已登录用户（含学生）都能全量读写 `/v2/edu/dimension/**` 全部端点：

```
GET    students / teachers / classes / subjects          全 200 放行
POST   students|teachers|classes|subjects /save         全放行
DELETE students / teachers / classes                     全放行
POST   students/reset-password、students/impersonate     全放行
```

学生令牌**真的 INSERT 出一条学生记录**（`student_id=5`「越权新增」，已清理）。
其中 `impersonate` 被放行意味着学生可冒充他人学生，属权限提升。

**根因**：`EduDimensionController` 一个 `@Permission` 注解都没有，
而登录鉴权只校验「有没有有效令牌」。框架机制本身是有的且在 eduai 内生效
（`PermissionControlConfiguration` 自动装配，运行日志可见 `PermissionInterceptor`
`[权限拦截器]`）。

**修法**：类级 `@Permission("admin")`，一次覆盖本类全部端点。

- 逐方法标注容易在以后新增接口时漏掉，类级是默认安全；
- 本类端点全部只被管理端页面使用（`/dropdown` 也只被 StudentManageView/PaperView/
  QuestionView 等管理页调用，学生端无依赖），没有需要保留开放的例外；
- 取值 `admin`：`PermissionRules.evaluate` 命中 `roles ∪ permissions` 即放行，
  `ADMIN`/`SUPER_ADMIN` 直接放行。已核对线上 `admin`=ADMIN、`sa`=SUPER_ADMIN，
  **不会被锁在门外**；
- 行为变化：`ops`（OPS 角色）将无法管理维度数据（运维本不该管学生档案）。

## 2. 同时修掉的验收假绿

**A6「学生令牌访问管理端被拒 403」原本是假绿**：它用的是脚本更早取得、
在断言前已被 SINGLE 单点登录/管理员代入顶掉的令牌，403 来自「令牌无效」
而非鉴权拒绝 —— 用新鲜令牌实测该路径返回 **200**。该用例从未真正验证过鉴权。

现改为：断言前重取新鲜令牌 → **先证明该令牌在学生端可用（200）** →
再断言同一令牌打管理端 403，并补 `A6+`「学生令牌写管理端被拒」。

修好后又暴露 **A9 假红**：A6 里重新登录同一学生把代入令牌顶掉了，
已改为在 A6 之后重新签发代入令牌、A10 改用有效的新学生令牌。

> 教训：任何「预期 403」的断言，若不能证明该令牌本身有效，结论就不可信。

## 3. 产物与源码绑定

```
构建 jar 的 HEAD    = c60f8b90b（引入 @Permission 的那次提交）
当前 HEAD           = 0f09c1241（仅修测试脚本 A6/A9，不改 jar 内容）
本地构建 jar sha256 = 563dce9195bc865f6ffa1cb6c75251c79b07c9d8a88eda9aad0ccadcf6c115db
发布包 bundle sha256= 5e24fe967b2e459c4a761bc89bc7b86fcc3cf6bee3973b8b263605d42fc06e15
部署后线上 jar      = 563dce9195bc865f6ffa1cb6c75251c79b07c9d8a88eda9aad0ccadcf6c115db
判定                = PROVENANCE_OK
部署前线上 jar      = 75e228fc6e068ad6070181d6f02da7607335d7f01495d26e04a6472cd3b2865b
```

## 4. 线上指纹（部署后 == 验收后）

```
eduai jar     sha256 = 563dce9195bc865f6ffa1cb6c75251c79b07c9d8a88eda9aad0ccadcf6c115db
oauth jar     sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
eduai unit    md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit    md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh     md5    = 2419490ef43ce052cc1805710c812255
eduai 表数           = 105
oauth 表数           = 72
健康                 = eduai 200 / oauth 200
```

## 5. 验收结果（三套全绿）

```
生产级验收 acceptance.py                 通过 53 / 失败 0
学生管理专项 student-manage-acceptance.py 通过 19 / 失败 0
学生改密三项 student-password-acceptance.py 通过 15 / 失败 0
```

鉴权相关关键项：

```
A6 前置：取到新鲜学生令牌                       PASS
A6 前置：该令牌在学生端可用（证明令牌有效）      PASS  http=200
A6 学生令牌访问管理端被拒                       PASS  http=403
A6+ 学生令牌写管理端被拒（不得创建学生）         PASS  http=403
学生令牌不能新增学生（越权）                    PASS  http=403
学生令牌不能删除学生（越权）                    PASS  http=403
A7 代入令牌访问管理端被拒                       PASS  http=403
A8 代入令牌写学生端被拒（只读）                 PASS  http=403
A9 代入令牌读学生端放行                         PASS  http=200
B1~B15 管理端功能（含学生档案）                 全 PASS（管理员未被误锁）
```

**双向验证成立**：管理员仍能做全部管理操作，学生/代入令牌被正确拒绝。

## 6. 业务能力实测（用户关心的三项）

```
学生登录（学号 2026000001）            PASS
学生登录（家长手机号 13800000001）      PASS
管理员重置学生密码                      PASS（重置后学生立刻能用新口令登录）
学生自助改密                           PASS（新口令可登录、旧口令即失效）
负向：自助改密旧口令错误 -> 400 拒绝     PASS
负向：学生令牌重置他人密码 -> 400/403    PASS
```

学号与档案需求：

```
学号自动生成、不允许填写                PASS（请求塞 9998887776 被忽略，生成 2026000003）
编辑时学号锁定不变                      PASS（编辑时再塞 1112223333，学号仍为 2026000003）
头像 student_avatar(500)                PASS（save 后回读一致）
身份证 student_id_card(32)              PASS（save 后回读一致）
新增/编辑/删除主流程                     PASS（自建自删，不污染数据）
```

## 7. 并发与延迟

```
F1 20 并发 × 3 批 全部成功（60/60）   p50=75ms  p95=111ms  max=114ms
```

## 8. 故障注入

```
基线             : ok=True token_len=178
停 oauth-server  : 耗时=2527ms  ok=False token_len=0  eduai健康=200  restarts=0
恢复后(oauth=200): ok=True token_len=178
```

## 9. 资源判据

```
available=2724MB (≥1536)   swap=28% (<80)   tmpfs=20% (<80)   oom-kill=0   java=9
```

## 10. 附带：deploy.sh 保留策略首次生效

```
==> 5.5/5 清理旧回滚包（保留最新 5 份）
    删除旧回滚包 eduai-backend.jar.bak-20261002-054727
    删除旧回滚包 eduai-backend.jar.bak-20261002-021726
    剩余 5 份
```

## 11. 结论

| 维度 | 判据 | 结果 |
|---|---|---|
| 来源自证 | 线上 jar == 开发机构建 | **PROVENANCE_OK** |
| 越权修复 | 学生/代入令牌访问管理端被拒，管理员不受影响 | **PASS** |
| 验收可信度 | A6 已改为「先证明令牌有效再断言 403」 | **PASS** |
| 功能/安全 | 53/53 + 19/19 + 15/15 | **PASS** |
| 并发/延迟 | 60/60，p95=111ms | **PASS** |
| 依赖隔离 | oauth 停机快速失败、eduai 不崩 | **PASS** |
| 资源 | 四条判据 | **PASS** |

**可签收。** 绑定产物 `563dce9195bc865f6ffa1cb6c75251c79b07c9d8a88eda9aad0ccadcf6c115db`；
换包即失效，签收前须以 `report-staleness.py` 复核。