package com.chua.starter.queue.configuration;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.StringUtils;
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

        MessageTemplate template = templateMap.get(type);
        if (template == null) {
            log.warn("No MessageTemplate found for type: {}, skipping listener: {}.{}",
                    type, bean.getClass().getSimpleName(), method.getName());
            return;
        }

        ClassUtils.setAccessible(method);
        MessageHandler handler = createHandler(bean, method, payloadType);

        if (StringUtils.isNotEmpty(group)) {
            template.subscribe(destination, group, handler);
        } else {
            template.subscribe(destination, handler);
        }

        log.info("[Queue] 注册消息监听器: {}.{} -> {} (类型: {})",
                highlight(bean.getClass().getSimpleName()), highlight(method.getName()), highlight(destination), highlight(type));
    }

    /**
     * 创建消息处理器
     */
    private MessageHandler createHandler(Object bean, Method method, Class<?> payloadType) {
        return message -> {
            try {
                Object[] args = resolveArguments(method, message, payloadType);
                method.invoke(bean, args);
            } catch (Exception e) {
                log.error("Error handling message: {}", e.getMessage(), e);
            }
        };
    }

    /**
     * 解析方法参数
     */
    private Object[] resolveArguments(Method method, Message message, Class<?> payloadType) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> paramType = parameterTypes[i];

            if (Message.class.isAssignableFrom(paramType)) {
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
