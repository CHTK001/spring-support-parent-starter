package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师档案。
 *
 * <p>与 {@link EduStudent} 类似：基础用户信息来自 spring-support 用户体系，
 * 本表只保存教学域扩展属性（工号/学科/班级）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_teacher")
public class EduTeacher {

    @TableId(type = IdType.AUTO)
    /** TeacherID */
    private Long teacherId;

    /**
     * 系统用户 userId
     */
    private Long teacherUserId;

    /**
     * 工号
     */
    private String teacherNo;

    /**
     * 教师姓名（冗余展示）
     */
    private String teacherName;

    /**
     * 任教学科编码（关联 edu_subject.subject_code）
     */
    private String teacherSubjectCode;

    /**
     * 状态：0 离职 / 1 在职
     */
    private Integer teacherStatus;

    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    /** Create时间 */
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    /** Update时间 */
    private LocalDateTime updateTime;
}