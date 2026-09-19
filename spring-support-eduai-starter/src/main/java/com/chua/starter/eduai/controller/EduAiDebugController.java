package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.service.impl.EduMarkingServiceImpl;
import com.chua.starter.queue.MessageTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 教育 AI 模块调试端点。
 *
 * <p>提供 AI 引擎健康检查、队列状态、阅卷任务统计等运维信息。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/debug")
@Tag(name = "教育AI-调试")
public class EduAiDebugController {

    /**
     * EDUAI服务
    */
    private final EduAiService eduAiService;
    /**
     * 消息模板（队列未启用时为null）
    */
    private final MessageTemplate messageTemplate;

    @Autowired
    public EduAiDebugController(EduAiService eduAiService,
            @Autowired(required = false) MessageTemplate messageTemplate) {
        this.eduAiService = eduAiService;
        this.messageTemplate = messageTemplate;
    }

    /**
     * AI 健康检查。
     * @return AI健康的结果
     */
    @Operation(summary = "AI 模块健康检查")
    @GetMapping("/ai-health")
    public ReturnResult<Map<String, Object>> aiHealth() {
        Map<String, Object> info = new HashMap<>();
        info.putAll(eduAiService.debugInfo());
        if (messageTemplate != null) {
            info.put("queueType", messageTemplate.getType());
            info.put("queueConnected", messageTemplate.isConnected());
        } else {
            info.put("queueType", "none");
            info.put("queueConnected", false);
        }
        return ReturnResult.ok(info);
    }

    /**
     * 队列连通性测试（echo）。
     * @return 队列echo的结果
     */
    @Operation(summary = "队列连通性测试")
    @GetMapping("/queue-echo")
    public ReturnResult<String> queueEcho() {
        if (messageTemplate == null) {
            return ReturnResult.error("queue not enabled");
        }
        try {
            String echoTopic = "edu.marking.echo";
            messageTemplate.send(echoTopic, "ping");
            return ReturnResult.ok("queueType=" + messageTemplate.getType() + ", topic=" + echoTopic);
        } catch (Exception e) {
            return ReturnResult.error("queue error: " + e.getMessage());
        }
    }

    /**
     * 队列名称常量（供前端展示）。
     * @return 队列名称的结果
     */
    @Operation(summary = "队列名称")
    @GetMapping("/queue-name")
    public ReturnResult<String> queueName() {
        return ReturnResult.ok(EduMarkingServiceImpl.QUEUE_NAME);
    }
}
