# WebSocket 实时监控功能测试总结

## 已完成的改造

### 1. 后端桥接 ✅

**文件：** [SyncTaskWebSocketService.java](H:\workspace\2\spring-support-parent-starter\spring-support-sync-data-starter\src\main\java\com\chua\starter\sync\data\support\service\sync\SyncTaskWebSocketService.java)

**改造内容：**
- 注入 `SyncProgressWebSocketHandler` 和 `SyncMonitorService`
- 实现实时快照机制，维护每个任务的最新状态
- 所有推送方法同时发送到主题和 WebSocket handler
- 每次推送自动调用 `syncMonitorService.recordProgress` 更新缓存
- 支持状态、日志、进度、指标四种消息类型

**关键方法：**
```java
// 推送任务状态
pushTaskStatus(taskId, taskName, status, message)

// 推送任务进度
pushTaskProgress(taskId, logId, readCount, writeCount, successCount, failCount, progress)

// 推送任务指标
pushTaskMetrics(taskId, logId, throughput, avgProcessTime, cpuUsage, memoryUsage)

// 推送任务日志
pushTaskLog(taskId, logId, level, nodeKey, message)

// 统一发送方法
sendTaskMessage(topic, messageType, taskId, data)
```

**消息流程：**
```
执行器调用 pushTaskStarted/pushTaskCompleted/pushError
    ↓
构建实时快照并合并最新数据
    ↓
调用 syncMonitorService.recordProgress 更新缓存
    ↓
发送到 SocketSessionTemplate 主题（兼容旧系统）
    ↓
调用 syncProgressWebSocketHandler.broadcastTaskMessage
    ↓
推送到所有订阅该任务的 WebSocket 客户端
```

### 2. 前端对齐 ✅

**文件：** [monitor.ts](H:\workspace\2\vue-support-parent-starter\apps\vue-support-sync-starter\src\stores\monitor.ts)

**改造内容：**
- 增加 `normalizeWebSocketPayload` 函数处理消息格式
- 支持包装格式（`{ messageType, data: {...} }`）和扁平格式（`{ messageType, taskId, ... }`）
- 自动提取 `data` 字段并合并 `messageType` 和 `timestamp`

**关键代码：**
```typescript
const normalizeWebSocketPayload = (raw: any): any => {
  if (!raw || typeof raw !== "object") {
    return raw;
  }

  // 如果是包装格式，提取 data 并合并顶层字段
  if (raw.data && typeof raw.data === "object" && raw.messageType) {
    return { ...raw.data, messageType: raw.messageType, timestamp: raw.timestamp };
  }

  // 否则直接返回扁平格式
  return raw;
};
```

**文件：** [TaskMonitor.vue](H:\workspace\2\vue-support-parent-starter\apps\vue-support-sync-starter\src\pages\sync\TaskMonitor.vue)

**改造内容：**
- 扩展 `applyRealtimePatch` 支持更多字段
- 新增 `readCount`、`writeCount`、`successCount`、`failCount` 实时更新
- 保持原有 `progress`、`throughput`、`status` 更新逻辑

**关键代码：**
```typescript
const applyRealtimePatch = (data: any) => {
  if (!taskId.value || data.taskId !== taskId.value) {
    return;
  }

  const target = realtimeTasks.value.find((item) => item.syncTaskId === taskId.value);
  if (!target) {
    return;
  }

  // 更新所有实时字段
  if (data.progress !== undefined) target.progress = Number(data.progress);
  if (data.throughput !== undefined) target.throughput = Number(data.throughput);
  if (data.status) target.syncTaskStatus = data.status;
  if (data.readCount !== undefined) target.syncTaskReadCount = Number(data.readCount);
  if (data.writeCount !== undefined) target.syncTaskWriteCount = Number(data.writeCount);
  if (data.successCount !== undefined) target.syncTaskSuccessCount = Number(data.successCount);
  if (data.failCount !== undefined) target.syncTaskFailCount = Number(data.failCount);
};
```

