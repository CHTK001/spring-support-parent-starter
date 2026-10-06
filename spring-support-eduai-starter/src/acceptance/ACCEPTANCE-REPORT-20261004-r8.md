# eduai 终验报告（2026-10-04 · r8，学生头像上传 + UI 统一）

> ## ⚠️ 本报告已失效（2026-10-06 补标，§4.6 收口）
>
> 依据 §4.6：本报告绑定的 eduai jar `df7b2ead` 其后被多次换包，
> 现役为 `e4e0b214…`。**报告证明的二进制已不在线上，不得作为签收依据**；
> 签收口径见 `ACCEPTANCE-STATUS.md`。

> 报告与产物 sha256 绑定；换包即失效。复核：
> `python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

## 1. 本版内容

按用户四项要求改动并发布：

| # | 要求 | 处置 |
|---|---|---|
| 1 | 点头像改学生头像；删掉表单里的头像与 URL 输入框 | 新增后端上传/预览端点 + 前端点头像上传 |
| 2 | 操作列表头是白的，与其它列不一致 | 表头单元格背景钉回主题色 |
| 3 | 新增学生按钮圆角作为 ReButton 统一默认 | Square 10px / Modern 8px → 统一 `--el-border-radius-base`(6px) |
| 4 | Tab 没吃满高度 | page→card→tabs→content→pane→table 纵向 flex 链 |

## 2. 产物与源码绑定

```
源码 HEAD（构建时）   = 22c19a5e35163a65d10534a50c5416953b8e04db
本地构建 jar sha256   = df7b2eadb0550255cbe89f34b452ed416a6285da95fbf73e092910ccd365db9a
发布包 bundle sha256  = 41899a1a15cac15fdc17b58bfe82aa19f9b7cfb4bfe31e90823b2bae3ec664d6
部署后线上 jar sha256 = df7b2eadb0550255cbe89f34b452ed416a6285da95fbf73e092910ccd365db9a
判定                  = PROVENANCE_OK
```

## 3. 线上指纹（部署后 == 验收后）

```
eduai jar  sha256 = df7b2eadb0550255cbe89f34b452ed416a6285da95fbf73e092910ccd365db9a
oauth jar  sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
eduai unit md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh  md5    = 2419490ef43ce052cc1805710c812255
表数 eduai/oauth  = 105 / 72
健康              = eduai 200 / oauth 200
紧凑对象头        = 运行进程 cmdline 含 UseCompactObjectHeaders
```

## 4. 学生头像端点（新增，实测）

```
① 上传（管理员）    POST /v2/edu/student-avatar/upload            -> 200, 返回 filePath
② 预览（无令牌）    GET  /v2/edu/student-avatar/file?path=..      -> 200 image/png，67B == 原图 67B
③ 学生令牌上传      -> 403（@Permission("admin") 生效）
④ 路径穿越          path=../../etc/passwd -> 400
⑤ 非图片扩展名      .txt -> 拒绝（仅 png/jpg/jpeg/gif/webp/bmp）
⑥ 落地目录          data/student-avatars/<yyyyMMdd>/<uuid>.<ext>
```

> 预览端点刻意**不加权限注解并列入 oauth 白名单**：头像要在 `<img src>` 里显示，
> 而 `<img>` 无法携带 Authorization —— 若预览受权限拦截，取图必然 403 且只在图片
> 请求里报错、极隐蔽。放行不构成任意文件读取（相对路径 + 穿越校验 + 随机名 + 扩展名白名单）。
> 测试产生的头像文件已清理。

## 5. 生产级验收（API）

```
主验收 acceptance.py                       通过 55 / 失败 0
  含 A6 越权（学生令牌读写管理端被拒）、G1/G2 跨域、F1 并发 60/60（p95=302ms）
故障注入：停 oauth 耗时 2626ms 快速失败、eduai 不崩(restarts=0)、
          恢复后 oauth=200 且登录 ok（上轮修的 240s 窗口生效）
deploy.sh 5.5 旧回滚包保留策略生效（删 3 份、保留 5 份）
资源：available=5685MB  swap=0%  tmpfs=6%  oom-kill=0  java=8
```

## 6. 前端四项改动的验证

- **#1 头像**：后端端点见 §4（全项实测）。前端「基本资料」里的「头像」表单项
  及其 URL 输入框已删除，左画像大头像改为可点击上传（按钮 + 隐藏 file input，
  hover 浮出「换头像」），上传成功写回 `studentAvatar`，点「保存」才落库。
- **#2/#3/#4**：`vite dev` HMR 应用**无编译错误**（已核对 dev server 输出）。
- **浏览器验证的局限（如实记录）**：本机浏览器自动化工具在**管理端侧边子菜单
  折叠态下不渲染菜单项**（只有图标），无法在「已登录且不整页刷新」的前提下
  自动跳到 `/edu/students`；新开标签因令牌在 sessionStorage/内存中，会落到登录页。
  故 §5 的 API 验收 + §4 的端点实测是本版的主要依据；#2/#3/#4 为纯 CSS，
  已核验 HMR 无错，请刷新页面目视确认。

## 7. 结论

| 维度 | 结果 |
|---|---|
| 来源自证 | **PROVENANCE_OK** |
| API 生产级（55/55） | **PASS** |
| 学生头像上传/预览/鉴权/穿越/类型 | **PASS** |
| 紧凑对象头 / 非 H2 / 停端口后升级 | **PASS** |
| 并发 / 故障注入 / 资源 | **PASS** |
| 四项 UI 改动 | 已实现（HMR 无错；管理端页面目视待确认） |

**可交付。** 绑定产物 `df7b2eadb0550255cbe89f34b452ed416a6285da95fbf73e092910ccd365db9a`；
换包即失效，签收前须以 `report-staleness.py` 复核。