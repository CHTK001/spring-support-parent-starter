# 设备数据上报客户端

## 功能概述

`spring-support-report-client-starter` 是一个设备数据上报客户端，使用 `utils-support-oshi-starter` 获取设备硬件和系统信息，并定时推送到监控服务器。

## 主要功能

### 📊 设备数据收集
- **硬件信息**: CPU、内存、磁盘、网络等硬件指标
- **系统信息**: 操作系统、主机名、运行时间等
- **实时指标**: CPU使用率、内存使用率、磁盘使用率等
- **网络指标**: 网络流量、包数量等

### 🚀 自动上报
- **定时推送**: 可配置的推送间隔时间
- **自动重试**: 推送失败时自动重试
- **心跳检测**: 定期发送心跳保持连接
- **故障恢复**: 网络恢复后自动重新连接

### 🔧 灵活配置
- **推送地址**: 可配置监控服务器地址
- **推送频率**: 可配置推送间隔和初始延迟
- **超时设置**: 可配置连接和读取超时时间
- **设备标识**: 可自定义设备ID和名称

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-report-client-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加配置：

```yaml
plugin:
  report:
    client:
      # 启用设备数据上报
      enable: true
      
      # 监控服务器地址
      address: "http://monitor-server:8080"
      
      # 推送间隔（秒）
      push-interval: 30
      
      # 设备标识（可选）
      device-id: "device-001"
      device-name: "生产服务器"
```

### 3. 启动应用

启动Spring Boot应用，客户端会自动开始收集和推送设备数据。

## 配置说明

### 基本配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enable` | boolean | false | 是否启用设备数据上报 |
| `address` | String | - | 监控服务器地址 |
| `receivable-protocol` | String | http | 接收协议 |
| `receivable-port` | Integer | -1 | 接收端口 |

### 推送配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `push-interval` | long | 30 | 推送间隔时间（秒） |
| `initial-delay` | long | 10 | 初始延迟时间（秒） |
| `connect-timeout` | int | 5000 | 连接超时时间（毫秒） |
| `read-timeout` | int | 10000 | 读取超时时间（毫秒） |
| `retry-count` | int | 3 | 重试次数 |

### 设备配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `device-id` | String | 自动生成 | 设备唯一标识 |
| `device-name` | String | 主机名 | 设备名称 |

## 数据格式

### 设备指标数据

```json
{
  "deviceId": "device-001",
  "deviceName": "生产服务器",
  "ipAddress": "192.168.1.100",
  "port": 8080,
  "osName": "Linux",
  "osVersion": "Ubuntu 20.04",
  "hostname": "prod-server-01",
  "cpuUsage": 45.2,
  "cpuCores": 8,
  "memoryUsage": 67.8,
  "totalMemory": 16777216000,
  "usedMemory": 11372748800,
  "diskUsage": 23.1,
  "totalDisk": 1000000000000,
  "networkInBytes": 1024000,
  "networkOutBytes": 512000,
  "loadAverage": "1.25 1.18 1.32",
  "uptime": 86400,
  "temperature": 45.5,
  "online": true,
  "collectTime": "2024-12-19T10:30:00"
}
```

### 设备基本信息

```json
{
  "deviceId": "device-001",
  "deviceName": "生产服务器",
  "ipAddress": "192.168.1.100",
  "port": 8080,
  "osName": "Linux",
  "osVersion": "Ubuntu 20.04",
  "hostname": "prod-server-01",
  "cpuCores": 8,
  "totalMemory": 16777216000,
  "totalDisk": 1000000000000
}
```

## API接口

客户端会向监控服务器推送数据到以下接口：

- `POST /api/v1/monitor/device/metrics` - 推送设备指标数据
- `POST /api/v1/monitor/device/info` - 推送设备基本信息
- `POST /api/v1/monitor/device/ping` - 设备心跳检测

## 监控和日志

### 日志配置

```yaml
logging:
  level:
    com.chua.report.client.starter: DEBUG
    com.chua.oshi.support: DEBUG
```

### 关键日志

- 设备数据收集日志
- 推送成功/失败日志
- 连接状态变化日志
- 重试和错误日志

## 故障排除

### 常见问题

1. **推送失败**
   - 检查网络连接
   - 验证服务器地址配置
   - 查看服务器端口是否开放

2. **数据收集异常**
   - 检查OSHI依赖是否正确
   - 验证系统权限
   - 查看系统兼容性

3. **配置不生效**
   - 确认配置文件路径正确
   - 检查配置项拼写
   - 验证enable参数为true

### 调试方法

1. 启用DEBUG日志查看详细信息
2. 检查网络连通性
3. 验证服务器端接口是否正常
4. 查看系统资源使用情况

## 扩展开发

### 自定义数据收集

可以实现 `DeviceDataService` 接口来自定义数据收集逻辑：

```java
@Service
@Spi("custom")
public class CustomDeviceDataService implements DeviceDataService {
    
    @Override
    public DeviceMetrics getDeviceMetrics() {
        // 自定义数据收集逻辑
        return metrics;
    }
}
```

### 自定义推送方式

可以实现 `ReportPushService` 接口来自定义推送方式：

```java
@Service
@Spi("custom")
public class CustomReportPushService implements ReportPushService {
    
    @Override
    public boolean pushDeviceMetrics(DeviceMetrics metrics) {
        // 自定义推送逻辑
        return true;
    }
}
```

## 性能优化

1. **合理设置推送间隔**: 避免过于频繁的数据推送
2. **网络优化**: 使用连接池和Keep-Alive
3. **数据压缩**: 对大量数据进行压缩传输
4. **异步处理**: 使用异步方式处理数据收集和推送

## 安全考虑

1. **HTTPS传输**: 生产环境建议使用HTTPS
2. **认证授权**: 添加API认证机制
3. **数据加密**: 敏感数据进行加密传输
4. **访问控制**: 限制客户端访问权限
