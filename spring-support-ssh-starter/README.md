# spring-support-ssh-starter

基于 Apache SSHD 的 SSH 服务端 starter：随 Spring Boot 启动一个可登录的 SSH/SFTP 服务端。
默认关闭，必须显式设置 `plugin.ssh.server.enabled=true`。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| SSH 服务端生命周期 | `SshServerService` | `InitializingBean` / `DisposableBean` |
| 自动配置 | `SshAutoConfiguration` | `plugin.ssh.server.enabled=true` |
| 配置绑定 | `SshServerProperties` | `plugin.ssh.server.*` |
| 密码认证 | `SshServerService#configureAuthentication` | `PasswordAuthenticator` |
| 公钥认证 | `SshServerService#configureAuthentication` | `AuthorizedKeysAuthenticator` |
| Shell 会话 | `SshServerService#startSshServer` | `ProcessShellFactory` |
| SFTP 文件传输 | `SshServerService#configureFileTransfer` | `SftpSubsystemFactory` |
| 端口转发 | — | `AcceptAllForwardingFilter` |
| 运行状态查询 | `SshServerService#isRunning` / `#getActiveSessionCount` | 直接调用 Bean |
| 服务信息登记 | `sshServiceInfoContributor` | 控制台模块能力 |

实际生效的能力：

- 用户名 + 密码登录（单账号，凭据来自配置）；
- 公钥登录（`public-key-file` 指向 `authorized_keys` 格式文件）；
- Shell 命令执行（交给操作系统进程）；
- SFTP：虚拟根目录限制在 `file-transfer.root-directory`；
- 本地/远程端口转发（无条件接受）；
- 应用关闭时停止 SSHD。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-ssh-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `org.apache.sshd:sshd-core` | SSH 服务端核心 |
| `org.apache.sshd:sshd-sftp` | SFTP 子系统 |
| `org.apache.sshd:sshd-scp` | SCP 相关类（当前未装配服务端命令工厂） |
| `spring-boot-starter` | 自动配置与容器 |
| `spring-support-common-starter` | 模块日志 `ModuleLog` 与服务信息登记 |

SSHD 版本由父 pom 的依赖管理决定，本模块未单独钉版本。

## 三、配置项

配置前缀：**`plugin.ssh.server`**（注意不是 `ssh.server`）。

### 3.1 服务端

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.ssh.server.enabled` | `false` | 是否真正启动 SSHD；**必须显式设为 true** |
| `plugin.ssh.server.enable` | `false` | 仅声明字段，运行期未读取 |
| `plugin.ssh.server.host` | `0.0.0.0` | 监听地址 |
| `plugin.ssh.server.port` | `2222` | 监听端口 |

### 3.2 认证

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.ssh.server.authentication.password` | `true` | 启用密码认证 |
| `plugin.ssh.server.authentication.public-key` | `false` | 启用公钥认证 |
| `plugin.ssh.server.authentication.username` | `admin` | 唯一允许的用户名 |
| `plugin.ssh.server.authentication.user-password` | 空串 | 密码；默认空串，不配置则无法登录 |
| `plugin.ssh.server.authentication.public-key-file` | 无 | `authorized_keys` 文件路径 |
| `plugin.ssh.server.authentication.private-key-file` | 无 | 仅声明，未使用 |

### 3.3 会话

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.ssh.server.session.timeout` | `3600` | 会话超时（秒），当前仅打印日志 |
| `plugin.ssh.server.session.max-sessions` | `10` | 最大会话数，当前仅打印日志 |
| `plugin.ssh.server.session.idle-timeout` | `1800` | 空闲超时（秒），当前未使用 |

### 3.4 文件传输

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.ssh.server.file-transfer.scp-enabled` | `true` | 仅声明，当前未注册 SCP 命令工厂 |
| `plugin.ssh.server.file-transfer.sftp-enabled` | `true` | 是否注册 SFTP 子系统 |
| `plugin.ssh.server.file-transfer.root-directory` | `/tmp` | SFTP 虚拟根目录 |
| `plugin.ssh.server.file-transfer.max-file-size` | `104857600` | 仅声明，当前未做大小校验 |

### 3.5 安全

`plugin.ssh.server.security.*` 下的 `allowed-ips`、`denied-ips`、`max-login-attempts`、
`lockout-duration`、`host-key-verification` 均有字段与默认值，但**当前运行期未接入**，
不要据此判断访问已被限制（详见「七、已知限制」）。

## 四、快速开始

**第一步：引入依赖**（见上）。

**第二步：显式开启并配置凭据**

