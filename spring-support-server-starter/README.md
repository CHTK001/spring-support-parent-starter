# spring-support-server-starter

服务器资产与远程运维 starter：主机台账 / 登录身份 / 密钥库、基于 guacd 与直连桥的远程终端、SFTP 文件管理、主机工作台（Docker / Kubernetes）、发布管理、批量执行与计划任务、代理与隧道、邮箱、远控令牌与终端分享、SSE 推送、Arthas 与 Agent 构件投放。

模块定位是「内部 starter 库」：`pom.xml` 中 `spring-boot-maven-plugin` 显式 `skip=true`，不产出 fat-jar，由宿主应用以普通依赖引入后经 `ServerAutoConfiguration` 装配。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 主机台账 | `ServerHostService`、`ServerHostController` | `/ms/server/host` |
| 主机分组 | `ServerGroupService`、`ServerGroupController` | `/ms/server/group` |
| 登录身份 / 密钥库 | `ServerIdentityService`、`ServerKeyService`、`CredentialCipher` | `/ms/server/identity`、`/ms/server/key` |
| 资产导入导出 | `HostArchiveService`、`HostArchiveController` | `/ms/server/host/archive` |
| 硬件探测与指标采集 | `HostDetectProvider`、`HostCollectProvider`、`HostRemoteProvider` | SPI 扩展点（见 5.2） |
| 远程终端（guacd 中转） | `TerminalWebSocketHandler`、`GuacdConnectionFactory`、`GuacdTunnel` | `/ws/ms/server/terminal` |
| SSH 直连终端 | `SshDirectWebSocketHandler`、`ConnectionManager` | `/ws/ms/server/terminal/ssh` |
| VNC 直连桌面 | `VncWebsockifyWebSocketHandler` | `/ws/ms/server/terminal/vnc` |
| Telnet 直连终端 | `TelnetWebSocketHandler`、`TelnetNegotiation` | `/ws/ms/server/terminal/telnet` |
| RDP 直连桌面 | `RdpDirectWebSocketHandler`、`RdpjNative` | `/ws/ms/server/terminal/rdp` |
| SFTP 文件管理 | `SftpService`、`SftpController` | `/ms/server/sftp/{hostId}` |
| 会话审计与在线列表 | `TerminalSessionService`、`TerminalController` | `/ms/server/terminal/online` |
| 主机工作台（Docker / K8s） | `WorkbenchService`、`WorkbenchController` | `/ms/server/host/{hostId}/docker/*`、`/kubernetes/*` |
| 发布管理 | `ReleaseService`、`ReleaseScheduleService`、`ReleaseController` | `/ms/server/release` |
| 批量执行与计划任务 | `ExecService`、`ExecJobScheduler` | `/ms/server/exec` |
| 代理池 | `ProxyService`、`ProxyController` | `/ms/server/proxy` |
| 主机隧道 | `TunnelService`、`TunnelController` | `/ms/server/host/{hostId}/tunnel` |
| 邮箱 | `MailboxService`、`MailboxSseService`、`MailboxController` | `/ms/server/identity/{identityId}/mailbox/*` |
| 远控令牌 | `RemoteTokenService`、`RemoteTokenController` | `/ms/server/remote/token` |
| 终端分享 | `TerminalShareStore`、`TerminalShareController` | `/ms/server/terminal/share` |
| SSE 推送 | `HostAssetsSseHub`、`ReleaseSseHub` | `/ms/server/host/assets/stream`、`/ms/server/release/stream` |
| Arthas 诊断 | `ArthasService`、`ArthasController` | `/ms/server/arthas` |
| Agent 构件投放 | `AgentArtifactService`、`AgentArtifactController`、`HostAgentController` | `/ms/server/agent`、`/ms/server/host/agent` |

协议枚举以 `ProtocolType` 为准，其中只有 `SSH` / `SFTP` / `RDP` / `VNC` 四个值；`isGuacdProtocol()` 对 `SSH`、`RDP`、`VNC` 返回 true，`SFTP` 为 REST 实现，不经 guacd。Telnet 与串口不在该枚举中，分别由 `TelnetWebSocketHandler`、`SerialWebSocketHandler` 承载。

## 二、依赖与引入

Maven 坐标（版本随父工程 `4.0.0.51`）：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-server-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

`pom.xml` 中的关键依赖：

