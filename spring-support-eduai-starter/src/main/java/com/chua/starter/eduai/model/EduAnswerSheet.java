package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答题卡实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_answer_sheet")
public class EduAnswerSheet {

    /**
     * 答题卡标识
     */
    @TableId(type = IdType.AUTO)
    /**
     * answersheetid
    */
    private Long answerSheetId;

    /**
     * 考试标识
     */
    private Long answerSheetExamId;

    /**
     * 学生标识
     */
    private String answerSheetStudentId;

    /**
     * 答题内容JSON
     */
    private String answerSheetContent;

    /**
     * 提交时间
     */
    private LocalDateTime answerSheetSubmitTime;

    /**
     * 状态
     */
    private Integer answerSheetStatus;

    /**
     * 创建时间
     */
    private LocalDateTime answerSheetCreateTime;

    /**
     * 更新时间
     */
    private LocalDateTime answerSheetUpdateTime;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("answer_sheet_owner_user_id")
    private String ownerUserId;
}
