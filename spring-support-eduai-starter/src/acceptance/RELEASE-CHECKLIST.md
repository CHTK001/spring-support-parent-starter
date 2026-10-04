# eduai 发布冻结与终验清单（可直接执行）

> 目的：把「线上是什么」钉死，并让验收报告**与产物 md5 绑定**（AGENTS.md §4.6/§4.7）。
> 交付 = 交付物 + 验收；本清单保证二者说的是同一个二进制。

## 0. 前置：冻结窗口

- 约定一个时间点，**所有会话停手**（不在窗口内提交源码、不替换 jar、不改 unit）。
- 窗口内工作的只有发布者。
- 判据：`git status --porcelain` 对**本交付面**（`spring-support-eduai-starter/**` 与它依赖的
  `spring-support-oauth-client-starter/**`）为空。

**为什么必须冻结**：实测发生过「线上 jar 被并发会话覆盖、而报告仍写全绿」——
报告证明的是一个已经不存在的二进制（§4.6 的原话）。

## 1. 保真性检查（先看，不改）

```bash
cd <spring-support-parent-starter>
echo "HEAD = $(git rev-parse HEAD)"
echo "--- 本交付面的未提交改动（应为空）---"
git status --porcelain -- spring-support-eduai-starter spring-support-oauth-client-starter
echo "--- 线上 jar / unit 指纹 ---"
ssh <host> 'sha256sum /home/kali/eduai/backend/eduai-backend.jar; \
            md5sum /etc/systemd/system/eduai-backend.service; \
            sha256sum /home/kali/oauth/app/spring-api-support-oauth-starter-4.0.0.42.jar'
```

- 若「未提交改动」非空 → **停止**：线上二进制不在 HEAD 上，按 §4.7 不构成交付物。
  要么让作者提交，要么把窗口后移。
- 把上面三个指纹抄进终验报告的「验收前」栏。

## 2. 从 HEAD 重建并部署（交付面 = oauth + eduai）

```bash
# 2.1 oauth（先起，eduai 依赖它的 19180）
cd <spring-support-api-starter>/spring-api-support-oauth-starter
bash deploy/package.sh                     # 或 pwsh -File deploy/package.ps1
# 上传产物到根分区（不要放 /tmp：那里是小容量 tmpfs）
pscp -batch -pw <pass> dist/oauth-app.tar.gz <user>@<host>:/home/<user>/oauth/oauth-app.tar.gz
ssh <user>@<host> 'sudo BUNDLE=/home/<user>/oauth/oauth-app.tar.gz \
                   bash /home/<user>/oauth/deploy/deploy.sh'

# 2.2 eduai
cd <spring-support-parent-starter>/spring-support-eduai-starter
bash deploy/package.sh
pscp -batch -pw <pass> dist/eduai-app.tar.gz <user>@<host>:/home/<user>/eduai/eduai-app.tar.gz
# 传脚本前必须校验 md5 + 字节数（§4.8），不匹配就不执行
pscp -batch -pw <pass> deploy/deploy.sh <user>@<host>:/home/<user>/eduai/deploy.sh
ssh <user>@<host> 'sudo BUNDLE=/home/<user>/eduai/eduai-app.tar.gz \
                   bash /home/<user>/eduai/deploy.sh'
```

`deploy.sh` 会打印：jar 备份名、schema 收敛结果、**记账异常告警**、健康自检。
**记账告警里有非空列表就要人工确认**（框架不会重跑这类脚本；见 `deploy.sh` 内注释）。

```bash
# 2.3 前端（静态站点 -> /var/www/eduai，由 eduai-web.service 的 nginx 在 19093 提供）
#     与后端同一形态：package（构建+打包） / deploy（备份+原子换+自检） / verify（来源自证）
cd <vue-support-parent-starter>/apps/vue-support-eduai-starter
bash deploy/package.sh                     # 或 pwsh -File deploy/package.ps1
#     会打印 bundle sha256 与 dist/index.html 的 md5 —— 第 4 步要拿它做来源自证
pscp -batch -pw <pass> dist/eduai-web.tar.gz <user>@<host>:/home/<user>/eduai/eduai-web.tar.gz
#     部署脚本单独传，**不要**打进 web bundle：web root 是公开目录，脚本会被直接下载
pscp -batch -pw <pass> deploy/deploy.sh deploy/verify.sh <user>@<host>:/home/<user>/eduai/web-deploy/
ssh <user>@<host> 'sha256sum /home/<user>/eduai/eduai-web.tar.gz'     # 与 package 输出比对
ssh <user>@<host> 'sudo BUNDLE=/home/<user>/eduai/eduai-web.tar.gz \
                   bash /home/<user>/eduai/web-deploy/deploy.sh'
# 4) 来源自证：线上站点 == 本地构建的 dist
ssh <user>@<host> 'bash /home/<user>/eduai/web-deploy/verify.sh <dist/index.html 的 md5>'
#    期望输出 WEB_PROVENANCE_OK（内含 INDEX_MD5_MATCH）；否则线上不是这份构建
```

## 3. 终验（全部跑完才可签）

