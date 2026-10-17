package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷VO
 *
 * @param paperId                试卷标识
 * @param paperName              试卷名称
 * @param paperSubject           学科
 * @param paperGradeLevel        学段
 * @param paperGrade             年级
 * @param paperTotalScore        总分
 * @param paperDuration          考试时长（分钟）
 * @param paperQuestionCount     题目数量
 * @param paperStatus            状态
 * @param paperSource            来源
 * @param paperNo                试卷唯一编号（业务码，二维码/防篡改标识）
 * @param paperPageSize          纸张规格: A4/A3/A5/B5/LETTER
 * @param paperPageOrientation   纸张方向: PORTRAIT/LANDSCAPE
 * @param paperTemplate          首页模板标识
 * @param paperHeader            页头（富文本）
 * @param paperFooter            页脚（富文本）
 * @param paperVersion           当前版本号（每次改动 +1）
 * @param paperSubjectLocked     学科/学段是否锁定: 0-否 1-是
 * @param paperDifficulty        试卷整体难度 1-5
 * @param paperTemplateId        引用的组卷模板ID
 * @param paperTitleStyle        首页大标题风格: default/line/double/calligraphy
 * @param paperCreatorName       创建人姓名（落库时取自登录态；历史数据可能为空）
 * @param paperCreatorType       创建人类型：TEACHER-教师创建 / ADMIN-管理员创建 / SYSTEM-系统创建 / AI-AI 组卷
 * @param createTime             创建时间
 * @param questions              试卷内题目列表
 * @since 4.0.0.42
 * @author CH
 */
public record PaperVO(
        Long paperId,
        String paperName,
        String paperSubject,
        String paperGradeLevel,
        String paperGrade,
        Integer paperTotalScore,
        Integer paperDuration,
        Integer paperQuestionCount,
        Integer paperStatus,
        String paperSource,
        String paperNo,
        String paperPageSize,
        String paperPageOrientation,
        String paperTemplate,
        String paperHeader,
        String paperFooter,
        Integer paperVersion,
        Integer paperSubjectLocked,
        Integer paperDifficulty,
        Long paperTemplateId,
        String paperTitleStyle,
        String paperCreatorName,
        String paperCreatorType,
        LocalDateTime createTime,
        List<QuestionVO> questions) {

    /**
     * 回填创建人姓名与类型。
     *
     * @param creatorName 创建人展示名
     * @param creatorType 创建人类型
     * @return 携带创建人信息的新 VO
     */
    public PaperVO withCreator(String creatorName, String creatorType) {
        return new PaperVO(paperId, paperName, paperSubject, paperGradeLevel, paperGrade, paperTotalScore,
                paperDuration, paperQuestionCount, paperStatus, paperSource, paperNo, paperPageSize,
                paperPageOrientation, paperTemplate, paperHeader, paperFooter, paperVersion, paperSubjectLocked,
                paperDifficulty, paperTemplateId, paperTitleStyle, creatorName, creatorType, createTime, questions);
    }

    /**
     * 回填试卷内题目列表。
     *
     * @param questions 题目列表
     * @return 携带题目列表的新 VO
     */
    public PaperVO withQuestions(List<QuestionVO> questions) {
        return new PaperVO(paperId, paperName, paperSubject, paperGradeLevel, paperGrade, paperTotalScore,
                paperDuration, paperQuestionCount, paperStatus, paperSource, paperNo, paperPageSize,
                paperPageOrientation, paperTemplate, paperHeader, paperFooter, paperVersion, paperSubjectLocked,
                paperDifficulty, paperTemplateId, paperTitleStyle, paperCreatorName, paperCreatorType, createTime,
                questions);
    }
}
