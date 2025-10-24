# Spring Support Pay Client Starter

支付客户端模块 - 基于Spring State Machine的订单状态管理

## 模块简介

本模块提供完整的支付功能，支持多种支付方式（微信支付、支付宝支付、钱包支付等），并使用Spring State Machine管理订单状态流转。

### 主要特性

- ✅ **多支付方式**：支持微信JSAPI、H5、Native、钱包等多种支付方式
- ✅ **状态机管理**：使用Spring State Machine管理订单状态流转
- ✅ **自动校验**：状态转换合法性自动校验，防止非法状态流转
- ✅ **分布式支持**：基于Redis的状态持久化，支持分布式环境
- ✅ **完整日志**：详细的状态转换日志，便于问题追踪
- ✅ **事件驱动**：支持订单状态变更事件，便于扩展业务逻辑
- ✅ **退款支持**：支持完整退款和部分退款
- ✅ **超时处理**：自动处理订单超时

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-pay-client-starter</artifactId>
    <version>4.0.0.34</version>
</dependency>
```

### 2. 配置Redis

状态机持久化依赖Redis，需要配置Redis连接：

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

### 3. 配置支付参数

```yaml
pay:
  merchant:
    # 商户配置
    id: 1
    name: 测试商户
    # 订单超时时间（分钟）
    timeout: 30
  wechat:
    # 微信支付配置
    app-id: your-app-id
    mch-id: your-mch-id
    api-key: your-api-key
    cert-path: /path/to/cert
```

## 核心功能

### 1. 创建订单

```java
@Autowired
private PayMerchantOrderService orderService;

// 创建订单请求
CreateOrderV2Request request = new CreateOrderV2Request();
request.setPayMerchantId(1);
request.setPayTradeType(PayTradeType.PAY_WECHAT_JSAPI);
request.setPayMerchantOrderAmount(BigDecimal.valueOf(100.00));
request.setPayMerchantOrderOriginId("ORIGIN_001");

// 创建订单
ReturnResult<CreateOrderV2Response> result = orderService.createOrder(request);
if (result.isSuccess()) {
    String orderCode = result.getData().getPayMerchantOrderCode();
    System.out.println("订单创建成功: " + orderCode);
}
```

### 2. 订单支付

```java
// 支付成功后，状态机会自动转换状态
boolean success = stateMachineService.sendEvent(
    orderCode,
    PayOrderStatus.PAY_WAITING,
    PayOrderEvent.PAY_SUCCESS,
    order
);

if (success) {
    // 更新订单状态
    order.setPayMerchantOrderStatus(PayOrderStatus.PAY_SUCCESS);
    orderService.updateById(order);
}
```

### 3. 订单退款

```java
// 申请退款
RefundOrderV2Request refundRequest = new RefundOrderV2Request();
refundRequest.setRefundAmount(BigDecimal.valueOf(50.00));
refundRequest.setRefundReason("用户申请退款");

ReturnResult<RefundOrderV2Response> result = 
    orderService.refundOrder(orderCode, refundRequest);
```

### 4. 关闭订单

```java
// 关闭订单
ReturnResult<Boolean> result = orderService.closeOrder(orderCode);
```

### 5. 查询订单状态

```java
// 查询订单状态
PayOrderStatus status = orderService.getOrderStatus(orderCode);
```

## 状态机使用

### 状态转换示例

```java
@Autowired
private PayOrderStateMachineService stateMachineService;

// 发送状态转换事件
boolean success = stateMachineService.sendEvent(
    orderCode,           // 订单编号
    currentStatus,       // 当前状态
    PayOrderEvent.CLOSE, // 状态转换事件
    order                // 订单实体
);

if (success) {
    // 状态转换成功
    log.info("订单状态转换成功: {}", orderCode);
} else {
    // 状态转换失败（非法转换）
    log.warn("订单状态转换失败: {}", orderCode);
}
```

### 检查状态转换是否合法

```java
// 检查是否可以关闭订单
boolean can = stateMachineService.canSendEvent(
    orderCode,
    currentStatus,
    PayOrderEvent.CLOSE
);

if (can) {
    // 可以关闭订单
} else {
    // 不能关闭订单
    return ReturnResult.illegal("当前状态不允许关闭订单");
}
```

## 订单状态说明

| 状态 | 说明 | 是否终态 |
|------|------|---------|
| PAY_CREATE | 订单创建 | ❌ |
| PAY_CREATE_FAILED | 创建失败 | ✅ |
| PAY_WAITING | 待支付 | ❌ |
| PAY_PAYING | 支付中 | ❌ |
| PAY_SUCCESS | 支付成功 | ✅ |
| PAY_TIMEOUT | 订单超时 | ✅ |
| PAY_CANCEL_SUCCESS | 订单取消 | ✅ |
| PAY_CLOSE_SUCCESS | 订单关闭 | ✅ |
| PAY_REFUND_WAITING | 正在退款 | ❌ |
| PAY_REFUND_PART_SUCCESS | 部分退款 | ❌ |
| PAY_REFUND_SUCCESS | 退款成功 | ✅ |

## 状态流转规则

详细的状态流转规则请参考：[支付订单状态机流程图](doc/支付订单状态机流程图.md)

主要流程：

1. **正常支付流程**：创建 -> 待支付 -> 支付中 -> 支付成功
2. **快速支付流程**：创建 -> 待支付 -> 支付成功
3. **订单超时流程**：创建/待支付 -> 超时
4. **订单关闭流程**：创建/待支付/支付中 -> 关闭
5. **完整退款流程**：支付成功 -> 正在退款 -> 退款成功
6. **部分退款流程**：支付成功 -> 正在退款 -> 部分退款 -> 正在退款 -> 退款成功

## 事件监听

支付模块会在状态变更时发布事件，可以监听这些事件来实现业务逻辑：

```java
@Component
@Slf4j
public class PayOrderEventListener {
    
