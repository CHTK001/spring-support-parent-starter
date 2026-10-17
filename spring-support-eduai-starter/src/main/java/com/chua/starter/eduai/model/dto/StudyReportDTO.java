package com.chua.starter.eduai.model.dto;

/**
 * 学习行为上报 DTO
 *
 * <p>学生端每完成一次学习（一局游戏、一组练习、一段阅读）就上报一次，
 * 服务端据此累加模块进度与当日汇总，首页的所有统计都由这两张表算出来。</p>
 *
 * @param moduleCode    模块编码，如 idiom-game；为空时只累加当日汇总、不动模块进度
 * @param studyMinutes  本次学习分钟数
 * @param questionCount 本次练习题数
 * @param correctCount  本次答对题数
 * @param stars         本次获得星星
 * @param points        本次获得学习积分
 * @param progressDelta 本次为该模块带来的进度增量（0-100）；为空按 0 处理
 * @since 4.0.0.42
 * @author CH
 */
public record StudyReportDTO(
        String moduleCode,
        Integer studyMinutes,
        Integer questionCount,
        Integer correctCount,
        Integer stars,
        Integer points,
        Integer progressDelta) {
}
