package com.chua.starter.ai.support.configuration.rag;

/**
 * RAG 全局设置变更事件
 * @since 4.0.0.42
 * @author CH
 */
public class RagSettingsChangedEvent {
    private final String group;
    public RagSettingsChangedEvent(String group) { this.group = group; }
    public String getGroup() { return group; }
}
