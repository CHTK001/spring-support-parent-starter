package com.chua.starter.ai.support.nomifun.controller;

import com.chua.starter.ai.support.nomifun.service.NomifunConversationService;
import com.chua.starter.ai.support.nomifun.service.NomifunConversationService.NomifunConversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Nomifun 契约 - 会话接口（轻量壳）。
 *
 * <p>映射 nomifun 前端 {@code conversation} 命令到服务层。Spring Boot + Jackson
 * 会自动将 record 序列化为 JSON，前端 mapper 能直接消费。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class NomifunConversationController {

    /** 会话业务服务 */
    private final NomifunConversationService conversationService;

    /** 列出会话 */
    @GetMapping
    public List<NomifunConversation> list() {
        return conversationService.listConversations();
    }

    /** 创建会话 */
    @PostMapping
    public NomifunConversation create(@RequestBody(required = false) Map<String, Object> body) {
        var name = (body != null && body.get("name") != null) ? String.valueOf(body.get("name")) : null;
        return conversationService.createConversation(name);
    }

    /** 获取单个会话。前端对 404 静默处理，故未找到抛 404 */
    @GetMapping("/{id}")
    public NomifunConversation get(@PathVariable("id") String id) {
        var conv = conversationService.getConversation(id);
        if (conv == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found: " + id);
        return conv;
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) {
        conversationService.deleteConversation(id);
    }

    /** 列出消息 */
    @GetMapping("/{id}/messages")
    public List<Map<String, Object>> messages(@PathVariable("id") String id) {
        return conversationService.getMessages(id);
    }

    /** 发送消息（同步完成）。 */
    @PostMapping("/{id}/messages")
    public NomifunConversationService.NomifunSendMessageResult sendMessage(@PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> body) {
        String content = (body != null && body.get("content") != null) ? String.valueOf(body.get("content")) : "";
        return conversationService.sendMessage(id, content);
    }

    /** 活跃会话计数 */
    @GetMapping("/active-count")
    public Map<String, Object> activeCount() {
        int count = conversationService.listConversations().size();
        return Map.of("count", count);
    }
}
