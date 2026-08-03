package com.chua.starter.ai.support.entity;

import lombok.Data;

/**
 * AI 会话实体，对应一次对话会话。
 *
 * @author CH
 */
@Data
public class AIDialog {

    private Long id;
    private String name;
    private Long timestamp;

    public AIDialog() {
    }

    public AIDialog(Long id, String name) {
        this.id = id;
        this.name = name;
        this.timestamp = System.currentTimeMillis();
    }
}
