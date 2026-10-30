package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.chat.AiChatGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话能力探测端点。
 *
 * <p>把「AI 能不能用、为什么不能用、实际用的是哪个模型」做成两个无副作用的只读接口，
 * 供任何前端页面在渲染时判断是否展示 AI 入口：</p>
 * <ul>
 *   <li>{@code GET /v2/chat/capability}：只判断客户端能否解析，不产生外部调用；</li>
 *   <li>{@code POST /v2/chat/probe}：真实发一条最小请求，用于验证密钥/网络/模型名。</li>
 * </ul>
 *
 * <p>业务模块不再各自实现能力探测 —— 探测口径必须与真实调用同源，否则会出现
 * 「页面显示可用、实际调用失败」。能力归属 AI 模块，业务只消费结果。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@RestController
@RequestMapping("/v2/chat")
@Tag(name = "AI 对话能力")
@RequiredArgsConstructor
public class ChatCapabilityController {

    /**
     * AI 对话网关
     */
    private final AiChatGateway aiChatGateway;

    /**
     * 探测 AI 对话能力（不产生外部调用）。
     *
     * @return 可用性与生效的厂商/模型
     */
    @GetMapping("/capability")
    @Operation(summary = "探测 AI 对话能力")
    public ReturnResult<AiChatGateway.AiChatCapability> capability() {
        return ReturnResult.ok(aiChatGateway.capability());
    }

    /**
     * 测试 AI 连通性（真实发一条最小请求）。
     *
     * @return 连通性结果与耗时
     */
    @PostMapping("/probe")
    @Operation(summary = "测试 AI 连通性")
    public ReturnResult<AiChatGateway.AiChatProbe> probe() {
        return ReturnResult.ok(aiChatGateway.probe(90_000L));
    }
}
