package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.InputOptimizeRequest;
import com.chua.starter.ai.support.model.InputOptimizeResponse;

/**
 * AI 输入优化服务接口。
 *
 * <p>在对话/接口调用前，使用大语言模型将用户原始输入改写为更清晰、更适合模型理解的版本。
   * 优化模型与提示词读取自 {@code input} 分组全局设置（sys_AI_全局_setting），配置变更实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiInputOptimizeService {

    /**
     * 执行输入优化。
     *
     * @param request 优化请求（原始输入与可选覆盖项）
     * @return 优化结果（优化后文本、实际使用模型与耗时）
     */
    InputOptimizeResponse optimize(InputOptimizeRequest request);
}
