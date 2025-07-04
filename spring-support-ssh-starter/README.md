# Spring Support SSH Starter

SSH服务端集成模块，为Spring Boot应用提供完整的SSH服务端功能。

## 功能特性

### 🚀 核心功能
- **SSH服务端** - 基于Apache SSHD的SSH服务端功能
- **用户认证** - 支持用户名密码认证和公钥认证
- **命令执行** - 支持远程命令执行和Shell会话
- **文件传输** - 支持SCP和SFTP文件传输协议
- **端口转发** - 支持本地和远程端口转发
- **会话管理** - 提供会话管理和监控功能

### 🔒 安全功能
- **IP访问控制** - 支持IP白名单和黑名单
- **登录限制** - 支持最大登录尝试次数和锁定机制
- **主机密钥验证** - 支持主机密钥验证
- **会话超时** - 支持会话和空闲超时配置

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-ssh-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加配置：

```yaml
# SSH服务端配置
ssh:
  server:
    enabled: true
    host: 0.0.0.0
    port: 2222
    authentication:
      password: true
      username: admin
      user-password: your-secure-password
    file-transfer:
      sftp-enabled: true
      root-directory: /tmp
```

### 3. 启动应用

启动Spring Boot应用后，SSH服务端将自动启动。

### 4. 连接测试

使用SSH客户端连接：

```bash
# SSH连接
ssh admin@localhost -p 2222

# SFTP连接
sftp -P 2222 admin@localhost
```

## 配置说明

### SSH服务端配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ssh.server.enabled` | true | 是否启用SSH服务端 |
| `ssh.server.host` | 0.0.0.0 | SSH服务器监听地址 |
| `ssh.server.port` | 2222 | SSH服务器端口 |

### 认证配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ssh.server.authentication.password` | true | 是否启用密码认证 |
| `ssh.server.authentication.public-key` | false | 是否启用公钥认证 |
| `ssh.server.authentication.username` | admin | 默认用户名 |
| `ssh.server.authentication.user-password` | admin123 | 默认密码 |

### 会话配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ssh.server.session.timeout` | 3600 | 会话超时时间（秒） |
| `ssh.server.session.max-sessions` | 10 | 最大会话数 |
| `ssh.server.session.idle-timeout` | 1800 | 空闲超时时间（秒） |

### 文件传输配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ssh.server.file-transfer.scp-enabled` | true | 是否启用SCP |
| `ssh.server.file-transfer.sftp-enabled` | true | 是否启用SFTP |
| `ssh.server.file-transfer.root-directory` | /tmp | 文件传输根目录 |
| `ssh.server.file-transfer.max-file-size` | 104857600 | 最大文件大小（字节） |

## 使用示例

### 基础SSH连接
```bash
ssh admin@your-server -p 2222
```

### SFTP文件传输
```bash
# 连接SFTP
sftp -P 2222 admin@your-server

# 上传文件
put local-file.txt

# 下载文件
get remote-file.txt

# 列出文件
ls
```

### SCP文件传输
```bash
# 上传文件
scp -P 2222 local-file.txt admin@your-server:/tmp/

# 下载文件
scp -P 2222 admin@your-server:/tmp/remote-file.txt ./
```

## 安全建议

1. **修改默认密码**
```yaml
ssh:
  server:
    authentication:
      username: your-username
      user-password: your-strong-password
```

2. **启用IP白名单**
```yaml
ssh:
  server:
    security:
      allowed-ips:
        - 192.168.1.0/24
        - 10.0.0.100
```

3. **配置登录限制**
```yaml
ssh:
  server:
    security:
      max-login-attempts: 3
      lockout-duration: 300
```

## 注意事项

1. 生产环境请务必修改默认用户名和密码
2. 建议启用IP白名单限制访问
3. 定期检查SSH连接日志
4. 考虑使用公钥认证替代密码认证
5. 合理设置文件传输根目录权限

## 版本历史

- 4.0.0.32 - 初始版本，提供基础SSH服务端功能