```yaml
plugin:
  ssh:
    server:
      enabled: true
      host: 0.0.0.0
      port: 2222
      authentication:
        password: true
        username: admin
        user-password: change-me
      file-transfer:
        sftp-enabled: true
        root-directory: /data/sftp
```

**第三步：连接验证**

```bash
ssh admin@127.0.0.1 -p 2222
sftp -P 2222 admin@127.0.0.1
```

启动成功后日志形如：

```
[SSH] 服务已启动 0.0.0.0:2222 [开启]
[SSH] SFTP文件传输 [开启] 根目录: /data/sftp
```

（`开启` 由 `ModuleLog#enabled()` 着色输出。）未开启时日志为 `[SSH] 服务状态 [关闭]`，且不会占用端口。

## 五、运行示例

### 5.1 调用示例

本模块**无链式 API**，也没有对外 REST 接口；使用方式是配置 + 注入 `SshServerService` 查询状态。

```java
@Service
public class SshStatusDemo {

    private final SshServerService sshServerService;

    public SshStatusDemo(SshServerService sshServerService) {
        this.sshServerService = sshServerService;
    }

    public String status() {
        boolean running = sshServerService.isRunning();
        int active = sshServerService.getActiveSessionCount();
        return "running=" + running + ", sessions=" + active;
    }
}
```

SFTP 常用操作（客户端侧）：

```bash
sftp -P 2222 admin@your-server
put local-file.txt
get remote-file.txt
ls
```

```bash
scp -P 2222 local-file.txt admin@your-server:/data/sftp/
scp -P 2222 admin@your-server:/data/sftp/remote-file.txt ./
```

> 上述 `scp` 能否成功取决于客户端实现：若客户端走 SFTP 协议则可用；
> 纯 SCP 协议（`scp -O`）当前没有服务端命令工厂承载。

### 5.2 SPI 扩展示例

**本模块不提供 SPI 扩展点**：没有 `META-INF/extensions`，也没有可替换的认证/文件系统策略接口。
认证方式与文件系统由 `SshServerService` 内部直接装配，要扩展只能替换该 Bean 或自行实现 SSHD 配置。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-common-starter` | 本模块依赖 | `ModuleLog`、`ServiceInfoContributor` |
| `utils-support-ssh-starter` | 独立模块，非本模块依赖 | SSH **客户端**能力（连接远程主机、远程文件读取） |
| `spring-support-filesystem-starter` | 间接使用 utils 层 | 其远程文件预览用的是 `utils-support-ssh-starter`，不是本模块 |
| `spring-support-tunnel-ngrok-starter` | 可组合，无依赖 | ngrok 的 `tcp` 模式可把 2222 暴露到公网（需 reserve 地址） |

注意区分：

- **本模块 = SSH 服务端**（让别人登录你的 Spring 应用所在机器）；
- **`utils-support-ssh-starter` = SSH 客户端**（让你的应用去连别人的机器）。

两者包名不同、职责相反，不要混用依赖。

## 七、已知限制

- **默认不启动**：`enabled` 默认 `false`，只引入依赖不会开放端口；`enable` 字段不参与判断。
- **单账号**：密码认证只比较 `authentication.username` 与 `user-password` 两个字面量，
  没有用户库、角色或权限模型；`user-password` 默认空串，必须显式配置。
- **安全配置未接入**：`allowed-ips` / `denied-ips` / `max-login-attempts` /
  `lockout-duration` / `host-key-verification` 均未在认证流程中读取，配置了也不会限制访问。
- **会话限制未接入**：`timeout` / `idle-timeout` / `max-sessions` 只出现在 debug 日志，
  不会真正断开空闲会话或限制并发数。
- **SCP 未装配**：pom 引了 `sshd-scp`，但服务端只注册了 `SftpSubsystemFactory`，
  没有 `ScpCommandFactory`；`scp-enabled` 字段也不参与装配。
- **文件大小限制未生效**：`max-file-size` 当前没有校验逻辑。
- **主机密钥落在工作目录**：`SimpleGeneratorHostKeyProvider(Paths.get("hostkey.ser"))`，
  首次启动生成 `hostkey.ser`；换工作目录会换主机密钥，客户端会看到 host key 变更告警。
- **Shell 是操作系统进程**：`ProcessShellFactory` 直接起系统 shell，
  权限边界取决于运行应用的 OS 用户，不提供命令白名单。
- **端口转发全开放**：`AcceptAllForwardingFilter` 接受所有本地/远程转发请求。
- 生产环境务必：修改默认用户名与密码、限制监听地址或前置防火墙、
  把 `root-directory` 指到专用目录并收紧 OS 权限、不要把 2222 直接暴露到公网。
