package com.chua.common.support.ai;

import lombok.Data;

@Data
public class AiToolResult {

    private String callId;
    private String name;
    private Object result;
    private boolean success;
    private String errorMessage;

    public AiToolResult(String callId, String name, Object result, boolean success, String errorMessage) {
        this.callId = callId;
        this.name = name;
        this.result = result;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public AiToolResult() {
    }

    public static AiToolResultBuilder builder() {
        return new AiToolResultBuilder();
    }

    public static class AiToolResultBuilder {
        private String callId;
        private String name;
        private Object result;
        private boolean success;
        private String errorMessage;

        public AiToolResultBuilder callId(String callId) { this.callId = callId; return this; }
        public AiToolResultBuilder name(String name) { this.name = name; return this; }
        public AiToolResultBuilder result(Object result) { this.result = result; return this; }
        public AiToolResultBuilder success(boolean success) { this.success = success; return this; }
        public AiToolResultBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public AiToolResult build() {
            return new AiToolResult(callId, name, result, success, errorMessage);
        }
    }
}
