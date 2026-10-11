# 全站按钮/图标点击验收报告（r45，md5 绑定冻结构建）

> 采样 2026-10-11 13:33–14:01（+08:00），目标机 192.168.50.198

## 部署指纹（前后端同刻冻结构建，md5/sha256 绑定成立）

| 面 | 指纹 | 基线 |
|---|---|---|
| 后端 jar | sha256 `44acbf8f2bcaa2b681ff2866a30d74d5c9ce7cad4d0013ab00e3de0b639ef5b5`（713.1MB） | spring-support-parent-starter `b496ab733`（已推送 gitee） |
| 前端 web | index.html md5 `87b008cd6d473146ea0ba32aee169e14`，1471 文件，入口 `/assets/index-Dvdg6Cbd.js` | vue-support-parent-starter `023f036ff`（已推送 gitee） |

- 来源自证：目标机 `verify.sh` → **WEB_PROVENANCE_OK**（线上 == 本地 dist，逐字节一致）
- 后端部署：`deploy.sh` 幂等流程，unit=active、restarts=0、`/actuator/health`=200
- 回滚备份：`eduai-backend.jar.bak-20261011-013629`（旧 jar `b864a726…`）、`web-20261011-013818.tar.gz`（旧 web md5 `3c42b9e4…`，1117 文件）
- 部署时资源：available=5318MB、swap 2.4%、tmpfs 6%、oom-kill=0；hs_err 转储 4 份均为 10-04 历史件，本次零新增
- eduai 库表数 114（结构脚本幂等收敛，10 份全部执行）

## 验收口径

`ui-full-click-acceptance.ps1`（爬虫式，非手写清单）：Phase 1 逐路由盘点 `main.el-container` 内
全部可点击控件（每点击前重新打标），Phase 2 布局 chrome（顶栏/侧栏/页签）。每次点击后清空并
读取 page error / console error / network 日志；命中未捕获错误、console 错误、HTTP≥500 或空页即
失败；点击无任何效果记 DEAD。覆盖层一律 ESC/关闭 glyph 退出，绝不按文案匹配，避免误触破坏性确认。

## 结论：全过

```
routes: 30   clicks executed: 553   dead clicks: 0   passed: 621   failed: 0
```

- 30 条路由全部存活（含 /login、/home、/edu 及 16 个 /edu/* 子页、/edu/student-login、/edu/teacher）
- 553 次真实点击：表格设置 popper、批量选择、分页、搜索/重置、新建/编辑/删除弹层、开关、
  侧栏 16 项菜单、顶栏（主控台/学生端/老师端）、页签、图标按钮 —— 全部有响应（0 dead）
- 637 行 CSV 逐格校验：pageErr / consoleErr / 5xx / 4xx 四列**无一非零**
- 侧栏 16 项菜单逐一解析：工作台、教育概览、采集管理、AI 组卷、考试管理、AI 阅卷、错题本、
  题库管理、知识库、成语库、古诗集、昆虫园、汉字库、模块管理、学生管理、维度数据

## 证据

- `click-matrix-admin-20261011-1401.md`（矩阵全文 643 行）/ `.csv`（637 行原始记录）
- 逐路由截图 `content-*.png` 35 张 + 收尾 `chrome-after.png`（本机 `D:\ch\eduai-click-20261011\`）

## 过程备注

- 爬虫 14:01 已写出全部产物（矩阵 + 截图），脚本收尾阶段挂住（agent-browser 守护进程不退），
  4 小时超时后被外层杀掉；产物完整、结果不受影响。
- 验收基线为冻结构建：spring `b496ab733` + vue `023f036ff` 同刻 HEAD，部署前后指纹逐位一致。
