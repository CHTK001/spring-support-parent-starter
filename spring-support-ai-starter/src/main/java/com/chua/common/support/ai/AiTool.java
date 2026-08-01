package com.chua.common.support.ai;

import java.util.List;
import java.util.Map;

/**
 * AI 工具定义接口，描述一个可被 LLM 调用的工具（含名称、参数、调用方法）。
 *
 * @author CH
 * @since 4.0.0
 */
public interface AiTool {

    /**
     * @return 工具名称（LLM 调用的标识）
     */
    String name();

    /**
     * @return 工具描述（供 LLM 决定何时调用）
     */
    String description();

    /**
     * @return 工具参数名称列表
     */
    List<String> parameters();

    /**
     * 调用工具执行业务逻辑。
     *
     * @param args 工具入参键值映射
     * @return 工具执行结果
     */
    AiToolResult call(Map<String, Object> args);
}
