package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.chat.DecisionClientRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 决策供应商清单端点。
 *
 * <p>供会话配置的「决策模型」下拉使用：列出已启用且平台属于决策协议
 * （System one / decisions）的供应商。选择结果由前端写入
 * {@code sys_ai_session.settings.decisionProviderId} 持久化，
 * 流式编排准备阶段读出并挂到 Agent 上（见 {@code DecisionClientRegistry}）。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@RestController
@RequestMapping("/v2/decision-providers")
@Tag(name = "AI 决策供应商")
@RequiredArgsConstructor
public class DecisionProviderController {

    /**
     * 决策能力入口：供应商装配与客户端缓存
     */
    private final DecisionClientRegistry decisionClientRegistry;

    /**
     * 可选的决策供应商清单（启用 + 平台匹配）。
     *
     * @return 每行 provider_id / name / platform / model
     */
    @GetMapping
    @Operation(summary = "可选决策供应商清单")
    public ReturnResult<List<Map<String, Object>>> candidates() {
        return ReturnResult.ok(decisionClientRegistry.candidates());
    }
}
