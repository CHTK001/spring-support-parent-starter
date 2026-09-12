package com.chua.common.support.ai;

import java.io.Serializable;
import java.util.Map;

/**
 * AI 工具调用记录，描述 LLM 一次工具调用的标识、工具名与参数。
 *
 * @since 4.0.0
 * @author CH
 */
public class AiToolCall implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
      * 工具调用唯一 标识
     */
    private String id;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具调用参数（JSON 反序列化结果）
     */
    private Map<String, Object> arguments;

    /**
     * @return 工具调用唯一 标识
     */
    public String getId() {
        return id;
    }

    /**
      * 设置工具调用唯一 标识。
     *
     * @param id 调用 标识
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return 工具名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置工具名称。
     *
     * @param name 工具名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 工具调用参数
     */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * 设置工具调用参数。
     *
     * @param arguments 参数键值映射
     */
    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
