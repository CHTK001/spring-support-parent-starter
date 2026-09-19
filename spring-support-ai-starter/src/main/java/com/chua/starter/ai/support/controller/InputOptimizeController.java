package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.model.InputOptimizeRequest;
import com.chua.starter.ai.support.model.InputOptimizeResponse;
import com.chua.starter.ai.support.service.AiInputOptimizeService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 输入优化控制器。
 *
 * <p>提供输入优化对外接口，优化模型与提示词读取自 {@code input} 分组全局设置，配置变更实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/input")
@RequiredArgsConstructor
@Tag(name = "AI 输入优化", description = "使用大语言模型优化用户输入，提升后续对话/接口的理解效果")
public class InputOptimizeController {

    /**
     * 输入优化服务
     */
    private final AiInputOptimizeService inputOptimizeService;

    /**
     * 输入优化接口。
     *
     * <p>调用前需通过 AppKey/Bearer Token 鉴权。请求体 {@code input} 为待优化原始输入，
     * {@code model}/{@code systemPrompt} 可选覆盖全局配置。</p>
     *
     * @param request 优化请求
     * @return 优化结果
     */
    @Permission("admin")
    @Operation(summary = "输入优化", description = "使用配置的优化模型改写输入文本，返回优化后内容与使用模型")
    @PostMapping("/optimize")
    public ReturnResult<InputOptimizeResponse> optimize(@RequestBody InputOptimizeRequest request) {
        if (request == null || request.getInput() == null || request.getInput().isBlank()) {
            return ReturnResult.error("输入内容不能为空");
        }
        return ReturnResult.ok(inputOptimizeService.optimize(request));
    }
}
