# Socket 二进制数据传输支持

## 概述

Socket模块现已支持真正的二进制数据传输功能，可用于传输文件、图片、视频流等大型二进制数据，避免了Base64编码带来的性能开销。

## 功能特性

- ✅ 单会话二进制数据发送
- ✅ 广播二进制数据到所有客户端
- ✅ 向指定用户发送二进制数据
- ✅ 自动处理连接状态检查
- ✅ 异常处理和容错机制

## API 使用

### 1. SocketSession - 单会话发送

通过 `SocketSession` 发送二进制数据到指定客户端：

```java
@Component
public class BinaryDataService {
    
    @Autowired
    private SocketSessionTemplate socketSessionTemplate;
    
    /**
     * 向指定会话发送二进制数据
     */
    public void sendToSession(String sessionId, byte[] imageData) {
        SocketSession session = socketSessionTemplate.getSession(sessionId);
        if (session != null) {
            // 发送二进制图片数据
            session.sendBinary("image:data", imageData);
        }
    }
}
```

### 2. SocketSessionTemplate - 模板方法

#### 2.1 发送到指定会话

```java
// 向指定会话ID发送二进制数据
socketSessionTemplate.sendBinary(sessionId, "file:download", fileBytes);
```

#### 2.2 广播到所有客户端

```java
// 广播二进制数据到所有在线客户端
byte[] videoFrame = captureVideoFrame();
socketSessionTemplate.broadcastBinary("video:frame", videoFrame);
```

#### 2.3 发送到指定用户

```java
// 向指定用户的所有会话发送二进制数据
String userId = "user123";
byte[] pdfData = generatePdfReport();
socketSessionTemplate.sendBinaryToUser(userId, "report:pdf", pdfData);
```

### 3. 在 SocketListener 中使用

在自定义的 Socket 监听器中响应客户端请求并发送二进制数据：

```java
@Component
@Slf4j
public class FileTransferListener implements SocketListener {
    
    @Autowired
    @Lazy
    private SocketSessionTemplate socketSessionTemplate;
    
    /**
     * 处理文件下载请求
     */
    @OnEvent("file:request")
    public void onFileRequest(SocketSession session, JsonObject data) {
        try {
            String filePath = data.getString("filePath");
            
            // 读取文件内容
            byte[] fileContent = readFile(filePath);
            
            // 发送二进制文件数据
            session.sendBinary("file:data", fileContent);
            
            log.info("文件发送成功: path={}, size={}", filePath, fileContent.length);
        } catch (Exception e) {
            log.error("文件发送失败: {}", e.getMessage(), e);
            session.send("file:error", ReturnResult.illegal("文件读取失败"));
        }
    }
    
    private byte[] readFile(String filePath) throws IOException {
        return Files.readAllBytes(Paths.get(filePath));
    }
}
```

## 完整示例

### 服务端实现 - 屏幕共享

```java
@Component
@Slf4j
public class ScreenShareListener implements SocketListener {
    
    @Autowired
    @Lazy
    private SocketSessionTemplate socketSessionTemplate;
    
    private final Map<Integer, ScheduledFuture<?>> screenCaptureTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    
    /**
     * 开始屏幕共享
     */
    @OnEvent("screen:share:start")
    public void onStartScreenShare(SocketSession session, JsonObject data) {
        try {
            Integer shareId = data.getInteger("shareId");
            int fps = data.getIntValue("fps", 30); // 默认30帧
            
            // 定时捕获屏幕并发送
            ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
                try {
                    byte[] screenData = captureScreen();
                    
                    // 广播屏幕数据到所有客户端
                    socketSessionTemplate.broadcastBinary("screen:frame", screenData);
                    
                } catch (Exception e) {
                    log.error("屏幕捕获失败: {}", e.getMessage());
                }
            }, 0, 1000 / fps, TimeUnit.MILLISECONDS);
            
            screenCaptureTasks.put(shareId, task);
            session.send("screen:share:started", ReturnResult.success("屏幕共享已开始"));
            
        } catch (Exception e) {
            log.error("启动屏幕共享失败: {}", e.getMessage(), e);
            session.send("screen:share:error", ReturnResult.illegal("启动失败"));
        }
    }
    
    /**
     * 停止屏幕共享
     */
    @OnEvent("screen:share:stop")
    public void onStopScreenShare(SocketSession session, JsonObject data) {
        Integer shareId = data.getInteger("shareId");
        ScheduledFuture<?> task = screenCaptureTasks.remove(shareId);
        if (task != null) {
            task.cancel(false);
            session.send("screen:share:stopped", ReturnResult.success("屏幕共享已停止"));
        }
    }
    
    /**
     * 捕获屏幕（示例实现）
     */
    private byte[] captureScreen() throws Exception {
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage capture = robot.createScreenCapture(screenRect);
        
        // 转换为JPEG字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(capture, "jpg", baos);
        return baos.toByteArray();
    }
}
```

### 客户端接收（JavaScript）