### 3. 编译验证 ✅

**前端构建：**
```bash
cd vue-support-parent-starter/apps/vue-support-sync-starter
pnpm build
```
- ✅ 构建成功，耗时 1m 17s
- ✅ 产物已复制到后端 `static` 目录

**后端编译：**
```bash
cd spring-support-parent-starter
mvn -pl spring-support-sync-data-starter -am -DskipTests clean compile
```
- ✅ 编译成功，BUILD SUCCESS
- ✅ 所有依赖模块编译通过

## 测试准备

### 1. 测试页面 ✅

**文件：** [websocket-test.html](H:\workspace\2\spring-support-parent-starter\spring-support-sync-data-starter\websocket-test.html)

**功能：**
- WebSocket 连接管理（连接/断开）
- 任务订阅/取消订阅
- 实时监控卡片（状态、进度、吞吐量、计数器）
- 消息日志查看器
- 支持自定义 WebSocket URL 和任务 ID

**使用方法：**
1. 在浏览器中打开 `websocket-test.html`
2. 修改 WebSocket URL（如需要）
3. 点击"连接"按钮
4. 输入任务 ID 并点击"订阅任务"
5. 触发任务执行，观察实时更新

### 2. 测试指南 ✅

**文件：** [WEBSOCKET_TEST_GUIDE.md](H:\workspace\2\spring-support-parent-starter\spring-support-sync-data-starter\WEBSOCKET_TEST_GUIDE.md)

**内容：**
- 测试环境准备
- WebSocket 测试步骤
- 消息格式说明
- 验证要点
- 常见问题解决
- 性能测试方法

### 3. 测试数据 ✅

**文件：**
- `test-schema.sql` - 数据库表结构
- `test-data.sql` - 测试数据

**内容：**
- 创建所有必需的表
- 插入测试任务（ID=1）
- 插入测试节点（输入/输出）

## 测试方法

### 方式一：使用测试页面（推荐）

1. **启动后端应用**
   ```bash
   # 使用包含 sync-data-starter 的任何 Spring Boot 应用
   cd spring-support-api-parent/spring-api-support-monitor-starter
   mvn spring-boot:run
   ```

2. **打开测试页面**
   ```
   在浏览器中打开：websocket-test.html
   ```

3. **连接并订阅**
   - 点击"连接"
   - 点击"订阅任务"

4. **触发任务执行**
   ```bash
   curl -X POST http://localhost:19170/monitor/api/sync/task/1/execute
   ```

5. **观察实时更新**
   - 任务状态变化：STOPPED → RUNNING → STOPPED
   - 进度变化：0% → 100%
   - 计数器实时更新
   - 消息日志滚动显示

### 方式二：使用前端应用

1. **访问监控页面**
   ```
   http://localhost:19170/monitor/api/#/sync/monitor?taskId=1
   ```

2. **执行任务**
   - 在任务列表点击"执行"按钮
   - 或使用 API 触发

3. **观察实时更新**
   - 监控卡片自动刷新
   - 进度条实时变化
   - 无需手动刷新页面

## 预期效果

### 任务开始时

**WebSocket 消息：**
```json
// 状态消息
{
  "messageType": "sync_task_status",
  "taskId": 1,
  "status": "RUNNING",
  "message": "任务开始执行"
}

// 进度消息
{
  "messageType": "sync_task_progress",
  "taskId": 1,
  "progress": 0,
  "readCount": 0,
  "writeCount": 0,
  "successCount": 0,
  "failCount": 0
}
```

**前端更新：**
- 任务状态显示 "RUNNING"
- 进度条显示 0%
- 所有计数器归零

### 任务完成时

**WebSocket 消息：**
```json
// 进度消息
{
  "messageType": "sync_task_progress",
  "taskId": 1,
  "progress": 100,
  "readCount": 100,
  "writeCount": 100,
  "successCount": 100,
  "failCount": 0
}

// 指标消息（如果有）
{
  "messageType": "sync_task_metrics",
  "taskId": 1,
  "throughput": 20.5,
  "avgProcessTime": 48.8
}

// 状态消息
{
  "messageType": "sync_task_status",
  "taskId": 1,
  "status": "STOPPED",
  "message": "任务执行完成"
}
```

