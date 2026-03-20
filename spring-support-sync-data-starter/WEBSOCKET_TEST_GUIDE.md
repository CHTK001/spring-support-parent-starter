# WebSocket 实时监控测试指南

## 测试环境准备

### 1. 后端应用启动

由于当前环境限制，需要手动启动包含 sync-data-starter 的 Spring Boot 应用。

**方式一：使用现有的 Monitor 应用**
```bash
cd H:/workspace/2/spring-support-api-parent/spring-api-support-monitor-starter
mvn spring-boot:run
```

**方式二：集成到其他应用**
确保应用依赖了 `spring-support-sync-data-starter`，并且配置了数据库。

### 2. 数据库准备

确保数据库中存在以下表：
- `monitor_sync_task` - 同步任务表
- `monitor_sync_node` - 同步节点表
- `monitor_sync_task_log` - 任务日志表
- `monitor_sync_alert` - 告警表
- `monitor_sync_statistics` - 统计表

可以使用 `test-schema.sql` 和 `test-data.sql` 初始化测试数据。

### 3. 前端应用

前端已构建并复制到后端 `static` 目录：
```
H:\workspace\2\spring-support-parent-starter\spring-support-sync-data-starter\src\main\resources\static
```

访问地址（假设后端端口 19170）：
```
http://localhost:19170/monitor/api/
```

## WebSocket 测试

### 使用测试页面

打开 `websocket-test.html` 进行独立测试：

```bash
# 在浏览器中打开
H:\workspace\2\spring-support-parent-starter\spring-support-sync-data-starter\websocket-test.html
```

**测试步骤：**

1. **连接 WebSocket**
   - 修改 WebSocket URL（如果后端端口不是 18080）
   - 点击"连接"按钮
   - 等待连接成功提示

2. **订阅任务**
   - 输入要监控的任务 ID（默认 1）
   - 点击"订阅任务"按钮
   - 后端会将该任务的实时更新推送到前端

3. **触发任务执行**

   使用 curl 或 Postman 调用后端接口：

   ```bash
   # 手动执行一次任务
   curl -X POST http://localhost:19170/monitor/api/sync/task/1/execute

   # 或启动定时任务
   curl -X POST http://localhost:19170/monitor/api/sync/task/1/start
   ```

4. **观察实时更新**

   测试页面会实时显示：
   - 任务状态（RUNNING/STOPPED/ERROR）
   - 执行进度（0-100%）
   - 吞吐量（条/秒）
   - 读取/写入/成功数量
   - 详细的 WebSocket 消息日志

### 使用前端应用测试

1. **访问任务列表**
   ```
   http://localhost:19170/monitor/api/#/sync/tasks
   ```

2. **进入监控页面**
   - 点击任务卡片的"监控"按钮
   - 或直接访问：`http://localhost:19170/monitor/api/#/sync/monitor?taskId=1`

3. **执行任务**
   - 在任务列表点击"执行"按钮
   - 或点击"启动"按钮启动定时任务

4. **观察实时更新**
   - 监控页面会通过 WebSocket 实时更新
   - 进度条、吞吐量、状态会自动刷新
   - 无需手动刷新页面

## WebSocket 消息格式

### 订阅消息（前端 -> 后端）

```json
{
  "action": "subscribe",
  "taskId": 1
}
```

### 取消订阅（前端 -> 后端）

```json
{
  "action": "unsubscribe",
  "taskId": 1
}
```

### 推送消息（后端 -> 前端）

**状态消息：**
```json
{
  "messageType": "sync_task_status",
  "taskId": 1,
  "taskName": "测试任务",
  "status": "RUNNING",
  "message": "任务开始执行",
  "time": "2026-03-20 10:30:00.123",
  "timestamp": 1710907800123
}
```

**进度消息：**
```json
{
  "messageType": "sync_task_progress",
  "taskId": 1,
  "logId": 123,
  "readCount": 500,
  "writeCount": 480,
  "successCount": 480,
  "failCount": 0,
  "progress": 50,
  "time": "2026-03-20 10:30:05.456",
  "timestamp": 1710907805456
}
```

**指标消息：**
```json
{
  "messageType": "sync_task_metrics",
  "taskId": 1,
  "logId": 123,
  "throughput": 95.5,
  "avgProcessTime": 10.5,
  "cpuUsage": 0.0,
  "memoryUsage": 0.0,
  "time": "2026-03-20 10:30:10.789",
  "timestamp": 1710907810789
}
```