```bash
# 3.1 记下验收前指纹（同 §1）
# 3.2 API 生产级验收 + 来源自证（一条命令；在目标机上跑）
#     先在开发机 deploy/package.sh 构建，记下打印的 jar sha256，再：
ssh <host> 'sudo EXPECTED_JAR_SHA=<开发机构建的 sha256> \
            BUNDLE=/home/<user>/eduai/eduai-app.tar.gz \
            bash /home/<user>/eduai/backend/deploy/release-run.sh'
#     它会：部署 -> 断言线上 jar == 本地构建（PROVENANCE）-> 资源判据 ->
#           49 项 API 验收 -> 故障注入 -> 前后指纹。任一项不过即非 0 退出。
#     只做只读体检：bash release-run.sh --check
#
# 3.3 报告失效核查（在**仓库侧**跑，因为报告在 src/acceptance/）
python3 src/acceptance/report-staleness.py \
    --live-eduai <线上 eduai jar sha256> --live-oauth <线上 oauth jar sha256>
#     期望：所有历史报告要么 VALID，要么已标注失效（REPORT_VALID / 均已标注）
# 3.3 故障注入：停 oauth -> 登录应秒级失败且 eduai 不崩 -> 恢复后自动可用
ssh <host> 'sudo systemctl stop oauth-server.service; sleep 2; \
            curl -s -o /dev/null -w "%{http_code}\n" --max-time 30 \
              -X POST http://127.0.0.1:19095/v2/user/login \
              -H "Content-Type: application/json" \
              --data-binary "{\"username\":\"admin\",\"password\":\"admin123\"}"; \
            curl -s -o /dev/null -w "eduai=%{http_code}\n" http://127.0.0.1:19095/actuator/health; \
            sudo systemctl start oauth-server.service'
#     判据：登录 3s 级返回 ok=false（HTTP 200 但 ok=false）、eduai 健康 200、restarts 不增
# 3.4 UI（需要真实浏览器，只能在**开发机**跑；目标机没有浏览器）
#     班级试卷布置的浏览器业务验收已脚本化入库：
#       pwsh -File src/acceptance/ui-paper-assign-acceptance.ps1                              # 开发机 dev（19093）
#       pwsh -File src/acceptance/ui-paper-assign-acceptance.ps1 -Base http://<host>:19093    # **打包部署后的站点**
#     期望 21/21、退出码 0。它会自建夹具（班级+学生，自建自删）→ 老师用界面布置 →
#     学生端看到并点开题目（UI 题数 == 接口题数）→ 撤回/删学生/删班级。
#     截图默认落 %TEMP%/eduai-ui-acceptance（-OutDir 可改）：不要让它写进仓库，会污染历史。
#     部署后的静态站点本身由 2.3 的 deploy/verify.sh 做来源自证（WEB_PROVENANCE_OK）。
# 3.5 复核指纹：必须与 3.1 逐一相同
```

## 4. 报告模板（复制填写）

```
产物（验收前 = 验收后，必须逐字相同）
  eduai jar     sha256 = ________________
  oauth jar     sha256 = ________________
  eduai unit    md5    = ________________
  oauth unit    md5    = ________________
  deploy.sh     md5    = ________________
源码
  repo HEAD            = ________________
  本交付面未提交改动   = 0（必须）
库
  eduai 表数 / oauth 表数 = ____ / ____
  记账未成功脚本          = ____（空为佳；非空需说明处置）
结果
  API 生产级验收  = 通过 __ / 失败 __
  并发            = __/__
  故障注入        = 快速失败 ___ms、eduai restarts ___、恢复 ___
  UI              = 学生登录 __/__ · 代入 __/__ · 白名单 __/__
```

## 5. 签收条件（缺一不可）

1. §1 的「本交付面未提交改动」为空。
2. §4 报告里**验收前/后指纹逐字相同**。
3. API 验收 **0 失败**；故障注入满足判据；UI 全绿。
4. `deploy.sh` 的**记账异常告警为空**（或已逐条说明处置）。
5. 资源侧有明确结论（见下）——否则不予承诺可用性。

## 6. 已知阻断项（签收前必须放行或显式豁免）

| # | 阻断项 | 放行判据 |
|---|---|---|
| A | 交付物无冻结点 | §1 未提交改动为空 + 报告指纹前后一致 |
| B | 资源与稳定性 | 目标机留出安全余量（不再出现 OOM kill）；swap 不长期打满；`/tmp` 不再被打满 |
| C | 存量库结构漂移 | `deploy.sh` 记账告警为空；`ngxacc` / `monitordb` 等同类库由各自 owner 用同一招收敛 |

> B 若无法在本轮解决，须由发布决定人**书面豁免**并写进报告，不得默认放过。

## 7. 资源基线（终验时必须记录）

实测（2026-10-01，目标机）：

```
Mem total 16165 MB / available ~2.9 GB ；Swap 845/953 MB（接近满）
/tmp = tmpfs 2.9G：曾 100% 打满（**吃的是内存，不是磁盘**），现 39%
java 进程 12 个。RSS 前几：
  eduai 2666MB(-Xmx2g) · app.jar 1357MB · nacos 1262MB · app.jar 1160MB
  · monitor 854MB · … · oauth 604MB(-Xmx512m)
根分区 39G 空闲；eduai jar 备份累计 7.1G
```

放行判据（不满足则不予承诺可用性，须书面豁免）：

1. `available ≥ 1.5 GB` 且 swap 使用率 `< 80%`，稳态观察 ≥ 10 分钟；
2. 自本清单最后一次重启起，`dmesg` 无 `oom-kill`（历史上曾杀过 eduai）；
3. `/tmp`（tmpfs）使用率 `< 80%`。

建议：

- 明确「交付必需」的服务清单，非必需的同机服务在验收窗口内停掉；
- 备份按**文件名里的时间戳**保留 newest N —— 不要用 mtime 排序，
  `cp -p` 会让多份备份 mtime 并列（仓库里记过这个坑）。

