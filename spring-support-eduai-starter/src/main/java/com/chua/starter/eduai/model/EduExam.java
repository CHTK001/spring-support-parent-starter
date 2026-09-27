package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;
import java.time.LocalDateTime;

/**
 * 考试实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_exam")
public class EduExam extends SysBase {

    /**
     * 考试标识
     */
    @TableId(type = IdType.AUTO)
    /**
     * examid
    */
    private Long examId;

    /**
     * 试卷标识
     */
    private Long examPaperId;

    /**
     * 考试名称
     */
    private String examName;

    /**
     * 班级标识
     */
    private String examClassId;

    /**
     * 开始时间
     */
    private LocalDateTime examStartTime;

    /**
     * 结束时间
     */
    private LocalDateTime examEndTime;

    /**
     * 考试时长
     */
    private Integer examDuration;

    /**
     * 状态
     */
    private Integer examStatus;

    /**
     * 删除标记（0 正常 / 1 已删除），与生命周期列 exam_status 分开
     */
    @TableField("exam_del_flag")
    @TableLogic
    private Integer examDelFlag;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("exam_owner_user_id")
    private String ownerUserId;
}
