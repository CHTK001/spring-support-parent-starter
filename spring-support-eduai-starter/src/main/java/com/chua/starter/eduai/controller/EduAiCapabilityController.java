package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.model.vo.EduAiCapabilityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 能力探测。
 *
 * <p>AI 组卷依赖 spring-support-ai-starter 提供的对话客户端。该 starter 未引入、
 * AI 总开关关闭、或没有可用模型时，AI 组卷入口应当禁用并说明原因，
 * 因此单独提供一个无副作用的探测端点供前端在渲染时判断。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/edu/ai")
@Tag(name = "AI 能力")
public class EduAiCapabilityController {

    /**
     * 属性
     */
    private final EduAiService eduAiService;

    /**
     * 构造方法，创建 EduAiCapabilityController 实例。
     *
     * @param eduAiService 教育 AI 调用服务，不允许为 null
     */
    @Autowired
    public EduAiCapabilityController(EduAiService eduAiService) {
        this.eduAiService = eduAiService;
    }

    /**
     * 查询 AI 组卷能力是否可用。
     *
     * @return 能力探测结果，含是否可用与不可用原因
     */
    @GetMapping("/capability")
    @Operation(summary = "查询 AI 组卷能力")
    public ReturnResult<EduAiCapabilityVO> capability() {
        return ReturnResult.ok(eduAiService.capability());
    }
}
