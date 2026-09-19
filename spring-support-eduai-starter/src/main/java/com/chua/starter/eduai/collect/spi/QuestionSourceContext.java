package com.chua.starter.eduai.collect.spi;

import lombok.Builder;
import lombok.Data;

/**
 * 采集上下文（任务配置快照）
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@Builder
public class QuestionSourceContext {

    /**
     * 采集任务标识
     */
    private Long taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 来源配置原始 JSON 字符串
     */
    private String sourceConfig;

    /**
     * 学科（落题默认值）
     */
    private String subject;

    /**
     * 学段
     */
    private String gradeLevel;

    /**
     * 年级
     */
    private String grade;

    /**
     * 难度（1-5）
     */
    private Integer difficulty;
}
