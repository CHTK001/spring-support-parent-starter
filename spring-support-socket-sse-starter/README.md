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

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