    /**
     * 监听支付成功事件
     */
    @EventListener
    public void onPaySuccess(FinishPayOrderEvent event) {
        PayMerchantOrder order = event.getPayMerchantOrder();
        log.info("订单支付成功: {}", order.getPayMerchantOrderCode());
        
        // 执行业务逻辑
        // 1. 发送通知
        // 2. 更新库存
        // 3. 生成积分
        // ...
    }
    
    /**
     * 监听退款成功事件
     */
    @EventListener
    public void onRefundSuccess(RefundPayOrderEvent event) {
        PayMerchantOrder order = event.getPayMerchantOrder();
        log.info("订单退款成功: {}", order.getPayMerchantOrderCode());
        
        // 执行业务逻辑
        // 1. 发送通知
        // 2. 恢复库存
        // 3. 扣除积分
        // ...
    }
}
```

## 扩展开发

### 1. 添加新的支付方式

实现 `CreateOrderAdaptor` 接口：

```java
@Extension("PAY_ALIPAY")
public class AlipayCreateOrderAdaptor implements CreateOrderAdaptor {
    
    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(
            CreateOrderV2Request request, 
            String userId, 
            String openId) {
        // 实现支付宝支付逻辑
        return ReturnResult.ok();
    }
}
```

### 2. 添加新的状态或转换规则

修改 `PayOrderStateMachineConfig` 配置类：

```java
@Override
public void configure(
        StateMachineTransitionConfigurer<PayOrderStatus, PayOrderEvent> transitions) 
        throws Exception {
    transitions
        // 添加新的转换规则
        .withExternal()
        .source(PayOrderStatus.NEW_STATE)
        .target(PayOrderStatus.TARGET_STATE)
        .event(PayOrderEvent.NEW_EVENT)
        .and();
}
```

### 3. 自定义状态转换动作

可以在状态转换时执行自定义动作：

```java
transitions
    .withExternal()
    .source(PayOrderStatus.PAY_WAITING)
    .target(PayOrderStatus.PAY_SUCCESS)
    .event(PayOrderEvent.PAY_SUCCESS)
    .action(context -> {
        // 执行自定义动作
        PayMerchantOrder order = context.getMessage()
            .getHeaders()
            .get(ORDER_ENTITY_HEADER, PayMerchantOrder.class);
        
        // 发送通知
        notificationService.send(order);
    })
    .and();
```

## 配置说明

### 日志配置

建议配置状态机日志级别为DEBUG，便于问题排查：

```yaml
logging:
  level:
    com.chua.starter.pay.support.statemachine: DEBUG
```

### Redis配置

状态机持久化使用Redis，建议配置：

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 5000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

## 测试

运行测试用例：

```bash
mvn test -Dtest=PayOrderStateMachineTest
```

测试覆盖以下场景：

- ✅ 正常支付流程
- ✅ 快速支付流程
- ✅ 订单超时流程
- ✅ 订单关闭流程
- ✅ 订单取消流程
- ✅ 完整退款流程
- ✅ 部分退款流程
- ✅ 非法状态转换
- ✅ 状态转换合法性检查

## 文档

- [支付模块状态机改造说明](doc/支付模块状态机改造说明.md)
- [支付订单状态机流程图](doc/支付订单状态机流程图.md)

## 常见问题

### 1. 状态转换失败怎么办？

检查以下几点：

1. 当前状态是否正确
2. 状态转换是否合法（参考状态流转图）
3. 查看状态机日志，确认具体错误信息
4. 检查Redis是否正常运行

### 2. Redis不可用时状态机能否工作？

可以工作，但状态不会被持久化。建议在生产环境确保Redis高可用。

### 3. 如何处理并发订单？

状态机本身是线程安全的，但数据库更新需要使用事务和乐观锁：

```java
@Version
private Integer version;
```

### 4. 状态机性能如何优化？

对于高并发场景，建议：

1. 使用异步处理状态转换
2. 批量处理超时订单
3. 使用状态机缓存池
4. 合理配置Redis连接池

## 版本历史

- **v4.0.0.34** (2025-10-24)
  - ✨ 引入Spring State Machine管理订单状态
  - ✨ 添加状态转换合法性校验
  - ✨ 支持状态机持久化到Redis
  - ✨ 完整的状态转换日志记录
  - ✨ 编写完整的测试用例

## 技术栈

- Spring Boot 3.x
- Spring State Machine 4.0.0
- MyBatis Plus
- Redis
- Lombok

## 联系方式

如有问题或建议，请联系开发团队。

## 许可证

Copyright © 2025 CH

