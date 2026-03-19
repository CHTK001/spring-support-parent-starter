# 支付系统部署文档

## 环境要求

- JDK 21+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.8+

## 数据库初始化

1. 创建数据库：
```sql
CREATE DATABASE payment_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 执行建表脚本：
```bash
mysql -u root -p payment_system < src/main/resources/db/schema.sql
```

## 配置文件

在 `application.yml` 中配置数据库和 Redis 连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
    
  redis:
    host: localhost
    port: 6379
    password: your_redis_password
    database: 0
    
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.chua.payment.support.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

## 加密密钥配置

支付渠道的 API 密钥和私钥使用 AES 加密存储，需要配置加密密钥：

```yaml
payment:
  encryption:
    key: your-32-character-encryption-key
```

## 编译打包

```bash
mvn clean package -DskipTests
```

## 启动应用

```bash
java -jar target/spring-support-payment-starter-1.0.0.jar
```

## Docker 部署

1. 构建镜像：
```bash
docker build -t payment-system:1.0.0 .
```

2. 运行容器：
```bash
docker run -d \
  --name payment-system \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/payment_system \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=password \
  -e SPRING_REDIS_HOST=redis \
  -e SPRING_REDIS_PORT=6379 \
  payment-system:1.0.0
```

## Kubernetes 部署

参考 `k8s/` 目录下的配置文件：

```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
```

## 健康检查

访问健康检查端点：
```
GET http://localhost:8080/actuator/health
```

## 监控指标

访问 Prometheus 指标端点：
```
GET http://localhost:8080/actuator/prometheus
```

## 日志配置

日志文件位置：`logs/payment-system.log`

调整日志级别：
```yaml
logging:
  level:
    com.chua.payment: DEBUG
    org.springframework.statemachine: INFO
```

## 性能优化建议

1. 数据库连接池配置：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

2. Redis 连接池配置：
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
```

3. 状态机缓存配置：
```yaml
spring:
  statemachine:
    cache:
      enabled: true
```

## 故障排查

### 数据库连接失败
- 检查数据库服务是否启动
- 检查连接字符串是否正确
- 检查用户名密码是否正确

### Redis 连接失败
- 检查 Redis 服务是否启动
- 检查 Redis 密码是否正确
- 检查防火墙规则

### 状态机流转失败
- 检查状态机配置是否正确
- 检查订单当前状态是否允许该操作
- 查看状态流转日志表 `order_state_log`

## 安全建议

1. 使用 HTTPS 协议
2. 定期更换加密密钥
3. 限制 API 访问频率
4. 启用 SQL 注入防护
5. 定期备份数据库
