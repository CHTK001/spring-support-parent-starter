package com.chua.starter.ai.support.properties;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 配置属性
 *
 * @author CH
 * @since 2025/01/XX
 */
@Data
public class McpProperties {
    /**
     * 是否启用 MCP
     */
    private boolean enabled = false;

    /**
     * 前置处理器列表
     */
    private List<String> preprocessors = new ArrayList<>();

    /**
     * 后置处理器列表
     */
    private List<String> postprocessors = new ArrayList<>();
}
