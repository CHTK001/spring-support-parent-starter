package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 学习行为上报 DTO
 *
 * <p>学生端每完成一次学习（一局游戏、一组练习、一段阅读）就上报一次，
 * 服务端据此累加模块进度与当日汇总，首页的所有统计都由这两张表算出来。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class StudyReportDTO {

    /**
     * 模块编码，如 idiom-game；为空时只累加当日汇总、不动模块进度
     */
    private String moduleCode;

    /**
     * 本次学习分钟数
     */
    private Integer studyMinutes;

    /**
     * 本次练习题数
     */
    private Integer questionCount;

    /**
     * 本次答对题数
     */
    private Integer correctCount;

    /**
     * 本次获得星星
     */
    private Integer stars;

    /**
     * 本次获得学习积分
     */
    private Integer points;

    /**
     * 本次为该模块带来的进度增量（0-100）；为空按 0 处理
     */
    private Integer progressDelta;
}