**日志消息：**
```json
{
  "messageType": "sync_task_log",
  "taskId": 1,
  "logId": 123,
  "level": "INFO",
  "nodeKey": null,
  "message": "任务执行完成, 耗时: 5000ms",
  "time": "2026-03-20 10:30:15.012",
  "timestamp": 1710907815012
}
```

## 验证要点

### 1. WebSocket 连接

- ✅ 前端能成功连接到 `/ws/sync/progress`
- ✅ 订阅消息能正确发送
- ✅ 后端 `SyncProgressWebSocketHandler` 正确处理订阅

### 2. 消息推送

- ✅ 任务开始时推送 `RUNNING` 状态和 0% 进度
- ✅ 任务完成时推送 100% 进度和 `STOPPED` 状态
- ✅ 任务失败时推送 `ERROR` 状态
- ✅ 所有消息包含 `messageType` 和 `timestamp`

### 3. 前端更新

- ✅ `monitor.ts` 正确解析包装和扁平两种格式
- ✅ `TaskMonitor.vue` 的 `applyRealtimePatch` 正确更新状态
- ✅ 进度条、吞吐量、计数器实时变化
- ✅ 任务状态正确显示（RUNNING/STOPPED/ERROR）

### 4. 数据一致性

- ✅ WebSocket 推送的数据与 REST API 查询一致
- ✅ `SyncMonitorService.recordProgress` 正确更新缓存
- ✅ `/monitor/realtime/{taskId}` 返回最新状态

## 常见问题

### WebSocket 连接失败

**问题：** 浏览器控制台显示 WebSocket 连接失败

**解决：**
1. 检查后端应用是否启动
2. 确认 WebSocket URL 正确（协议、主机、端口、路径）
3. 检查防火墙或代理设置
4. 查看后端日志确认 WebSocket 配置是否生效

### 订阅后无消息

**问题：** 订阅成功但没有收到任何消息

**解决：**
1. 确认任务 ID 存在且正确
2. 手动触发任务执行
3. 检查后端日志确认 `SyncTaskWebSocketService` 是否调用
4. 确认 `broadcastTaskMessage` 是否正确执行

### 前端不更新

**问题：** 收到 WebSocket 消息但前端不更新

**解决：**
1. 打开浏览器开发者工具查看 WebSocket 消息
2. 检查 `normalizeWebSocketPayload` 是否正确解析
3. 确认 `applyRealtimePatch` 的 `taskId` 匹配
4. 查看控制台是否有 JavaScript 错误

### 消息格式不匹配

**问题：** 前端解析消息失败

**解决：**
1. 确认后端推送的是 JSON 格式
2. 检查 `messageType` 字段是否存在
3. 验证 `taskId` 字段类型（应为 number）
4. 使用测试页面查看原始消息格式

## 性能测试

### 并发订阅测试

测试多个客户端同时订阅同一任务：

```javascript
// 在浏览器控制台执行
for (let i = 0; i < 10; i++) {
  const ws = new WebSocket('ws://localhost:19170/ws/sync/progress');
  ws.onopen = () => {
    ws.send(JSON.stringify({ action: 'subscribe', taskId: 1 }));
    console.log(`Client ${i} subscribed`);
  };
  ws.onmessage = (e) => console.log(`Client ${i} received:`, e.data);
}
```

### 高频推送测试

修改任务批次大小为较小值（如 10），观察高频推送时的性能。

## 下一步

1. **集成到实际应用**
   - 将 sync-data-starter 集成到生产应用
   - 配置真实数据源（MySQL、PostgreSQL 等）
   - 创建实际的同步任务

2. **功能扩展**
   - 支持全局监控（订阅所有任务）
   - 增加告警实时推送
   - 添加任务暂停/恢复功能

3. **性能优化**
   - 实现消息批量推送
   - 添加消息压缩
   - 优化大量订阅时的性能

## 相关文件

- 后端 WebSocket 服务：`SyncTaskWebSocketService.java`
- 后端 WebSocket 处理器：`SyncProgressWebSocketHandler.java`
- 后端监控服务：`SyncMonitorServiceImpl.java`
- 前端监控 Store：`monitor.ts`
- 前端监控页面：`TaskMonitor.vue`
- 测试页面：`websocket-test.html`
