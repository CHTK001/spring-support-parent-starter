package com.chua.starter.message.support.template;

import com.chua.common.support.task.message.MessagePush;
import com.chua.common.support.task.message.MessageRequest;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.message.support.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息推送模板
 *
 * <p>提供简化的消息发送API，支持多渠道消息推送。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private MessageTemplate messageTemplate;
 *
 * // 发送到默认渠道
 * messageTemplate.send("13800138000", "验证码：123456");
 *
 * // 发送到指定渠道
 * messageTemplate.send("dingding", "群组ID", "通知消息");
 *
 * // 使用模板发送
 * messageTemplate.sendTemplate("aliyun", "13800138000", "SMS_123456", Map.of("code", "123456"));
 * }</pre>
 *
 * @author CH
 * @since 2024/12/26
 */
@Slf4j
public class MessageTemplate implements AutoCloseable {

    private final MessageProperties properties;
    private final Map<String, MessagePush> pushInstances = new ConcurrentHashMap<>();

    public MessageTemplate(MessageProperties properties) {
        this.properties = properties;
        initChannels();
    }

    private void initChannels() {
        Map<String, MessageProperties.ChannelProperties> channels = properties.getChannels();
        if (channels == null || channels.isEmpty()) {
            log.warn("未配置任何消息推送渠道");
            return;
        }

        for (Map.Entry<String, MessageProperties.ChannelProperties> entry : channels.entrySet()) {
            String channelName = entry.getKey();
            MessageProperties.ChannelProperties channelConfig = entry.getValue();

            if (!channelConfig.isEnabled()) {
                log.debug("渠道 {} 未启用", channelName);
                continue;
            }

            try {
                String type = StringUtils.defaultString(channelConfig.getType(), channelName);
                MessagePush messagePush = MessagePush.create(type);
                if (messagePush != null) {
                    pushInstances.put(channelName, messagePush);
                    log.info("消息推送渠道 [{}] 初始化成功", channelName);
                } else {
                    log.warn("消息推送渠道 [{}] 创建失败，类型 {} 可能不支持", channelName, type);
                }
            } catch (Exception e) {
                log.error("初始化消息推送渠道 [{}] 失败: {}", channelName, e.getMessage());
            }
        }
    }

    private MessagePush getMessagePush(String channel) {
        if (StringUtils.isEmpty(channel)) {
            return null;
        }
        return pushInstances.get(channel);
    }

    public PushResult send(String target, String content) {
        String defaultChannel = properties.getDefaultChannel();
        if (StringUtils.isEmpty(defaultChannel)) {
            if (!pushInstances.isEmpty()) {
                defaultChannel = pushInstances.keySet().iterator().next();
            }
        }
        return send(defaultChannel, target, content);
    }

    public PushResult send(String channel, String target, String content) {
        MessagePush messagePush = getMessagePush(channel);
        if (messagePush == null) {
            return PushResult.failure("渠道 [" + channel + "] 不存在或未启用");
        }
        try {
            MessageRequest request = MessageRequest.builder()
                    .to(Objects.requireNonNullElse(target, ""))
                    .content(Objects.requireNonNullElse(content, ""))
                    .build();
            MessageResponse response = messagePush.send(request);
            return PushResult.ok(response);
        } catch (Exception e) {
            log.error("消息发送失败: 渠道={}, 目标={}", channel, target, e);
            return PushResult.failure("发送失败: " + e.getMessage());
        }
    }

    public PushResult sendTemplate(String channel, String target, String templateId, Map<String, Object> params) {
        return sendTemplate(channel, target, templateId, null, params);
    }

    public PushResult sendTemplate(String channel, String target, String templateId, String signCode, Map<String, Object> params) {
        MessagePush messagePush = getMessagePush(channel);
        if (messagePush == null) {
            return PushResult.failure("渠道 [" + channel + "] 不存在或未启用");
        }
        try {
            Map<String, String> stringParams = params != null
                    ? params.entrySet().stream()
                            .filter(e -> e.getKey() != null && e.getValue() != null)
                            .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey, e -> String.valueOf(e.getValue()), (a, b) -> a))
                    : null;

            MessageResponse response = messagePush.sendTemplate(templateId, target, stringParams);
            return PushResult.ok(response);
        } catch (Exception e) {
            log.error("模板消息发送失败: 渠道={}, 模板={}, 目标={}", channel, templateId, target, e);
            return PushResult.failure("发送失败: " + e.getMessage());
        }
    }

    public List<PushResult> sendBatch(String channel, String[] targets, String content) {
        MessagePush messagePush = getMessagePush(channel);
        if (messagePush == null) {
            return List.of(PushResult.failure("渠道 [" + channel + "] 不存在或未启用"));
        }
        if (targets == null || targets.length == 0) {
            return List.of();
        }
        try {
            return java.util.Arrays.stream(targets)
                    .map(target -> {
                        try {
                            MessageRequest request = MessageRequest.builder()
                                    .to(target)
                                    .content(Objects.requireNonNullElse(content, ""))
                                    .build();
                            MessageResponse response = messagePush.send(request);
                            return PushResult.ok(response);
                        } catch (Exception e) {
                            log.error("批量消息发送失败: 渠道={}, 目标={}", channel, target, e);
                            return PushResult.failure("发送失败: " + e.getMessage());
                        }
                    })
                    .toList();
        } catch (Exception e) {
            log.error("批量消息发送异常: 渠道={}", channel, e);
            return List.of(PushResult.failure("批量发送异常: " + e.getMessage()));
        }
    }

    @Override
    public void close() {
        pushInstances.clear();
    }

    @lombok.Data
    public static class PushResult {

        private final boolean success;
        private final String messageId;
        private final String message;
        private final long durationMillis;

        private PushResult(boolean success, String messageId, String message, long durationMillis) {
            this.success = success;
            this.messageId = messageId;
            this.message = message;
            this.durationMillis = durationMillis;
        }

        public static PushResult ok(MessageResponse response) {
            return new PushResult(
                    response != null && response.isSuccess(),
                    response != null ? response.getMessageId() : null,
                    null,
                    response != null ? response.getDurationMillis() : 0
            );
        }

        public static PushResult failure(String message) {
            return new PushResult(false, null, message, 0);
        }
    }
}