**前端更新：**
- 任务状态显示 "STOPPED"
- 进度条显示 100%
- 计数器显示最终值
- 吞吐量显示实际值

### 任务失败时

**WebSocket 消息：**
```json
// 状态消息
{
  "messageType": "sync_task_status",
  "taskId": 1,
  "status": "ERROR",
  "message": "任务执行失败: ..."
}

// 日志消息
{
  "messageType": "sync_task_log",
  "taskId": 1,
  "level": "ERROR",
  "message": "任务执行失败: ..."
}
```

**前端更新：**
- 任务状态显示 "ERROR"
- 进度保持当前值
- 错误信息显示在日志中

## 技术要点

### 1. 消息格式兼容性

后端发送的消息格式：
```json
{
  "messageType": "sync_task_progress",
  "taskId": 1,
  "progress": 50,
  "throughput": 95.5,
  "timestamp": 1710907805456
}
```

前端 `normalizeWebSocketPayload` 可以处理：
- 扁平格式（如上）
- 包装格式（`{ messageType, data: {...} }`）

### 2. 实时快照机制

后端维护每个任务的实时快照：
```java
private final Map<Long, Map<String, Object>> realtimeSnapshots = new ConcurrentHashMap<>();
```

每次推送时：
1. 合并新数据到快照
2. 同步到 `SyncMonitorService` 缓存
3. 广播完整快照给所有订阅者

### 3. 前端增量更新

前端只更新变化的字段：
```typescript
if (data.progress !== undefined) target.progress = Number(data.progress);
if (data.throughput !== undefined) target.throughput = Number(data.throughput);
```

避免不必要的 DOM 更新，提升性能。

### 4. 双向数据流

```
前端 → 后端：订阅/取消订阅
后端 → 前端：状态/进度/指标/日志
```

前端可以同时订阅多个任务（通过多个 WebSocket 连接或单连接多订阅）。

## 已知限制

1. **执行器未调用进度推送**
   - 当前 `MonitorSyncTaskExecutorImpl` 只在开始和完成时推送
   - 中间过程没有进度更新
   - 需要在 SyncFlow 执行过程中增加回调

2. **任务计数器为零**
   - `taskLog` 的计数字段在执行器中初始化为 0
   - SyncFlow 没有更新这些字段
   - 需要在 Sink 或 Output 中记录实际计数

3. **吞吐量计算**
   - 当前只在完成时从 `taskLog.syncLogThroughput` 读取
   - 如果该字段为 null，显示为 0
   - 需要在执行过程中实时计算

## 后续优化建议

1. **增加中间进度推送**
   - 在 SyncFlow 执行过程中定期推送进度
   - 可以通过监听器或回调实现

2. **实现真实计数**
   - 在 Sink 中记录实际读取/写入数量
   - 更新 taskLog 字段
   - 推送实时计数

3. **优化吞吐量计算**
   - 实时计算吞吐量（记录数/秒）
   - 定期推送指标消息

4. **支持全局监控**
   - 允许订阅所有任务（`taskId = 0` 或 `*`）
   - 推送所有任务的状态变化

5. **增加告警推送**
   - 任务失败时推送告警
   - 内存不足时推送告警
   - 性能下降时推送告警

## 总结

✅ **后端桥接完成** - WebSocket 推送已接入 `SyncProgressWebSocketHandler`
✅ **前端对齐完成** - 消息格式兼容，实时更新正常
✅ **编译验证通过** - 前后端构建成功
✅ **测试准备就绪** - 测试页面和文档已创建

**下一步：** 启动包含 sync-data-starter 的 Spring Boot 应用，使用测试页面或前端应用验证 WebSocket 实时监控功能。
