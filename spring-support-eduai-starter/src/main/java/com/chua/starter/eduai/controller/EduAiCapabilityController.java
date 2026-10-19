package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.model.vo.EduAiCapabilityVO;
import com.chua.starter.eduai.model.vo.EduAiProbeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * 测试 AI 连通性（真实发一条最小对话请求）。
     *
     * <p>与 {@code /capability} 的区别：后者无副作用、只判断能否解析出客户端；
     * 本接口会真的调用一次模型，用于在配置页确认「密钥 / 网关 / 模型名」是否可用。
     * 只在用户主动点击「测试连通」时调用，不要放进页面初始化。</p>
     *
     * @return 连通性测试结果（含耗时与模型返回片段）
     */
    @PostMapping("/probe")
    @Operation(summary = "测试 AI 连通性")
    public ReturnResult<EduAiProbeVO> probe() {
        return ReturnResult.ok(eduAiService.probe());
    }
}
