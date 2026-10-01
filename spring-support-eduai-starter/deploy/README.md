# EDUAI 后端部署

从源码到运行的可重放部署。**目标形态**：目标主机上的 systemd 服务 `eduai-backend.service`。

## 产物与脚本

| 文件 | 作用 | 在哪执行 |
|---|---|---|
| `package.sh` / `package.ps1` | 构建 fat jar + 打包，产出 `dist/eduai-app.tar.gz` | 开发机 |
| `deploy.sh` | 解包 → 备份/替换 jar → 装 unit → 重启 → 自检 | 目标主机（root） |
| `eduai-backend.service` | systemd unit（凭据走 `EnvironmentFile`，**不含明文**） | — |
| `eduai.env.example` | 凭据模板 | — |

产物包内容：`spring-support-eduai-starter-<ver>.jar`（fat jar，已 repackage，含全部依赖）
+ `deploy/`。**装置前请把产物放根分区**：这个 jar ~800MB，本仓库部署主机的 `/tmp`
是小容量 tmpfs（实测 2.9G，且占用内存），放进去会 `No space left on device`。
目标机上现役文件名是 `eduai-backend.jar`，`deploy.sh` 会改名安装。

## 前置条件

1. 目标机已装 **JDK 25**（jar 以 Java 25 编译，class 版本 69）。
2. 目标机有 **MariaDB**（`eduai` 库）与 **Redis**。
3. 认证服务端 oauth **已就绪**（eduai 走 `plugin.oauth.protocol=tcp` 连它的 19180；
   oauth 部署见 `spring-api-support-oauth-starter/deploy/`）。
4. 已准备凭据文件：

   ```bash
   sudo install -d -m 0755 /etc/eduai
   sudo install -m 0600 deploy/eduai.env.example /etc/eduai/eduai.env
   sudo vi /etc/eduai/eduai.env          # EDUAI_PORT / SPRING_DATASOURCE_* / OAUTH_TCP_ADDRESS
   ```

   **端口与 oauth 地址**由 unit 的 `ExecStart` 用 `${VAR}` 引用（systemd 支持在
   ExecStart 里展开 `Environment`/`EnvironmentFile` 的值，实测带 `?` 与 `&` 的
   JDBC URL 也原样展开、不做二次分词）；变量缺失时 systemd 直接报错、服务不起。

   **数据源刻意不写成命令行参数**：命令行会出现在 `/proc/<pid>/cmdline`（本地任意
   用户 `ps` 可见），而 unit 是要入仓库的。改用 `SPRING_DATASOURCE_URL` /
   `_USERNAME` / `_PASSWORD` —— Spring Boot 的环境变量绑定等价于
   `spring.datasource.*`，只在 `/proc/<pid>/environ` 里（仅属主/root 可读）。
   两者都不入仓库。

   可选：AI 对话密钥放 `/etc/eduai/ai-chat.env`（`AI_CHAT_APP_KEY=...`），该文件缺失时
   AI 功能不可用，不影响启动。

## 首次部署

```bash
# 1) 开发机：构建打包（fat jar，耗时较长）
bash deploy/package.sh          # 或 pwsh -File deploy/package.ps1

# 2) 上传到根分区
pscp -batch -pw <pass> dist/eduai-app.tar.gz <user>@<host>:/home/<user>/eduai/eduai-app.tar.gz

# 3) 目标机：执行（首次时 deploy.sh 还不存在，用上传的那份）
ssh <user>@<host>
sudo BUNDLE=/home/<user>/eduai/eduai-app.tar.gz bash /home/<user>/eduai/backend/deploy/deploy.sh
```

`deploy.sh` 会：解包到 `$APP_DIR/.deploy-staging` → 备份现役 jar → 替换 →
备份并安装 unit → `daemon-reload` → 重启 → 自检（`19095/actuator/health` 必须 200，
否则打印日志尾部并以非 0 退出，同时给出回滚用的备份名）。

## 升级部署

重复第 1~2 步，第 3 步改用 app 目录里的脚本：

```bash
sudo BUNDLE=/home/<user>/eduai/eduai-app.tar.gz bash /home/<user>/eduai/backend/deploy/deploy.sh
```

脚本幂等：替换用 `install -m 0644`，unit 覆盖安装，服务重启；jar 与 unit 都会**先备份**
（`eduai-backend.jar.bak-<时间戳>` / `eduai-backend.service.bak-<时间戳>`）。

## 验证

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:19095/actuator/health   # 200
ss -ltn | grep 19095
```

端到端（登录真正走的是 oauth 的 tcp 链路）：

```bash
curl -s -X POST http://127.0.0.1:19095/v2/user/login \
  -H 'Content-Type: application/json' \
  --data-binary '{"username":"admin","password":"admin123"}'
# 期望 ok=true 且 data.accessToken 非空；oauth 日志里线程名为 [aio-tcp-iocp]
```

## 回滚

```bash
sudo systemctl stop eduai-backend.service
sudo cp -p /home/kali/eduai/backend/eduai-backend.jar.bak-<时间戳> /home/kali/eduai/backend/eduai-backend.jar
sudo cp -p /etc/systemd/system/eduai-backend.service.bak-<时间戳> /etc/systemd/system/eduai-backend.service
sudo systemctl daemon-reload && sudo systemctl start eduai-backend.service
```

## 已知缺口

- `--enable-preview` 与 `-Xmx2g` 是当前环境下的必需值/经验值，换环境需按 JDK 与内存调整。
- 启动约 70~90s（含 OSGi 与模型加载），`deploy.sh` 的自检窗口按 30×10s 给足。
- 本机内存紧张（多个 java 服务共存），eduai 曾因 OOM 被杀；unit 里的堆上限**不能随手调大**。
- 启动约 70~90s（含 OSGi 与模型加载），自检窗口按 30×10s 给足。
- 依赖的外部账号（如 `AI_CHAT_APP_KEY`）不在本脚本管理范围内。
- 数据源的 `driver-class-name` 由 jar 内 `application.yml` 提供，故 EnvironmentFile 里不需要。
