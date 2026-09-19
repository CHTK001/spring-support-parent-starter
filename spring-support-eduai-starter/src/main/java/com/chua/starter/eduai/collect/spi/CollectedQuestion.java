package com.chua.starter.eduai.collect.spi;

import lombok.Data;

/**
 * SPI 采集产出的题目（尚未入库）
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectedQuestion {

    /**
     * 题型（SINGLE_CHOICE/MULTIPLE_CHOICE/JUDGE/FILL/SHORT_ANSWER...）
     */
    private String questionType;

    /**
     * 题干
     */
    private String stem;

    /**
     * 选项 JSON 数组字符串
     */
    private String optionsJson;

    /**
     * 标准答案
     */
    private String answer;

    /**
     * 解析
     */
    private String analysis;

    /**
     * 学科（可空，空则取任务默认）
     */
    private String subject;

    /**
     * 学段（可空，空则取任务默认）
     */
    private String gradeLevel;

    /**
     * 年级（可空，空则取任务默认）
     */
    private String grade;

    /**
     * 难度（可空，空则取任务默认）
     */
    private Integer difficulty;

    /**
     * 来源（采集URL/文件名等）
     */
    private String source;
}
