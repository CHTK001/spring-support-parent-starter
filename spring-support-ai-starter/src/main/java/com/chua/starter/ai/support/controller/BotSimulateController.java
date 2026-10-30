package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.bot.BotInboundMessage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.service.AiBotChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 连接器消息模拟器（仅 local profile 生效，用于业务验收）。
 *
 * <p>不真正对外发送消息：以捕获型客户端驱动 {@code AiBotChatService} 的真实处理链路，
 * 返回「仅 @ 回复」门控与「随机回复」概率分支的实际决策结果。</p>
 *
 * @author CH
 */
@Slf4j
@Profile("local")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/connectors/simulate")
public class BotSimulateController {

    private final AiBotChatService aiBotChatService;

    /**
     * 向指定绑定模拟一条入站消息。
     *
     * @param id      绑定 id
     * @param request 消息字段：content / fromUser / fromGroup / mentionedBot / mentionedList
     * @return 决策结果 outcome 与捕获到的回复 sentText
     */
    @PostMapping("/{id}")
    public ReturnResult<Map<String, Object>> simulate(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> request) {
        List<String> mentionedList = null;
        Object raw = request.get("mentionedList");
        if (raw instanceof List<?> list) {
            mentionedList = list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        }
        // 允许显式传入 msgId（幂等验收：同一消息投递多次/多实例）；未传则随机
        String msgId = asString(request.get("msgId"));
        BotInboundMessage.BotInboundMessageBuilder builder = BotInboundMessage.builder()
                .msgId(msgId.isBlank() ? "sim-" + System.nanoTime() : msgId)
                .type(BotInboundMessage.Type.TEXT)
                .content(asString(request.get("content")))
                .fromUser(asString(request.get("fromUser")))
                .createTime(System.currentTimeMillis())
                .fromGroup(Boolean.TRUE.equals(request.get("fromGroup")));
        if (request.containsKey("mentionedBot") && request.get("mentionedBot") != null) {
            builder.mentionedBot(Boolean.TRUE.equals(request.get("mentionedBot")));
        }
        if (mentionedList != null) {
            mentionedList.forEach(builder::mentionedItem);
        }
        Map<String, Object> result = aiBotChatService.simulateInbound(id, builder.build());
        return ReturnResult.success(result);
    }

    /**
     * as String
     * @param value 对象
     * @return 返回值
    */
    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
