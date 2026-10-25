# spring-support-sse-starter

## 📖 模块简介

**Server-Sent Events 模块** - 提供基于 SSE 协议的服务端推送功能，实现服务器到客户端的单向实时数据推送。

## ✨ 核心功能

### 📡 服务端推送

- ✅ 实时数据推送
- ✅ 事件流管理
- ✅ 自动重连
- ✅ 心跳保活

### 🎯 应用场景

- ✅ 实时通知
- ✅ 进度推送
- ✅ 日志流
- ✅ 监控数据推送

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-sse-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

```yaml
plugin:
  sse:
    # 是否启用SSE功能
    # 默认: false
    enable: true

    # 心跳间隔（秒）
    heartbeat-interval: 30
```

### 3. 创建 SSE 端点

```java
@RestController
@RequestMapping("/sse")
public class SseController {

    @GetMapping("/events")
    public SseEmitter streamEvents() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 异步发送事件
        executor.execute(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    emitter.send(SseEmitter.event()
                        .name("message")
                        .data("Event " + i));
                    Thread.sleep(1000);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
```

### 4. 前端订阅

```javascript
const eventSource = new EventSource("http://localhost:8080/sse/events");

eventSource.addEventListener("message", (event) => {
  console.log("收到事件:", event.data);
});

eventSource.onerror = (error) => {
  console.error("连接错误:", error);
};
```

## 💡 使用示例

### 推送实时通知

```java
@Service
public class NotificationService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));

        return emitter;
    }

    public void sendNotification(String userId, String message) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(message));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        }
    }
}
```

### 推送任务进度

```java
@RestController
public class TaskController {

    @GetMapping("/task/{id}/progress")
    public SseEmitter getTaskProgress(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter();

        taskService.executeTask(id, progress -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(progress));

                if (progress >= 100) {
                    emitter.complete();
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
```

## ⚙️ 配置说明

```yaml
plugin:
  sse:
    enable: true

    # 心跳间隔（秒）
    heartbeat-interval: 30

    # 超时时间（毫秒）
    timeout: 0 # 0表示永不超时

    # 最大连接数
    max-connections: 1000
```

## 🔌 Socket 协议 SPI 注册

本模块通过 `SocketProvider` 扩展点向 `spring-support-socket-starter` 注册 `sse` 协议，
注册文件为 `src/main/resources/META-INF/extensions/com.chua.socket.support.spi.SocketProvider`：

```properties
sse=com.chua.starter.sse.support.socket.SseSocketProvider
```

> 该文件缺失时协议不会被发现，`SocketConfiguration` 会打日志
> `[Socket] 未找到协议 [sse] 的 SPI 实现` 并跳过 Socket 初始化，
> 进而让所有依赖 `SocketSessionTemplate` 的 Bean 创建失败。
> 新增协议实现时，务必同步补 `META-INF/extensions/<接口全名>`（格式 `别名=实现类全名`）
> 与 `META-INF/native-image/**` 下的 `resource-config.json` / `reflect-config.json`。

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
