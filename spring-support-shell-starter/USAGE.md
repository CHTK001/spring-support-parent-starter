# Spring Support Shell Starter 使用指南

## 快速集成

### 1. 添加依赖

在你的Spring Boot项目中添加以下依赖：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-shell-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>

<!-- Redis依赖（登录追踪功能需要） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 2. 基础配置

在 `application.yml` 中添加最小配置：

```yaml
# SSH Shell 基础配置
ssh:
  shell:
    enable: true
    port: 2222
    user: admin
    password: your-secure-password

# Redis 配置
spring:
  redis:
    host: localhost
    port: 6379
```

### 3. 启动应用

启动Spring Boot应用后，SSH Shell服务会自动启动。

### 4. 连接SSH Shell

使用SSH客户端连接：

```bash
# 使用命令行SSH客户端
ssh admin@localhost -p 2222

# 使用PuTTY等图形化客户端
# 主机: localhost
# 端口: 2222
# 用户名: admin
# 密码: your-secure-password
```

## 常用命令示例

### 系统帮助
```bash
# 显示所有可用命令
shell> help

# 显示特定命令的帮助
shell> help log-level
```

### 日志管理
```bash
# 查看当前root日志等级
shell> log-level

# 设置root日志等级为DEBUG
shell> log-level DEBUG

# 设置特定包的日志等级
shell> log-level com.chua INFO

# 查看日志配置信息
shell> log-info

# 重置日志等级
shell> log-reset
```

### 登录统计
```bash
# 查看最近7天的登录统计
shell> login-stats

# 查看最近30天的登录统计
shell> login-stats -d 30

# 查看特定IP的登录统计
shell> login-stats -i 192.168.1.100

# 查看登录次数最多的前5个IP
shell> login-stats -t 5

# 查看登录历史记录
shell> login-history

# 查看特定IP的登录历史
shell> login-history -i 192.168.1.100

# 查看当前活跃会话
shell> active-sessions
```

### 系统监控
```bash
# 查看所有系统信息
shell> system-info

# 只查看内存信息
shell> system-info memory

# 只查看操作系统信息
shell> system-info os

# 查看详细内存使用情况
shell> memory-info

# 查看线程信息
shell> thread-info
```

## 高级配置

### 完整配置示例

```yaml
# SSH Shell 完整配置
ssh:
  shell:
    enable: true
    host: 0.0.0.0
    port: 2222
    authentication:
      password: true
      public-key: false
    user: admin
    password: your-secure-password
    session:
      timeout: 3600
      max-sessions: 10
    commands:
      built-in: true
      history:
        enable: true
        size: 1000
    audit:
      enable: true
      file: logs/ssh-shell-audit.log

# 登录追踪完整配置
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
      ip-whitelist-enabled: true
      ip-whitelist:
        - 127.0.0.1
        - 192.168.1.0/24
        - 10.0.0.0/8

# Redis 配置
spring:
  redis:
    host: localhost
    port: 6379
    password: redis-password
    database: 0
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms

# 监控端点配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,loggers
  endpoint:
    health:
      show-details: always
    loggers:
      enabled: true
```

### 安全配置建议

1. **修改默认密码**
```yaml
ssh:
  shell:
    user: your-username
    password: your-strong-password
```

2. **启用IP白名单**
```yaml
shell:
  login:
    security:
      ip-whitelist-enabled: true
      ip-whitelist:
        - 192.168.1.0/24  # 允许内网访问
        - 10.0.0.100      # 允许特定IP
```

3. **配置失败锁定**
```yaml
shell:
  login:
    security:
      max-failed-attempts: 3
      lockout-duration: 60  # 锁定60分钟
```

## 自定义扩展

### 创建自定义命令

```java
@SshShellComponent
@ShellCommandGroup("业务命令")
public class BusinessCommand {

    @ShellMethod(value = "查询用户信息", key = {"login-info", "ui"})
    public String userInfo(@ShellOption(value = {"-u", "--username"}) String username) {
        // 实现用户信息查询逻辑
        return "用户信息: " + username;
    }

    @ShellMethod(value = "清理缓存", key = {"clear-cache", "cc"})
    public String clearCache(@ShellOption(value = {"-t", "--type"}, defaultValue = "all") String type) {
        // 实现缓存清理逻辑
        return "缓存清理完成: " + type;
    }
}
```

### 自定义登录追踪

```java
@Service
@Primary
public class CustomLoginTrackingService implements LoginTrackingService {
    
    @Override
    public void recordLogin(String ip, String username, boolean success, String userAgent) {
        // 实现自定义的登录记录逻辑
        // 例如：记录到数据库、发送通知等
    }
    
    // 实现其他接口方法...
}
```

## 故障排除

### 常见问题

1. **SSH连接被拒绝**
   - 检查端口是否被占用
   - 检查防火墙设置
   - 确认SSH Shell已启用

2. **登录追踪不工作**
   - 检查Redis连接
   - 确认Redis配置正确
   - 查看应用日志

3. **命令执行失败**
   - 检查权限设置
   - 查看错误日志
   - 确认依赖是否完整

### 调试配置

```yaml
# 启用调试日志
logging:
  level:
    com.chua.starter.shell: DEBUG
    com.github.fonimus.ssh.shell: DEBUG
```

### 健康检查

```bash
# 检查SSH Shell状态
shell> system-info

# 检查Redis连接
shell> login-stats

# 检查内存使用
shell> memory-info
```

## 最佳实践

1. **生产环境安全**
   - 使用强密码
   - 启用IP白名单
   - 定期更换密码
   - 监控登录日志

2. **性能优化**
   - 合理设置会话超时
   - 限制最大会话数
   - 定期清理过期数据

3. **监控告警**
   - 监控失败登录次数
   - 设置异常IP告警
   - 监控系统资源使用

4. **日志管理**
   - 启用审计日志
   - 定期备份日志
   - 设置日志轮转
