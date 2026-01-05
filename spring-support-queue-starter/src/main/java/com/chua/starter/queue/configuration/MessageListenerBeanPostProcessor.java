package com.chua.starter.queue.configuration;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.queue.Acknowledgment;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.annotation.OnMessage;
import com.chua.starter.queue.properties.QueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * 消息监听注解处理器
 * <p>
 * 扫描 @OnMessage 注解并注册监听器
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
@Slf4j
@RequiredArgsConstructor
public class MessageListenerBeanPostProcessor implements BeanPostProcessor {

    private final List<MessageTemplate> messageTemplates;
    private final QueueProperties queueProperties;
    private final Map<String, MessageTemplate> templateMap = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (messageTemplates == null || messageTemplates.isEmpty()) {
            return bean;
        }

        // 缓存MessageTemplate
        if (templateMap.isEmpty()) {
            for (MessageTemplate template : messageTemplates) {
                templateMap.put(template.getType(), template);
            }
        }

        // 扫描@OnMessage注解
        Class<?> beanClass = bean.getClass();
        for (Method method : beanClass.getDeclaredMethods()) {
            OnMessage onMessage = method.getAnnotation(OnMessage.class);
            if (onMessage == null) {
                continue;
            }

            registerListener(bean, method, onMessage);
        }

        return bean;
    }

    /**
     * 注册消息监听器
     */
    private void registerListener(Object bean, Method method, OnMessage onMessage) {
        String destination = onMessage.value();
        String group = onMessage.group();
        String type = StringUtils.isNotEmpty(onMessage.type()) ? onMessage.type() : queueProperties.getType();
        Class<?> payloadType = onMessage.payloadType();
        boolean autoAck = onMessage.autoAck();
        int concurrency = onMessage.concurrency();

        MessageTemplate template = templateMap.get(type);
        if (template == null) {
            log.warn("No MessageTemplate found for type: {}, skipping listener: {}.{}",
                    type, bean.getClass().getSimpleName(), method.getName());
            return;
        }

        ClassUtils.setAccessible(method);
        MessageHandler handler = createHandler(bean, method, payloadType, autoAck);

        // 使用支持 concurrency 的方法
        if (StringUtils.isNotEmpty(group)) {
            template.subscribe(destination, group, handler, autoAck, concurrency);
        } else {
            template.subscribe(destination, handler, autoAck, concurrency);
        }

        log.info("[Queue] 注册消息监听器: {}.{} -> {} (类型: {}, autoAck: {}, concurrency: {})",
                highlight(bean.getClass().getSimpleName()), highlight(method.getName()), 
                highlight(destination), highlight(type), highlight(String.valueOf(autoAck)), 
                highlight(String.valueOf(concurrency)));
    }

    /**
     * 创建消息处理器
     */
    private MessageHandler createHandler(Object bean, Method method, Class<?> payloadType, boolean autoAck) {
        return (message, ack) -> {
            try {
                Object[] args = resolveArguments(method, message, payloadType, ack);
                method.invoke(bean, args);
                
                // 如果自动确认，处理成功后自动确认
                if (autoAck) {
                    ack.acknowledge();
                } else {
                    // 手动确认模式下，如果用户没有调用 ack，检查是否已确认
                    // 如果未确认，说明用户可能忘记调用 ack，记录警告
                    if (!ack.isAcknowledged()) {
                        log.warn("[Queue] 消息处理完成但未确认，可能导致消息堆积。目标: {}, 消息ID: {}", 
                                message.getDestination(), message.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error handling message: {}", e.getMessage(), e);
                // 如果自动确认，异常时也确认（避免消息堆积）
                if (autoAck) {
                    ack.acknowledge();
                } else {
                    // 手动确认模式下，如果用户没有调用 ack，自动 nack(true) 重新入队
                    // 这样可以避免消息堆积，同时给消息重新处理的机会
                    if (!ack.isAcknowledged()) {
                        log.warn("[Queue] 消息处理异常且未确认，自动重新入队。目标: {}, 消息ID: {}", 
                                message.getDestination(), message.getId());
                        ack.nack(true); // 重新入队，给消息重新处理的机会
                    }
                }
            }
        };
    }

    /**
     * 解析方法参数
     */
    private Object[] resolveArguments(Method method, Message message, Class<?> payloadType, Acknowledgment ack) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> paramType = parameterTypes[i];

            if (Acknowledgment.class.isAssignableFrom(paramType)) {
                args[i] = ack;
            } else if (Message.class.isAssignableFrom(paramType)) {
                args[i] = message;
            } else if (String.class.isAssignableFrom(paramType)) {
                args[i] = message.getPayloadAsString();
            } else if (byte[].class.isAssignableFrom(paramType)) {
                args[i] = message.getPayload();
            } else if (payloadType != Object.class && payloadType.isAssignableFrom(paramType)) {
                args[i] = message.getPayload(payloadType);
            } else {
                // 尝试反序列化为参数类型
                args[i] = message.getPayload(paramType);
            }
        }

        return args;
    }
}
