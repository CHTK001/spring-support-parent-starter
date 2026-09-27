package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 古诗学段/年级映射实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_poem_grade")
public class EduPoemGrade extends SysBase {

    /**
     * 映射标识
     */
    @TableId(type = IdType.AUTO)
    private Long mapId;

    /**
     * 古诗标识
     */
    private Long poemId;

    /**
     * 学段（小学/初中/高中）
     */
    private String gradeLevel;

    /**
     * 年级（为空表示该学段通用）
     */
    private String grade;

    /**
     * 归属用户 ID
     */
    @TableField("poem_grade_owner_user_id")
    private String ownerUserId;
}