| 依赖 | 用途 | 备注 |
|---|---|---|
| `spring-boot-starter-web`、`spring-boot-starter-websocket` | REST 与远控 WS 端点 | 必选 |
| `spring-support-datasource-starter`、`spring-support-mybatis-starter` | 台账持久化 | 必选，建表脚本在 `db/init` |
| `spring-support-filesystem-starter` | 主机文件页与分片上传 | 必选 |
| `spring-support-socket-sse-starter`、`spring-support-socket-starter` | 邮箱 SSE 等推送 | 必选 |
| `org.apache.guacamole:guacamole-common:1.6.0` | 对接 guacd | `guacamole.version` |
| `com.fazecast:jSerialComm:2.11.0` | 串口终端 | `jserialcomm.version`，缺失会导致整模块编译失败 |
| `utils-support-ssh-starter`、`utils-support-winrm-starter` | SSH / WinRM 通道 | WinRM 与 SSH 按系统类型路由 |
| `utils-support-common-starter` + `utils-support-native-nmap` | 端口扫描 | `scanPorts` 走 `NmapScanner` SPI；原生库由 native-nmap 提供 |
| `utils-support-email-starter` | 邮箱收发 | — |
| `utils-support-git-starter`、`utils-support-maven-starter` | Git 发布与本地构建 | — |
| `utils-support-common-starter` | RDP 直连原生桥契约 | 具体实现由 `utils-support-native-rdp` 部署时提供 |
| `spring-support-ai-starter` | 发布模块 AI 门面 | `optional`，宿主不引入时 AI 入口隐藏 |
| `spring-support-job-starter` | 定时发布 / Git 定时轮询 | `optional`，`ReleaseScheduleServiceImpl` 按 `@ConditionalOnBean(JobOperator.class)` 装配 |

`utils.version` 在父 `pom.xml` 中为 `4.0.0.50`；`lombok` 为 `optional`。

## 三、配置项

两个 `@ConfigurationProperties`：

### 3.1 `plugin.server.support.*`（`ServerProperties`）

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.server.support.enabled` | `true` | 服务信息面板展示旗标；不是模块开关（无 `@ConditionalOnProperty`） |
| `plugin.server.support.credential-key` | `server-support-default-credential-key` | 凭据加密密钥，生产必须覆盖 |
| `plugin.server.support.session-idle-minutes` | `30` | 远控会话空闲超时（分钟） |
| `plugin.server.support.exec-concurrency` | `16` | 批量执行最大并发主机数 |
| `plugin.server.support.guacd.host` | `127.0.0.1` | guacd 地址 |
| `plugin.server.support.guacd.port` | `4822` | guacd 端口 |
| `plugin.server.support.guacd.client-host` | `127.0.0.1:8082` | 浏览器侧 guacamole 客户端回连地址，同域部署可置空 |
| `plugin.server.support.guacd.drive-path` | `/opt/spring-support/drive` | RDP 磁盘映射挂载根目录 |
| `plugin.server.support.guacd.recording-path` | `/opt/spring-support/recordings` | 录屏输出目录 |
| `plugin.server.support.telnet.port` | `23` | 主机台账未配远控端口时的 Telnet 默认端口 |
| `plugin.server.support.telnet.timeout` | `8000` | Telnet 建连超时（毫秒） |

### 3.2 `plugin.server.support.serial.*`（`SerialProperties`）

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.server.support.serial.enabled` | `true` | 串口开关；false 时不启用串口页面与 WS 端点，注册表与查询接口仍可用 |
| `plugin.server.support.serial.baud-rate` | `115200` | 默认波特率 |
| `plugin.server.support.serial.data-bits` | `8` | 数据位（合法值 5/6/7/8） |
| `plugin.server.support.serial.stop-bits` | `1` | 停止位（合法值 1/2） |
| `plugin.server.support.serial.parity` | `0` | 校验位：0 无 / 1 奇 / 2 偶 / 3 标记 / 4 空 |
| `plugin.server.support.serial.flow-control` | `0` | 流控位组合，0 为关闭 |
| `plugin.server.support.serial.read-timeout` | `1000` | 读超时（毫秒） |
| `plugin.server.support.serial.write-timeout` | `0` | 写超时（毫秒），0 表示等待发送完成 |
| `plugin.server.support.serial.exclusive` | `true` | 是否独占打开串口 |
| `plugin.server.support.serial.device-path-prefix` | `/dev/` | 设备路径前缀，仅用于路径规范化 |

除上述静态配置外，`ServerConfigService` 还从 `ms_server_config` 表读取运行期键值，例如 `RemoteTokenService` 使用的 `guacd.client.host`、`guacd.client.port`、`guacd.token.ttl-minutes`（默认 60 分钟）；这些键不在 `@ConfigurationProperties` 中，以代码为准。

## 四、快速开始

第一步，引入依赖（见第二节），并保证宿主已具备数据源；模块的建表脚本位于 `src/main/resources/db/init`（`V2.0__init_all_server.sql` 起，含 `ms_server_host`、`ms_server_identity`、`ms_server_project`、`ms_server_release` 等表）。

第二步，按需覆盖配置：

