package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷VO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class PaperVO {

    /**
     * 试卷标识
    */
    private Long paperId;
    /**
     * 试卷名称
    */
    private String paperName;
    /**
     * 学科
    */
    private String paperSubject;
    /**
     * 学段
    */
    private String paperGradeLevel;
    /**
     * 年级
    */
    private String paperGrade;
    /**
     * 总分
    */
    private Integer paperTotalScore;
    /**
     * 考试时长（分钟）
    */
    private Integer paperDuration;
    /**
     * 题目数量
    */
    private Integer paperQuestionCount;
    /**
     * 状态
    */
    private Integer paperStatus;
    /**
     * 来源
     */
    private String paperSource;
    /**
     * 试卷唯一编号（业务码，二维码/防篡改标识）
     */
    private String paperNo;
    /**
     * 纸张规格: A4/A3/A5/B5/LETTER
     */
    private String paperPageSize;
    /**
     * 纸张方向: PORTRAIT/LANDSCAPE
     */
    private String paperPageOrientation;
    /**
     * 首页模板标识
     */
    private String paperTemplate;
    /**
     * 页头（富文本）
     */
    private String paperHeader;
    /**
     * 页脚（富文本）
     */
    private String paperFooter;
    /**
     * 当前版本号（每次改动 +1）
     */
    private Integer paperVersion;
    /**
     * 学科/学段是否锁定: 0-否 1-是
     */
    private Integer paperSubjectLocked;
    /**
     * 试卷整体难度 1-5
     */
    private Integer paperDifficulty;
    /**
     * 引用的组卷模板ID
     */
    private Long paperTemplateId;
    /**
     * 首页大标题风格: default/line/double/calligraphy
     */
    private String paperTitleStyle;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 试卷内题目列表
    */
    private List<QuestionVO> questions;
}