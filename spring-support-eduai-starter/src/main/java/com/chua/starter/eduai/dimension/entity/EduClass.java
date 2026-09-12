package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级维度。
 *
 * <p>班级信息独立存储，{@code classTeacherId} 等关联字段引用
 * {@code spring-support-common-starter} 的用户体系 (userId)，不重复存储用户表。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_class")
public class EduClass {

    @TableId(type = IdType.AUTO)
    /** 类标识 */
    private Long classId;

    /**
     * 班级编码（业务唯一）
     */
    private String classCode;

    /**
     * 班级名称（如 高一(3)班）
     */
    private String className;

    /**
     * 所属学段
     */
    private String classGradeLevel;

    /**
      * 年级（关联 edu_grade.grade_名称）
     */
    private String classGrade;

    /**
       * 班主任 用户标识（来自 spring-支持 用户体系）
     */
    private Long classTeacherId;

    /**
     * 状态：0 停用 / 1 启用
     */
    private Integer classStatus;

    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    /** 创建时间 */
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    /** 更新时间 */
    private LocalDateTime updateTime;
}