package com.chua.starter.monitor.support.event;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
* Socket 推送队列桥
* <p>
* 消费 {@link MonitorPushService#SOCKET_PUSH_TOPIC}（socket:push）上的推送消息，
* 转为 socket.io 的 "message" 事件广播到前端。
* 链路：业务 → MonitorPushService → MessageTemplate(socket:push) → 本桥 → SocketSessionTemplate.broadcast("message", json)
* 前端 createSocketService().subscribe(module, event) 按 {module, event, data} 分发。
* </p>
* <p>
* 队列（plugin.queue）未启用时，MessageTemplate 为空实现，本桥订阅无实际消息，静默跳过。
* </p>
*
* 与 spring-support-socket-starter 自带的 com.chua.socket.support.bridge.SocketQueueBridge
* 功能重叠且同名，二者同时装配会触发 ConflictingBeanDefinitionException；
* 故本类 Bean 命名加 monitor 前缀避免跨 jar 冲突（同一 socket:push 订阅逻辑仅其一生效）。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component("monitorSocketQueueBridge")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.queue", name = "enable", havingValue = "true", matchIfMissing = true)
public class SocketQueueBridge {

    /** 消息队列模板（queue 未启用时为空实现） */
    private final ObjectProvider<MessageTemplate> messageTemplateProvider;

    /** Socket 会话模板（socket 未启用时为空实现） */
    private final ObjectProvider<SocketSessionTemplate> socketTemplateProvider;

    /**
    * 初始化：订阅 socket:push 推送 topic
     */
    @PostConstruct
    public void init() {
        MessageTemplate messageTemplate = messageTemplateProvider.getIfAvailable();
        if (messageTemplate == null) {
            log.debug("[socket-bridge] 消息队列未启用，跳过 socket:push 订阅");
            return;
        }
        messageTemplate.subscribe(MonitorPushService.SOCKET_PUSH_TOPIC, (Message message, com.chua.starter.queue.Acknowledgment ack) -> {
            String json = message == null ? null : message.getPayloadAsString();
            if (json == null || json.isBlank()) {
                if (ack != null) ack.acknowledge();
                return;
            }
            try {
                SocketSessionTemplate socketTemplate = socketTemplateProvider.getIfAvailable();
                if (socketTemplate == null) {
                    if (ack != null) ack.acknowledge();
                    return;
                }
                // 诊断：确认广播实际使用的会话模板实现类型
                log.debug("[socket-bridge] 广播模板类型: {}", socketTemplate.getClass().getName());
                socketTemplate.broadcast("message", json);
                log.debug("[socket-bridge] 已广播 {} 字节到前端", json.length());
            } catch (Exception e) {
                log.warn("[socket-bridge] 广播失败: {}", e.getMessage());
            } finally {
                if (ack != null) ack.acknowledge();
            }
        });
        log.info("[socket-bridge] 已订阅 socket:push 推送 topic");
    }

    /**
    * 销毁：桥不持有长生命周期资源（订阅由 MessageTemplate 管理），留空占位。
     */
    @PreDestroy
    public void destroy() {
        // 无资源需显式释放
    }
}
