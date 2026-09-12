package com.chua.starter.queue.spi;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

/**
* 消息队列分发器 SPI。
* <p>
* 业务方通过引入适配器 jar（如 Kafka-适配器）注册此 SPI，
* 再由业务方自行引入客户端 jar（如 spring-Kafka），最后通过
* 配置 {@code plugin.queue.type=kafka} 激活对应实现。
* </p>
*
* @since 4.0.0.43
* @author CH
 */
public interface MessageDispatcherProvider {

    /**
    * 名称：与 {@code plugin.queue.type} 对应（如 {@code kafka} / {@code rabbitmq} / {@code rocketmq} / {@code mqtt} / {@code nats}）。
    * @return 名称的结果
     */
    String name();

    /**
    * 是否在当前 类路径 下可用（客户端 jar 是否被业务方拉入）。
    * 即检查关键类是否存在。
    * @return 是否可用的结果
     */
    boolean isAvailable();

    /**
    * 创建并绑定 {@link MessageTemplate}。
    * <p>
    * 实现应在此方法内完成连接工厂/客户端的初始化，并把模板注册到 Spring 容器。
    * </p>
    * @param beanFactory Bean工厂
    * @param properties 属性
    * @return dispatch的结果
     */
    MessageTemplate dispatch(ConfigurableBeanFactory beanFactory, QueueProperties properties);
}
