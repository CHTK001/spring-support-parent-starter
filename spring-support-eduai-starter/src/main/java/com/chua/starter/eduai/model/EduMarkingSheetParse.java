package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷解析结果实体（结构化落库）
 *
 * <p>阅卷链路把版面拆区域后识别出来的结果，逐项落到本表：一行一个解析项，
 * {@code parseKind} 取 STUDENT（姓名/学号）、TOTAL（卷面总分）、QUESTION（逐题）。
 * 原本这些结果散在 {@code edu_answer_sheet.answer_sheet_content} 的大 JSON 与
 * {@code edu_marking_result.marking_result_detail} 的 JSON 里，无法按字段检索；
 * 本表让「某生某题识别成什么」可直接查、可统计、可回溯。</p>
 *
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_marking_sheet_parse")
public class EduMarkingSheetParse extends SysBase {

    /**
     * 解析项类型：卷面学生（姓名/学号）
     */
    public static final String KIND_STUDENT = "STUDENT";

    /**
     * 解析项类型：卷面总分（满分口径）
     */
    public static final String KIND_TOTAL = "TOTAL";

    /**
     * 解析项类型：逐题解析
     */
    public static final String KIND_QUESTION = "QUESTION";

    /**
     * 解析结果标识
     */
    @TableId(type = IdType.AUTO)
    private Long parseId;

    /**
     * 归属用户 ID（按登录态隔离，与其余 eduai 表同口径）
     */
    private String parseOwnerUserId;

    /**
     * 阅卷任务业务标识
     */
    private String parseTaskId;

    /**
     * 答题卡标识
     */
    private Long parseAnswerSheetId;

    /**
     * 解析项类型：STUDENT / TOTAL / QUESTION
     */
    private String parseKind;

    /**
     * 卷面识别姓名
     */
    private String parseStudentName;

    /**
     * 卷面识别学号
     */
    private String parseStudentNo;

    /**
     * 卷面总分（满分口径）
     */
    private Integer parseTotalScore;

    /**
     * 题号（非逐题行为空串，保证唯一键能约束住每种类型只有一行）
     */
    private String parseQuestionNo;

    /**
     * 题干
     */
    private String parseQuestionStem;

    /**
     * 学生作答
     */
    private String parseStudentAnswer;

    /**
     * 该题满分
     */
    private Integer parseFullScore;

    /**
     * 该题得分
     */
    private Integer parseEarnedScore;

    /**
     * 对错：1 对 0 错 NULL 未判定
     */
    private Integer parseCorrect;

    /**
     * 识别来源：MODEL / LAYOUT / RULE
     */
    private String parseSource;

    /**
     * 所在页码
     */
    private Integer parsePage;

    /**
     * 来源版面区域标签
     */
    private String parseRegionLabel;

    /**
     * 区域归一化坐标 x(0~1)
     */
    private Double parseRegionX;

    /**
     * 区域归一化坐标 y(0~1)
     */
    private Double parseRegionY;

    /**
     * 区域归一化宽度(0~1)
     */
    private Double parseRegionW;

    /**
     * 区域归一化高度(0~1)
     */
    private Double parseRegionH;
}