```yaml
plugin:
  server:
    support:
      credential-key: ${SERVER_CREDENTIAL_KEY}
      session-idle-minutes: 30
      exec-concurrency: 16
      guacd:
        host: 127.0.0.1
        port: 4822
        client-host: 127.0.0.1:8082
      telnet:
        port: 23
        timeout: 8000
```

第三步，启动后访问入口（默认无额外 context-path 时）：

- REST：`/ms/server/host`、`/ms/server/release`、`/ms/server/proxy`、`/ms/server/exec`
- SSE：`GET /ms/server/host/assets/stream`、`GET /ms/server/release/stream`
- 远控 WS：`/ws/ms/server/terminal`（guacd）、`/ws/ms/server/terminal/ssh`、`/ws/ms/server/terminal/vnc`、`/ws/ms/server/terminal/telnet`、`/ws/ms/server/terminal/rdp`

路径常量集中在 `ServerConstants`（`API_PREFIX = "/ms/server"`）。WS 端点由 `TerminalWebSocketConfig#registerWebSocketHandlers` 统一注册，并设置 `setAllowedOriginPatterns("*")`，生产环境应在外层网关或安全配置中收敛来源。

## 五、运行示例

### 5.1 步骤式调用（本模块无链式 API）

本模块源码中没有 Builder 类，也没有 `return this;` 形式的链式 API：全部能力都是 Spring Bean 的接口方法调用，因此下面按步骤书写。

```java
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsExecLog;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.vo.SftpEntryVO;
import com.chua.starter.server.support.service.ExecService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.SftpService;
import com.chua.starter.server.support.service.WorkbenchService;

import java.util.List;
import java.util.Map;

public class ServerOpsDemo {

    private final ServerHostService hostService;
    private final ExecService execService;
    private final SftpService sftpService;
    private final WorkbenchService workbenchService;

    public ServerOpsDemo(ServerHostService hostService, ExecService execService,
                         SftpService sftpService, WorkbenchService workbenchService) {
        this.hostService = hostService;
        this.execService = execService;
        this.sftpService = sftpService;
        this.workbenchService = workbenchService;
    }

    public void inspect(Long hostId) {
        // 1. 连通性测试（按已绑定身份 SSH 登录并回显）
        ReturnResult<String> ping = hostService.testConnect(hostId);
        if (!ping.isSuccess()) {
            return;
        }

        // 2. 硬件探测并回写台账
        ReturnResult<MsServerHost> detected = hostService.detectHardware(hostId);

        // 3. 批量命令（单机超时 30 秒，不绑定计划任务）
        ReturnResult<List<MsExecLog>> logs =
                execService.runCommand(List.of(hostId), "uname -a", 30, null);

        // 4. 目录列表（identityId 传 null 表示按优先级取已绑定身份）
        ReturnResult<List<SftpEntryVO>> entries = sftpService.list(hostId, null, "/opt");

        // 5. 工作台：容器列表（含已停止）
        ReturnResult<List<Map<String, Object>>> containers =
                workbenchService.listDockerContainers(hostId, true);
    }
}
```

所有方法签名以源码为准：`ServerHostService#testConnect(Long)`、`#detectHardware(Long)`、`ExecService#runCommand(List<Long>, String, Integer, Long)`、`SftpService#list(Long, Long, String)`、`WorkbenchService#listDockerContainers(Long, Boolean)`。

远控令牌签发同样是步骤式：

```java
// RemoteTokenService 为普通组件，不是接口
String token = remoteTokenService.issueToken("ssh", "10.0.0.12", 22, "root", password);
String url = remoteTokenService.buildSessionUrl(token);
```

`issueToken(String protocol, String host, Integer port, String username, String password)` 与 `buildSessionUrl(String token)` 均在 `RemoteTokenService` 中。

### 5.2 SPI 扩展示例：新增主机探测 Provider

以 `HostDetectProvider` 为例，真实方法签名为：

```java
String osType();

default boolean requiresCredential() { ... }

ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential);

default ReturnResult<String> test(MsServerHost host) { ... }
```

第一步，实现接口：

```java
package com.example.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostDetectProvider;

@Spi(value = "openbsd", order = 100)
public class OpenBsdHostDetectProvider implements HostDetectProvider {

    @Override
    public String osType() {
        return "OPENBSD";
    }

    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        // 采集逻辑略：可参考 LinuxHostDetectProvider 借 ConnectionManager 执行命令
        return ReturnResult.ok(host, "已采集");
    }
}
```

第二步，在宿主工程的 `src/main/resources` 下登记扩展点文件，文件名就是接口全限定名，每行一个实现类：

```text
META-INF/extensions/com.chua.starter.server.support.spi.HostDetectProvider
```

```text
com.example.server.OpenBsdHostDetectProvider
```