```javascript
// Socket.IO 客户端
const socket = io('http://localhost:9090', {
    transports: ['websocket', 'polling']
});

// 连接成功
socket.on('connect', () => {
    console.log('已连接到服务器');
    
    // 请求开始屏幕共享
    socket.emit('screen:share:start', {
        shareId: 1,
        fps: 30
    });
});

// 接收二进制屏幕数据
socket.on('screen:frame', (binaryData) => {
    // binaryData 是 ArrayBuffer 类型
    const blob = new Blob([binaryData], { type: 'image/jpeg' });
    const imageUrl = URL.createObjectURL(blob);
    
    // 显示图片
    const img = document.getElementById('screen-display');
    img.src = imageUrl;
    
    // 释放旧的URL
    if (img.dataset.oldUrl) {
        URL.revokeObjectURL(img.dataset.oldUrl);
    }
    img.dataset.oldUrl = imageUrl;
});

// 停止屏幕共享
function stopScreenShare() {
    socket.emit('screen:share:stop', { shareId: 1 });
}
```

## 性能优化建议

### 1. 数据压缩

对于大型二进制数据，建议先进行压缩：

```java
import java.util.zip.GZIPOutputStream;

public byte[] compressData(byte[] data) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
        gzipOut.write(data);
    }
    return baos.toByteArray();
}

// 使用压缩
byte[] originalData = readLargeFile();
byte[] compressedData = compressData(originalData);
session.sendBinary("file:data:compressed", compressedData);
```

### 2. 分片传输

对于超大文件，建议分片传输：

```java
public void sendLargeFile(SocketSession session, byte[] fileData) {
    int chunkSize = 64 * 1024; // 64KB per chunk
    int totalChunks = (int) Math.ceil((double) fileData.length / chunkSize);
    
    for (int i = 0; i < totalChunks; i++) {
        int start = i * chunkSize;
        int end = Math.min(start + chunkSize, fileData.length);
        byte[] chunk = Arrays.copyOfRange(fileData, start, end);
        
        // 发送分片信息
        session.send("file:chunk:info", Map.of(
            "chunkIndex", i,
            "totalChunks", totalChunks,
            "chunkSize", chunk.length
        ));
        
        // 发送分片数据
        session.sendBinary("file:chunk:data", chunk);
    }
}
```

### 3. 使用消息队列

对于高并发场景，建议使用消息队列：

```java
@Component
public class BinaryDataQueue {
    
    private final BlockingQueue<BinaryMessage> messageQueue = new LinkedBlockingQueue<>();
    
    @PostConstruct
    public void startConsumer() {
        Thread consumer = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    BinaryMessage msg = messageQueue.take();
                    msg.session.sendBinary(msg.event, msg.data);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        consumer.setDaemon(true);
        consumer.start();
    }
    
    public void queueBinaryData(SocketSession session, String event, byte[] data) {
        messageQueue.offer(new BinaryMessage(session, event, data));
    }
    
    private record BinaryMessage(SocketSession session, String event, byte[] data) {}
}
```

## 实际应用场景

### 1. 文件传输系统
- 文件上传/下载
- 断点续传
- 多文件批量传输

### 2. 实时视频/音频流
- 视频会议
- 屏幕共享
- 直播推流

### 3. 图像传输
- 远程桌面
- 实时监控
- 图片编辑协同

### 4. 游戏数据
- 游戏状态同步
- 场景数据传输
- 资源文件分发

## 注意事项

1. **内存管理**: 大型二进制数据可能占用大量内存，注意及时释放
2. **网络带宽**: 二进制数据传输会占用较多带宽，注意控制发送频率
3. **异常处理**: 务必捕获并处理 IOException 等异常
4. **连接状态**: 发送前检查连接是否有效
5. **数据大小限制**: 注意配置 `maxFrameSize` 参数，默认为 1MB

## 配置参数

在 `application.yml` 中配置：

```yaml
plugin:
  socket:
    host: 0.0.0.0
    max-frame-size: 10485760  # 10MB，根据需求调整
    ping-timeout: 60000
    ping-interval: 25000
    room:
      - client-id: default
        port: 9090
        context-path: /socket.io
        enable: true
```

## 故障排查

### 问题：二进制数据发送失败

**可能原因：**
1. 数据大小超过 `maxFrameSize` 限制
2. 客户端连接已断开
3. 网络拥塞

**解决方案：**
1. 增加 `maxFrameSize` 配置或分片传输
2. 发送前检查 `session.isConnected()`
3. 添加重试机制或消息队列

### 问题：客户端无法接收二进制数据

**可能原因：**
1. 客户端未正确处理二进制消息
2. 事件名称不匹配

**解决方案：**
1. 确保客户端监听正确的事件名称
2. 检查客户端是否支持二进制传输模式

## 总结

Socket 模块的二进制数据传输功能提供了高效、可靠的数据传输方案。通过合理使用这些API和优化建议，可以轻松实现各种实时数据传输场景。
