package com.chua.common.support.ai;

import lombok.Data;

/**
 * AI 工具调用结果，含调用 ID、工具名、结果对象、成功标志与失败时的错误信息。
 *
 * @since 4.0.0
 * @author CH
 */
@Data
public class AiToolResult {

    /**
     * 关联的工具调用 ID
     */
    private String callId;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具返回结果（任意类型）
     */
    private Object result;

    /**
     * 是否调用成功
     */
    private boolean success;

    /**
     * 失败时的错误描述
     */
    private String errorMessage;

    /**
     * 全参构造。
     */
    public AiToolResult(String callId, String name, Object result, boolean success, String errorMessage) {
        this.callId = callId;
        this.name = name;
        this.result = result;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * 默认构造。
     */
    public AiToolResult() {
    }

    /**
     * @return 建造器实例
     */
    public static AiToolResultBuilder builder() {
        return new AiToolResultBuilder();
    }

    /**
     * AiToolResult 建造器。
     */
    public static class AiToolResultBuilder {

        /**
         * 关联的工具调用 ID
         */
        private String callId;

        /**
         * 工具名称
         */
        private String name;

        /**
         * 工具返回结果
         */
        private Object result;

        /**
         * 是否调用成功
         */
        private boolean success;

        /**
         * 失败时的错误描述
         */
        private String errorMessage;

        /** 调用Id */
        public AiToolResultBuilder callId(String callId) { this.callId = callId; return this; }
        /** Name */
        public AiToolResultBuilder name(String name) { this.name = name; return this; }
        /** Result */
        public AiToolResultBuilder result(Object result) { this.result = result; return this; }
        /** Success */
        public AiToolResultBuilder success(boolean success) { this.success = success; return this; }
        /** 记录错误Message */
        public AiToolResultBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        /**
         * 构造 AiToolResult 实例。
         *
         * @return AiToolResult 实例
         */
        public AiToolResult build() {
            return new AiToolResult(callId, name, result, success, errorMessage);
        }
    }
}
