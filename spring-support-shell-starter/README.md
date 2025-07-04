# Spring Support Shell Starter

SSH Shell集成模块，为Spring Boot应用提供完整的SSH Shell功能和登录追踪支持。

## 功能特性

### 🚀 核心功能
- **SSH Shell服务** - 基于ssh-shell-spring-boot-starter的SSH Shell功能
- **登录追踪** - 使用Redis记录30天内的IP登录信息和认证次数
- **内置命令** - 提供help、日志等级修改等基础命令
- **安全认证** - 支持用户名密码认证和公钥认证
- **会话管理** - 提供会话管理和监控功能

### 📊 监控功能
- **登录统计** - 详细的登录统计信息和趋势分析
- **系统监控** - 实时系统信息、内存使用、线程状态
- **会话追踪** - 活跃会话监控和历史记录
- **安全防护** - IP锁定、失败次数限制、白名单支持

### 🛠️ 内置命令

#### 系统命令
- `help` / `h` / `?` - 显示帮助信息
- `version` / `v` - 显示版本信息
- `exit` / `quit` - 退出Shell
- `clear` - 清屏

#### 日志管理
- `log-level` / `ll` - 查看或修改日志等级
- `log-info` / `li` - 显示日志配置信息
- `log-reset` / `lr` - 重置日志等级

#### 登录统计
- `login-stats` / `ls` - 显示登录统计信息
- `login-history` / `lh` - 显示登录历史记录
- `active-sessions` / `as` - 显示当前活跃会话

#### 系统监控
- `system-info` / `si` - 显示系统信息
- `memory-info` / `mi` - 显示内存使用情况
- `thread-info` / `ti` - 显示线程信息

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-shell-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加配置：

```yaml
# SSH Shell 配置
ssh:
  shell:
    enable: true
    host: 0.0.0.0
    port: 2222
    user: admin
    password: admin123
    authentication:
      password: true
      public-key: false
    session:
      timeout: 3600
      max-sessions: 10

# 登录追踪配置
shell:
  login:
    tracking:
      enabled: true
      redis-prefix: "shell:login:"
      retention-days: 30
      detailed: true
    security:
      max-failed-attempts: 5
      lockout-duration: 30
      ip-whitelist-enabled: false
      ip-whitelist:
        - 127.0.0.1
        - localhost
```

### 3. 启动应用

启动Spring Boot应用后，可以通过SSH客户端连接：

```bash
ssh admin@localhost -p 2222
```

## 使用示例

### 查看帮助信息
```bash
shell> help
=== Spring Support Shell 帮助系统 ===

系统命令:
  help, h, ?           - 显示帮助信息
  exit, quit           - 退出Shell
  clear                - 清屏
  history              - 显示命令历史

日志管理命令:
  log-level            - 查看或修改日志等级
  log-info             - 显示日志配置信息

登录统计命令:
  login-stats          - 显示登录统计信息
  login-history        - 显示登录历史记录
  active-sessions      - 显示当前活跃会话
```

### 修改日志等级
```bash
shell> log-level com.chua DEBUG
日志等级修改成功:
Logger: com.chua
New Level: DEBUG
```

### 查看登录统计
```bash
shell> login-stats -d 7
=== 登录统计信息 (最近7天) ===

基础统计:
  总登录次数: 25
  成功登录: 23
  失败登录: 2
  唯一IP数: 3
  唯一用户数: 2
  当前活跃会话: 1
  成功率: 92.0%
```

### 查看系统信息
```bash
shell> system-info memory
=== 内存信息 ===

JVM内存:
  最大内存: 4.0 GB
  总内存: 1.5 GB
  已用内存: 512.0 MB
  空闲内存: 1.0 GB
  内存使用率: 34.1%
```

## 配置说明

### SSH Shell配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ssh.shell.enable` | true | 是否启用SSH Shell |
| `ssh.shell.host` | 0.0.0.0 | SSH服务器监听地址 |
| `ssh.shell.port` | 2222 | SSH服务器端口 |
| `ssh.shell.user` | admin | 默认用户名 |
| `ssh.shell.password` | admin123 | 默认密码 |
| `ssh.shell.session.timeout` | 3600 | 会话超时时间（秒） |
| `ssh.shell.session.max-sessions` | 10 | 最大会话数 |

### 登录追踪配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `shell.login.tracking.enabled` | true | 是否启用登录追踪 |
| `shell.login.tracking.redis-prefix` | shell:login: | Redis key前缀 |
| `shell.login.tracking.retention-days` | 30 | 数据保留天数 |
| `shell.login.tracking.detailed` | true | 是否记录详细信息 |

### 安全配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `shell.login.security.max-failed-attempts` | 5 | 最大失败尝试次数 |
| `shell.login.security.lockout-duration` | 30 | 锁定时间（分钟） |
| `shell.login.security.ip-whitelist-enabled` | false | 是否启用IP白名单 |

## 扩展开发

### 自定义命令

创建自定义命令类：

```java
@SshShellComponent
@ShellCommandGroup("自定义命令")
public class CustomCommand {

    @ShellMethod(value = "自定义命令示例", key = {"custom", "c"})
    public String customCommand(@ShellOption(defaultValue = "world") String name) {
        return "Hello, " + name + "!";
    }
}
```

### 自定义登录追踪

实现 `LoginTrackingService` 接口：

```java
@Service
public class CustomLoginTrackingService implements LoginTrackingService {
    // 实现自定义的登录追踪逻辑
}
```

## 注意事项

1. **安全性**：生产环境请修改默认用户名和密码
2. **端口冲突**：确保SSH端口不与其他服务冲突
3. **Redis依赖**：登录追踪功能需要Redis支持
4. **防火墙**：确保SSH端口在防火墙中开放
5. **资源消耗**：监控命令可能消耗一定系统资源

## 版本历史

- **4.0.0.32** - 初始版本
  - 基础SSH Shell功能
  - 登录追踪和统计
  - 内置管理命令
  - 系统监控功能

## 许可证

本项目采用 Apache License 2.0 许可证。
