# 更新日志

本文档记录 `spring-support-rsocket-starter` 模块的所有重要变更。

## [4.0.0.34] - 2024-10-24

### 新增功能

#### 核心实现
- ✨ 新增 `RSocketProtocol` 协议实现类，提供完整的 RSocket 协议支持
- ✨ 新增 `RSocketProtocolServer` 服务器实现，支持四种交互模式
- ✨ 新增 `RSocketProtocolClient` 客户端实现，支持响应式通信
- ✨ 新增 `RSocketServletRequest` 请求封装类，兼容标准 ServletRequest 接口
- ✨ 新增 `RSocketServletResponse` 响应封装类，兼容标准 ServletResponse 接口

#### 交互模式
- ✨ 实现 **Request/Response** 模式：请求-响应，一对一通信
- ✨ 实现 **Fire-and-Forget** 模式：单向消息，不需要响应
- ✨ 实现 **Request/Stream** 模式：请求-流，一对多通信
- ✨ 实现 **Channel** 模式：双向流，多对多通信

#### 响应式编程
- ✨ 基于 Project Reactor 实现完整的响应式编程模型
- ✨ 支持异步非阻塞 I/O 操作
- ✨ 实现响应式流（Reactive Streams）规范
- ✨ 支持背压（Backpressure）机制

#### 网络传输
- ✨ 基于 Netty 实现高性能网络传输
- ✨ 支持 TCP 传输协议
- ✨ 支持多路复用
- ✨ 支持零拷贝技术（Zero-Copy）

#### 元数据管理
- ✨ 支持复合元数据（Composite Metadata）
- ✨ 支持路由元数据（Routing Metadata）
- ✨ 支持自定义元数据
- ✨ 支持元数据推送（Metadata Push）

#### 连接管理
- ✨ 实现自动重连机制
- ✨ 实现会话恢复功能
- ✨ 实现 Keep-Alive 心跳机制
- ✨ 实现连接状态监控
- ✨ 支持连接数限制

#### 流量控制
- ✨ 实现租约（Lease）机制
- ✨ 支持请求限流
- ✨ 支持背压控制
- ✨ 支持缓冲区管理

#### 事件处理
- ✨ 支持 `@OnOpen` 连接事件处理
- ✨ 支持 `@OnClose` 断开连接事件处理
- ✨ 支持 `@OnMessage` 消息事件处理
- ✨ 支持 `@OnEvent` 自定义事件处理
- ✨ 实现事件处理器（ProtocolEventHandler）集成

#### 服务器功能
- ✨ 实现广播消息功能（broadcast）
- ✨ 实现点对点消息发送（sendToClient）
- ✨ 实现客户端连接管理
- ✨ 实现客户端断开连接功能（disconnectClient）
- ✨ 实现连接数统计

#### 客户端功能
- ✨ 实现同步发送消息（sendSync）
- ✨ 实现异步发送消息（sendAsync）
- ✨ 实现单向消息发送（sendOneWay）
- ✨ 实现流式数据请求（requestStream）
- ✨ 实现双向通道通信（requestChannel）
- ✨ 实现元数据推送（metadataPush）
- ✨ 实现连接状态检查（isConnected）
- ✨ 实现待处理请求统计（getPendingRequestCount）

#### 配置管理
- ✨ 支持服务器配置（ServerSetting）
  - 监听地址和端口
  - 最大连接数
  - 最大帧大小
  - 线程池配置
  - 上下文路径
- ✨ 支持客户端配置（ClientSetting）
  - 服务器地址和端口
  - 连接超时配置
  - 读取超时配置
  - 重连配置
  - SSL配置

#### 日志和监控
- ✨ 实现完整的日志记录
- ✨ 实现调试日志（Debug日志）
- ✨ 实现性能监控（处理时间统计）
- ✨ 实现错误日志记录

#### SPI扩展
- ✨ 注册 `com.chua.common.support.protocol.Protocol` SPI 扩展
- ✨ 注册 `com.chua.common.support.protocol.server.ProtocolServer` SPI 扩展
- ✨ 注册 `com.chua.common.support.protocol.client.ProtocolClient` SPI 扩展

#### 文档
- ✨ 创建完整的 README.md 使用文档
- ✨ 创建 CHANGELOG.md 更新日志
- ✨ 添加详细的代码注释（Javadoc）
- ✨ 添加快速开始示例
- ✨ 添加高级特性说明
- ✨ 添加性能优化建议
- ✨ 添加最佳实践指南
- ✨ 添加常见问题解答

