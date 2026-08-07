package com.chua.starter.ai.support.entity;

import lombok.Data;

/**
 * AI 会话实体，对应一次对话会话。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class AIDialog {

    /**
     * 会话 ID
     */
    private Long id;

    /**
     * 会话名称
     */
    private String name;

    /**
     * 会话时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 空参构造。
     */
    public AIDialog() {
    }

    /**
     * 简易构造，时间戳取当前系统时间。
     *
     * @param id   会话 ID
     * @param name 会话名称
     */
    public AIDialog(Long id, String name) {
        this.id = id;
        this.name = name;
        this.timestamp = System.currentTimeMillis();
    }
}
