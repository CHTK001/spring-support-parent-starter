package com.chua.starter.message.support.push;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDescribe;
import com.chua.common.support.spi.annotations.SpiParam;
import com.chua.common.support.task.message.MessagePush;
import com.chua.common.support.task.message.MessageRequest;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.common.support.task.message.TemplateInfo;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* 站内消息推送实现
* <p>
* 作为消息推送的站内渠道（SPI 标识 {@code station}），通过 Socket 将消息推送到
* 目标用户已登录的前端页面。推送执行器由 Spring 环境注入（见
* {@code MessageAutoConfiguration}），未接入 Socket 服务时推送返回失败，不影响其他渠道。
* </p>
*
* <h3>对接协议</h3>
* <pre>
*   event: message
*   payload: { module: "message", event: "push", content, subject, target, sendTime }
*   前端通过 socket.subscribe("message", "push", handler) 接收
* </pre>
*
* @since 2026/08/03
 */
@Spi("station")
@SpiDescribe(
        value = "站内消息",
        type = "STATION",
        desc = "通过 Socket 将消息实时推送到登录用户的前端页面",
        optional = {
                @SpiParam(value = "socket.room", desc = "Socket 房间配置（需引入 socket-io-starter）", type = "String")
        }
)
@Slf4j
/**
* 状态消息push类。
*
* @author CH
* @since 4.0.0
 */
public class StationMessagePush implements MessagePush {

    /**
    * 渠道标识
    */
    private static final String PROVIDER = "station";

    /**
    * Socket 事件名（与前端监听保持一致）
    */
    private static final String SOCKET_EVENT = "message";

    /**
    * 站内消息模块标识
    */
    private static final String MODULE = "message";

    /**
    * 站内消息推送事件
    */
    private static final String PUSH_EVENT = "push";

    /**
    * 时间格式
    */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
    * 站内推送执行器（由 Spring 环境注入，未注入时推送失败）
    */
    private static volatile StationPusher pusher;

    /**
    * 注入站内推送执行器
    *
    * @param pusher 推送执行器
    */
    public static void setPusher(StationPusher pusher) {
        StationMessagePush.pusher = pusher;
    }

    /**
    * 站内推送执行器
    * @author CH
    * @since 4.0.0
    */
    @FunctionalInterface
    public interface StationPusher {

        /**
        * 向指定用户推送消息
        *
        * @param userId  目标用户 标识
        * @param payload 消息体（JSON 字符串）
        */
        void push(String userId, String payload);
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public MessageResponse send(MessageRequest request) {
        String userId = request.getTo();
        if (StringUtils.isBlank(userId)) {
            return MessageResponse.failure("站内消息接收人（用户ID）不能为空");
        }
        StationPusher current = pusher;
        if (current == null) {
            log.warn("站内消息推送器未初始化，消息未发送: userId={}", userId);
            return MessageResponse.failure("站内消息推送器未初始化（未启用 Socket 服务）");
        }
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> payload = new LinkedHashMap<>(8);
            payload.put("module", MODULE);
            payload.put("event", PUSH_EVENT);
            payload.put("content", request.getContent());
            payload.put("subject", request.getSubject());
            payload.put("target", userId);
            payload.put("sendTime", LocalDateTime.now().format(TIME_FORMATTER));
            current.push(userId, Json.toJson(payload));
            return MessageResponse.builder()
                    .success(true)
                    .durationMillis(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("站内消息推送失败: userId={}", userId, e);
            return MessageResponse.failure(e.getMessage());
        }
    }

    @Override
    public List<TemplateInfo> listTemplates() {
        return Collections.emptyList();
    }

    @Override
    public TemplateInfo getTemplate(String templateId) {
        return null;
    }
}