### 技术栈

#### 核心依赖
- Spring Boot 3.4.5
- Spring Boot RSocket Starter
- RSocket Core
- RSocket Transport Netty
- Project Reactor Core
- Netty
- Jackson (JSON 序列化)

#### 开发工具
- Lombok (代码简化)
- SLF4J (日志门面)
- Utils Support Common Starter (通用工具)

### 性能特性

#### 高性能
- ⚡ 零拷贝技术（Zero-Copy）
- ⚡ 多路复用支持
- ⚡ 低延迟通信
- ⚡ 高吞吐量

#### 响应式
- 🔄 异步非阻塞 I/O
- 🔄 背压机制
- 🔄 流式数据处理
- 🔄 响应式流规范

#### 可靠性
- 🛡️ 自动重连
- 🛡️ 会话恢复
- 🛡️ 心跳检测
- 🛡️ 错误处理

### 兼容性

#### 支持的传输协议
- TCP
- WebSocket（通过配置）
- HTTP/2（通过配置）

#### 支持的数据格式
- JSON（默认）
- 二进制数据
- 文本数据
- 自定义格式

#### Java 版本
- 最低要求：Java 21
- 推荐版本：Java 21 LTS

#### Spring Boot 版本
- 最低要求：Spring Boot 3.2.3
- 推荐版本：Spring Boot 3.4.5

### 与 Socket.IO 功能对比

本模块实现了 `utils-support-socketio-starter` 的所有核心功能，并提供了更强大的特性：

#### 已实现的 Socket.IO 功能
- ✅ 双向通信
- ✅ 事件驱动
- ✅ 连接管理
- ✅ 断线重连
- ✅ 心跳检测
- ✅ 广播消息
- ✅ 点对点消息
- ✅ 连接监控
- ✅ 事件处理器
- ✅ 自定义事件

#### RSocket 独有的增强功能
- ✅ 四种交互模式（vs Socket.IO 的单一模式）
- ✅ 背压机制（Socket.IO 不支持）
- ✅ 响应式流（Socket.IO 不支持）
- ✅ 流式数据传输（Socket.IO 不支持）
- ✅ 双向流通信（Socket.IO 不支持）
- ✅ 租约机制（Socket.IO 不支持）
- ✅ 零拷贝技术（Socket.IO 不支持）
- ✅ 更高的性能和吞吐量

### 使用场景

#### 适用场景
- ✅ 微服务之间的高性能通信
- ✅ 实时数据流传输
- ✅ 物联网设备通信
- ✅ 游戏服务器通信
- ✅ 金融交易系统
- ✅ 实时监控系统
- ✅ 视频流传输
- ✅ 大数据传输

#### 优势
- ⚡ 更低的延迟
- ⚡ 更高的吞吐量
- ⚡ 更好的资源利用
- ⚡ 更强的流量控制
- ⚡ 更灵活的交互模式

### 后续计划

#### 短期计划（v4.0.1）
- [ ] 添加 WebSocket 传输支持
- [ ] 添加 HTTP/2 传输支持
- [ ] 完善安全认证机制
- [ ] 添加更多示例代码
- [ ] 优化性能和内存使用

#### 中期计划（v4.1.0）
- [ ] 添加集群支持
- [ ] 添加负载均衡支持
- [ ] 添加服务发现集成
- [ ] 添加链路追踪支持
- [ ] 添加指标监控（Metrics）

#### 长期计划（v4.2.0）
- [ ] 添加 gRPC 协议兼容
- [ ] 添加消息队列集成
- [ ] 添加分布式追踪
- [ ] 添加性能调优工具
- [ ] 添加可视化监控面板

### 贡献者

- **CH** - 初始开发和文档编写

### 反馈与支持

如有问题或建议，请通过以下方式联系：

- 提交 Issue
- 发起 Pull Request
- 查阅项目文档

### 许可证

本模块遵循项目主许可证。

---

## 版本说明

版本号格式：`主版本.次版本.修订版本`

- **主版本**：不兼容的 API 修改
- **次版本**：向下兼容的功能新增
- **修订版本**：向下兼容的问题修正

当前版本：**4.0.0.34**

---

*注：本文档持续更新中，最新版本请参考项目仓库。*

