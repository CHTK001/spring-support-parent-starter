package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;
import java.time.LocalDateTime;

/**
 * 错题记录实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_wrong_question")
public class EduWrongQuestion extends SysBase {

    /**
     * 错题记录标识
     */
    @TableId(type = IdType.AUTO)
    /**
     * wrongquestionid
    */
    private Long wrongQuestionId;

    /**
     * 学生标识
     */
    private String wrongQuestionStudentId;

    /**
     * 题目标识
     */
    private Long wrongQuestionQuestionId;

    /**
     * 考试标识
     */
    private Long wrongQuestionExamId;

    /**
     * 试卷标识（错题来自哪张卷；任意卷可能为空）
     */
    private Long wrongQuestionPaperId;

    /**
     * 来源阅卷任务标识（哪一次「完成」把它归进来的）。
     *
     * <p>「撤销完成」靠它精确删除「本次登记的」行，不会误伤历史错题：
     * 同一道题可能同时是别的卷、别的任务登记进来的。</p>
     */
    private String wrongQuestionSourceTaskId;

    /**
     * 错误次数
     */
    private Integer wrongQuestionWrongCount;

    /**
     * 掌握度
     */
    private Integer wrongQuestionMasteryLevel;

    /**
     * 最近错误时间
     */
    private LocalDateTime wrongQuestionLastWrongTime;

    /**
     * 最近复习时间
     */
    private LocalDateTime wrongQuestionLastReviewTime;

    /**
     * 复习次数
     */
    private Integer wrongQuestionReviewCount;

    /**
     * 状态
     */
    private Integer wrongQuestionStatus;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("wrong_question_owner_user_id")
    private String ownerUserId;

    /** JOIN 补字段：学生姓名 */
    @TableField(exist = false)
    private String wrongQuestionStudentName;

    /** JOIN 补字段：学生绑定账号 */
    @TableField(exist = false)
    private String wrongQuestionStudentAccount;

    /** JOIN 补字段：题目正文摘要 */
    @TableField(exist = false)
    private String wrongQuestionStem;

    /** JOIN 补字段：题目所属学科（用于列表显示） */
    @TableField(exist = false)
    private String wrongQuestionSubject;
}
