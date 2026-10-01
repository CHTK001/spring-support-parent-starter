# eduai 终验报告（2026-10-02，含来源自证）

> 按 `RELEASE-CHECKLIST.md` 执行。与上一份 `ACCEPTANCE-REPORT-20261001.md` 的区别：
> 本次产物**由发布者从 HEAD 自己构建**，因此额外通过了一项「来源自证」——
> 线上 jar 的 sha256 与本地构建产物**逐字节相同**，不再依赖「相信别人构建的那份」。

## 1. 来源自证（本次新增，最关键的一项）

```
源码 HEAD             = f160e1690cd61bea8ad57e66ce96e670c9e41cca
本地构建 jar sha256   = a2d012b5436a80b8c3544157aa0c9d14633702bb77dd1310e1f2d8a59c0fd7a9
部署后线上 jar sha256 = a2d012b5436a80b8c3544157aa0c9d14633702bb77dd1310e1f2d8a59c0fd7a9
判定                  = PROVENANCE_OK
```

⇒ **线上跑的二进制 = 本仓库 HEAD 的构建**。这条把上一份报告里
「运行产物来源不可自证」的保留项消掉了。

构建/上传/部署三段都带校验：
- 构建：`deploy/package.sh`（或 `package.ps1`）→ `dist/eduai-app.tar.gz` sha256 `970e8f84…`
- 上传：bundle sha256 远端一致；`deploy.sh` 校验 **md5 + 字节数**
  （`TRANSFER_VERIFIED md5=99a838974044416fc9e55aeeaf8e268f size=9151`，§4.8）
- 部署：`deploy.sh` 打印新旧 jar 的 sha，备份旧 jar（`…bak-20261001-115703`）

## 2. 其余指纹（验收前 = 验收后）

```
oauth jar     sha256 = ca4ce0b3d929c96e6f607950c24ac235
eduai unit    md5    = 53f648c2a893c28e13f37c81333fb5f1
oauth unit    md5    = 813c6eb1d409753974047c4732eb9dbd
deploy.sh     md5    = 99a838974044416fc9e55aeeaf8e268f
eduai 表数 = 105      oauth 表数 = 72
```

## 3. 结果

| 项 | 结果 |
|---|---|
| API 生产级验收 | **通过 49 / 失败 0**（退出码 0）|
| 并发 | 20 × 3 = **60/60**；p50 **616ms** / p95 **856ms** / max 880ms（本机已加载 12 个 java 进程，非空载基线）|
| 故障注入·断 | 停 oauth → **2711ms** 返回 `ok=false`；eduai **健康 200、restarts 0** |
| 故障注入·恢复 | oauth 起来后：admin `ok=true token=178`、学生 `ok=true token=178`；oauth 日志线程 `aio-tcp-iocp`（走 tcp）|
| 部署脚本自检 | schema 收敛：结构脚本 8 个（整份成功 7、容错跳过 1），库 105 表 |
| 指纹复核 | 全部逐字相同 |

> 首跑时「恢复后」探到的是 `oauth=000` —— 那是**探测跑早**（oauth 冷启动 ~70s），
> 不是功能问题；补一次恢复验证即全通过。记下这个坑：故障注入的恢复判据
> 必须等 oauth 健康 200 之后再断言。

## 4. 需要发布决定人给结论的一项

**资源判据**（`RELEASE-CHECKLIST.md` §7）：实测 `available ≈ 2.9 GB`、
`swap 845/953`、java 进程 12 个、`/tmp` tmpfs 39%、根分区 39G 空闲。
三条判据（available ≥1.5G 且 swap <80%、dmesg 无 oom-kill、tmpfs <80%）
请给「放行」或「书面豁免」。

## 5. 结论

**可以交付。** 依据：
1. 交付面未提交改动 = 0；
2. **来源自证通过**（线上 = HEAD 构建）；
3. 指纹验收前后逐字一致；
4. API 49/49、并发 60/60、故障注入断/恢复均符合判据；
5. 部署两侧脚本可重放，且本次就是用它部署的。

唯一待办是 §4 的资源判据结论（属发布决定，不属技术缺陷）。
