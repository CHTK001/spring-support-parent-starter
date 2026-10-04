# eduai 终验报告（2026-10-04 · r6，最新代码版）

> ## ⚠️ 本报告已失效（2026-10-04，被 r7 替换）
>
> 本报告绑定的 eduai jar 是
> `e7824e44483798e2b17a3a988b41838066652432ead4827aa300fb51048fed78`，
> 其后线上被并发会话替换为 `a3c6dfd1…`，再由本会话用最新 HEAD 重建为 `d00d1c45…`。
> 已由 `ACCEPTANCE-REPORT-20261004-r7.md` 接替。

> 报告与产物 sha256 绑定；换包即失效。复核：
> `python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

## 0. 本版内容

用**最新 HEAD** 重建并发布，包含此前两个修复：
1. dimension 管理端越权（`@Permission("admin")`）；
2. **CORS 白名单**（修「登录 403 Invalid CORS request」）。

并按用户要求核对了：紧凑对象头、非 H2（MariaDB）、升级前先停自己端口、
本机前端 pnpm 直跑不打包、浏览器业务验收。

## 1. 产物与源码绑定

```
源码 HEAD（构建时）   = d9941f9b27e10fd5f9570f3142300a4ec948b57b
工作区（eduai/oauth） = 干净（git status --porcelain 为空）
本地构建 jar sha256   = e7824e44483798e2b17a3a988b41838066652432ead4827aa300fb51048fed78
发布包 bundle sha256  = 169dcd1416752aece769fa6b1da24471d5478eaee4b376d570507d691f08e89c
部署后线上 jar sha256 = e7824e44483798e2b17a3a988b41838066652432ead4827aa300fb51048fed78
判定                  = PROVENANCE_OK
```

## 2. 线上指纹（部署后 == 验收后）

```
eduai jar     sha256 = e7824e44483798e2b17a3a988b41838066652432ead4827aa300fb51048fed78
oauth jar     sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
eduai unit    md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit    md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh     md5    = 2419490ef43ce052cc1805710c812255
eduai 表数           = 105
oauth 表数           = 72
健康                 = eduai 200 / oauth 200
```

## 3. 用户点名要求的核对项

| 要求 | 实测 | 结果 |
|---|---|---|
| 发布到 192.168.50.198 | 见 §1/§2 | ✅ |
| **紧凑对象头** | 运行进程 cmdline：`-XX:+UseCompactObjectHeaders` | ✅ |
| **升级前先停自己端口** | 升级前 `systemctl stop eduai-backend`，确认 `:19095` 占用=0 后再部署 | ✅ |
| **数据库不要 H2** | 全树 `jdbc:h2`/`h2database` 命中 = 0；数据源 `jdbc:mysql://127.0.0.1:3306/eduai` | ✅ |
| 前端本机 pnpm 直跑不打包 | `pnpm dev`（vite，端口 19093），未走 build | ✅ |
| 自己项目只一个前端实例 | 19093 单实例（其余 8096/8855/15689/19096 属 system/monitor/ai-guada/collect，非本应用） | ✅ |
| 本机不跑后台 java | 本机无后台 java 服务（仅另一会话的 Maven 构建进程，未动） | ✅ |

## 4. 验收结果（三套）

```
主验收 acceptance.py                  通过 55 / 失败 0
学生管理专项 student-manage-acceptance.py 通过 19 / 失败 0
学生改密三项 student-password-acceptance.py 通过 15 / 失败 0
```

关键项（鉴权 + 跨域）：

```
A6 前置：取到新鲜学生令牌 / 该令牌在学生端可用(200)      PASS
A6 学生令牌访问管理端被拒(403)（同一有效令牌）            PASS
A6+ 学生令牌写管理端被拒                                 PASS
G1 白名单内来源可登录(200)                               PASS
G2 未列入白名单的来源被拒(403)                           PASS
```

## 5. 浏览器业务验收（真实浏览器，经本机前端 127.0.0.1:19093）

```
学生登录（学号 2026000001 / stu123456）
  -> 跳转 /edu/student，标题「学生端」，出现「登录成功」，渲染学习工作台   PASS
管理员登录（admin / admin123）
  -> 跳转 /home，标题「教育 AI 工作台」                                   PASS
学生管理页 /edu/students
  -> 标题「学生管理」，统计「学生总数 3 / 已绑定 3 人」，表格渲染学生数据   PASS
  -> 点「新增学生」弹出表单，含姓名/学段/年级/班级/家长手机号/登录密码/
     身份证号/头像/入学年份/状态/绑定账号；**「学号」字段 disabled +
     占位「保存后自动生成」**（坐实「学号自动生成、不允许填写」）          PASS
```

> 浏览器路径能登录本身即证明 **CORS 修复生效**（此前该路径 403）。
> 另：经前端代理 `POST /v2/user/login` 实测 200 ok=true。

## 6. 跨域（CORS）实测

```
Origin=http://localhost:19093        -> 200
Origin=http://192.168.50.198:19093   -> 200
Origin=http://evil.example.com       -> 403（白名单未退化为通配）
无 Origin（curl）                    -> 200
```

## 7. 并发与故障注入

```
F1 20 并发 × 3 批 全部成功（60/60）
故障注入：停 oauth 耗时 2678ms、ok=False、eduai健康=200、restarts=0；恢复后 ok=True
```

## 8. 资源

```
available=4682MB (≥1536)   swap=0% (<80)   java=9
```

## 9. 结论

| 维度 | 结果 |
|---|---|
| 来源自证（线上 jar == 本地构建） | **PROVENANCE_OK** |
| 功能 / 安全（55+19+15 全绿） | **PASS** |
| 越权（学生令牌读写管理端被拒） | **PASS** |
| 跨域（浏览器登录路径） | **PASS** |
| 浏览器业务验收 | **PASS** |
| 紧凑对象头 / 非 H2 / 停端口后升级 | **PASS** |
| 并发 / 故障注入 / 资源 | **PASS** |

**可交付。** 绑定产物 `e7824e44483798e2b17a3a988b41838066652432ead4827aa300fb51048fed78`；
换包即失效，签收前须以 `report-staleness.py` 复核。