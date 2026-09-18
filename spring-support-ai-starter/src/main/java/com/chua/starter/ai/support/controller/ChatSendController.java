package com.chua.starter.ai.support.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 聊天发送接口（前端适配）
 * 提供 /chat/send 接口，适配前端 Guada AI 的请求格式
 */
@RestController
@RequestMapping("/v2/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatSendController {

    /**
    * 发送消息
    * 前端调用：POST /chat/send
    * 请求体：{ sessionId, content, fileIds?, knowledgeBaseIds?, replaceMessageId? }
    */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, Object> request) {
        log.info("[ChatSend] 收到消息: {}", request);

        // 这里应该调用实际的聊天服务
        // 暂时返回成功，后续接入真实的 ChatClient
        return Map.of(
            "success", true,
            "message", "消息已接收"
        );
    }
}