第三步，生效方式：`HostDetectProviderRegistry` 首次解析时遍历扩展点定义，按 `osType()` 建索引（同一实现类只登记一次），`ServerHostServiceImpl` 的硬件探测按主机的 `serverHostOsType`（或采集方式）路由到对应实现。登记成功时日志会输出 `[detect] 注册探测 Provider: osType=..., impl=...`；无任何实现时输出「HostDetectProvider 扩展点无任何实现，硬件探测不可用」。

本模块已经这样登记了 6 个探测实现：`LinuxHostDetectProvider`、`WindowsHostDetectProvider`、`MacosHostDetectProvider`、`FreebsdHostDetectProvider`、`AixHostDetectProvider`、`SolarisHostDetectProvider`。

其它已开放的扩展点（同样位于 `META-INF/extensions`）：`HostCollectProvider`（`ScheduledHostCollectProvider`、`OpenSshHostCollectProvider`、`AgentHostCollectProvider`、`HttpHostCollectProvider`、`PrometheusHostCollectProvider`）、`HostRemoteProvider`（`PosixSshHostRemoteProvider`、`WindowsHostRemoteProvider`）、`TerminalSessionChannel`（`GuacdTerminalSessionChannel`、`SshDirectTerminalSessionChannel`、`VncDirectTerminalSessionChannel`、`TelnetDirectTerminalSessionChannel`、`RdpDirectTerminalSessionChannel`）、`SystemServiceProvider`、`ProjectTypeProvider`、`ProjectPlatformProvider`、`PackageManagerProvider`。

## 六、与其它 starter 的关系

依赖方向（本模块 → 依赖）：

- 基础：`spring-support-common-starter`、`spring-support-datasource-starter`、`spring-support-mybatis-starter`
- 能力：`spring-support-filesystem-starter`、`spring-support-socket-sse-starter`、`spring-support-socket-starter`、`spring-support-swagger-starter`
- 可选：`spring-support-ai-starter`（发布 AI 门面）、`spring-support-job-starter`（定时发布）
- utils 侧：`utils-support-ssh-starter`、`utils-support-winrm-starter`、`utils-support-common-starter`、`utils-support-guacamole-starter`、`utils-support-email-starter`、`utils-support-datasearch-starter`、`utils-support-git-starter`、`utils-support-maven-starter`

反向依赖本模块的 starter（父工程内）：

- `spring-support-monitor-starter`
- `spring-support-docker-starter`

因此本模块属于「资产与运维基座」，改动其公开服务接口会同时影响上述两个模块。

## 七、已知限制

- **RDP 直连依赖可选原生库**：本模块只依赖 `utils-support-common-starter` 契约，实际实现由部署时加入的 `utils-support-native-rdp` 提供。未部署时 `RdpjNative.exists()` 返回 false，直连被拒绝，提示语为 `RdpjNative.UNSUPPORTED_MESSAGE`：「当前环境不支持 RDP 直连：未部署 RDP 原生库（utils-support-native-rdp）」。
- **guacd 是外部进程**：`/ws/ms/server/terminal` 依赖可用的 guacd（默认 `127.0.0.1:4822`），`client-host` 需与浏览器实际回连地址一致，否则前端连不上隧道。
- **RDP 磁盘映射与录屏需真实目录**：`guacd.drive-path`、`guacd.recording-path` 不会自动创建到目标主机上，挂载失败由 guacd 侧报错。
- **定时发布是可选的**：`ReleaseScheduleServiceImpl` 标注 `@ConditionalOnBean(JobOperator.class)`，宿主未引入 `spring-support-job-starter`（无 `JobOperator` Bean）时该实现不装配，控制器通过 `ObjectProvider` 可选调用。
- **AI 入口是可选的**：模块只依赖 `ReleaseAiCapability` 门面，宿主未引入 `spring-support-ai-starter` 时 AI 入口自动隐藏。
- **`enabled` 不是开关**：`plugin.server.support.enabled` 仅影响服务信息面板展示；本模块没有 `@ConditionalOnProperty`，置 false 不会卸载任何 Bean。
- **凭据密钥必须替换**：`credential-key` 默认值仅供本地开发，生产环境应通过配置中心或环境变量注入。
- **端口扫描依赖本机 nmap**：`scanPorts` 只扫描常用端口集，同步接口不接受 1~65535 全端口范围。
- **串口链路**：`SerialWebSocketHandler`、`SerialSessionRegistry`、`SerialCommListingService` 等组件存在，但本模块的 `TerminalWebSocketConfig` 只注册了上述 5 个远控 WS 端点，串口端点注册方式以代码为准。
- **构建约束**：`maven-compiler-plugin` 使用 `source/target 25`，`spring-boot-maven-plugin` 已 `skip`，本模块不可单独作为可执行 jar 运行。
