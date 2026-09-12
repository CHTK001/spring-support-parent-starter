package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 学生档案。
*
* <p>学生的基础信息存于 {@code spring-support-common-starter} 的用户体系；
* 本表只存储教育域扩展属性（学号/班级/入学年份/年级）。
* {@code studentUserId} 是与系统用户的关联键。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("edu_student")
public class EduStudent {

    @TableId(type = IdType.AUTO)
    /** studentid */
    private Long studentId;

    /**
    * 系统用户 用户标识（关联 spring-支持 用户体系，可为空表示待注册）
     */
    private Long studentUserId;

    /**
    * 学号（业务唯一）
     */
    private String studentNo;

    /**
    * 学生姓名（冗余展示）
     */
    private String studentName;

    /**
    * 班级 标识（关联 edu_类.类_标识）
     */
    private Long studentClassId;

    /**
    * 学段
     */
    private String studentGradeLevel;

    /**
    * 年级
     */
    private String studentGrade;

    /**
    * 入学年份
     */
    private Integer studentEnrollYear;

    /**
    * 状态：0 离校 / 1 在读
     */
    private Integer studentStatus;

    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    /** 创建时间 */
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    /** 更新时间 */
    private LocalDateTime updateTime;
}