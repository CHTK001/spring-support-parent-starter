package com.chua.starter.message.support.template;

import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.message.MessagePush;
import com.chua.common.support.lang.message.config.PushSetting;
import com.chua.common.support.lang.message.option.ContentTemplateSetting;
import com.chua.common.support.lang.message.option.TemplateSetting;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.message.support.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息推送模板
 * <p>
 * 提供简化的消息发送API，支持多渠道消息推送。
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
 * messageTemplate.sendTemplate("aliyun", "13800138000", "SMS_123456",
 *     Map.of("code", "123456"));
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

    /**
     * 初始化渠道
     */
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
                PushSetting pushSetting = createPushSetting(channelConfig);
                MessagePush messagePush = MessagePush.create(type, pushSetting);

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

    /**
     * 创建推送配置
     */
    private PushSetting createPushSetting(MessageProperties.ChannelProperties config) {
        return PushSetting.builder()
                .accessKeyId(config.getAccessKeyId())
                .accessKeySecret(config.getAccessKeySecret())
                .endpoint(config.getEndpoint())
                .build();
    }

    /**
     * 发送消息到默认渠道
     *
     * @param target  目标 (手机号/用户ID/群组ID等)
     * @param content 消息内容
     * @return 推送结果
     */
    public PushResult send(String target, String content) {
        String defaultChannel = properties.getDefaultChannel();
        if (StringUtils.isEmpty(defaultChannel)) {
            // 使用第一个可用渠道
            if (!pushInstances.isEmpty()) {
                defaultChannel = pushInstances.keySet().iterator().next();
            }
        }
        return send(defaultChannel, target, content);
    }

    /**
     * 发送消息到指定渠道
     *
     * @param channel 渠道名称
     * @param target  目标
     * @param content 消息内容
     * @return 推送结果
     */
    public PushResult send(String channel, String target, String content) {
        MessagePush messagePush = getMessagePush(channel);
        if (messagePush == null) {
            return PushResult.builder()
                    .code(com.chua.common.support.lang.message.MessageCode.FAILURE)
                    .message("渠道 [" + channel + "] 不存在或未启用")
                    .build();
        }

        ContentTemplateSetting setting = ContentTemplateSetting.builder()
                .templateContent(content)
                .build();

        return messagePush.publish(target, setting);
    }

    /**
     * 使用模板发送消息
     *
     * @param channel    渠道名称
     * @param target     目标
     * @param templateId 模板ID
     * @param params     模板参数
     * @return 推送结果
     */
    public PushResult sendTemplate(String channel, String target, String templateId, Map<String, Object> params) {
        return sendTemplate(channel, target, templateId, null, params);
    }

    /**
     * 使用模板发送消息（带签名）
     *
     * @param channel    渠道名称
     * @param target     目标
     * @param templateId 模板ID
     * @param signCode   签名
     * @param params     模板参数
     * @return 推送结果
     */
    public PushResult sendTemplate(String channel, String target, String templateId, String signCode, Map<String, Object> params) {
        MessagePush messagePush = getMessagePush(channel);
        if (messagePush == null) {
            return PushResult.builder()
                    .code(com.chua.common.support.lang.message.MessageCode.FAILURE)
                    .message("渠道 [" + channel + "] 不存在或未启用")
                    .build();
        }

        JsonObject jsonParams = new JsonObject();
        if (params != null) {
            params.forEach((k, v) -> jsonParams.fluentPut(k, v));
        }

        TemplateSetting setting = TemplateSetting.builder()
                .templateId(templateId)
                .signCode(signCode != null ? signCode : getDefaultSign(channel))
                .templateParam(jsonParams)
                .build();

        return messagePush.publish(target, setting);
    }

    /**
     * 批量发送消息
     *
     * @param channel 渠道名称
     * @param targets 目标列表
     * @param content 消息内容
     * @return 推送结果列表
     */
    public List<PushResult> sendBatch(String channel, String[] targets, String content) {
        MessagePush messagePush = getMessagePush(channel);
        if (messagePush == null) {
            return List.of(PushResult.builder()
                    .code(com.chua.common.support.lang.message.MessageCode.FAILURE)
                    .message("渠道 [" + channel + "] 不存在或未启用")
                    .build());
        }

        ContentTemplateSetting setting = ContentTemplateSetting.builder()
                .templateContent(content)
                .build();

        return messagePush.publish(targets, setting);
    }

    /**
     * 获取渠道的默认签名
     */
    private String getDefaultSign(String channel) {
        MessageProperties.ChannelProperties config = properties.getChannels().get(channel);
        return config != null ? config.getDefaultSign() : null;
    }

    /**
     * 获取消息推送实例
     *
     * @param channel 渠道名称
     * @return MessagePush实例
     */
    public MessagePush getMessagePush(String channel) {
        if (StringUtils.isEmpty(channel)) {
            return null;
        }
        return pushInstances.get(channel);
    }

    /**
     * 检查渠道是否可用
     *
     * @param channel 渠道名称
     * @return 是否可用
     */
    public boolean isChannelAvailable(String channel) {
        MessagePush push = pushInstances.get(channel);
        return push != null && push.isServiceAvailable();
    }

    /**
     * 获取所有可用渠道
     *
     * @return 渠道名称列表
     */
    public java.util.Set<String> getAvailableChannels() {
        return pushInstances.keySet();
    }

    @Override
    public void close() throws Exception {
        for (Map.Entry<String, MessagePush> entry : pushInstances.entrySet()) {
            try {
                entry.getValue().close();
                log.debug("关闭消息推送渠道: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("关闭消息推送渠道 {} 失败: {}", entry.getKey(), e.getMessage());
            }
        }
        pushInstances.clear();
    }
}
