package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师班级配置（老师端数据范围）。
 *
 * <p>老师端只能做「布置」等教学功能，其数据范围由管理员在后台
 * （学生管理 → 教师 → 配置班级）统一配置：本表一行即代表
 * 「某教师可看 / 可操作某班级」。班级本身由管理员维护，
 * 老师端不再自建班级，老师的班级列表完全来自本表。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_teacher_class")
public class EduTeacherClass {

    @TableId(type = IdType.AUTO)
    /**
     * 关联标识
     */
    private Long teacherClassId;

    /**
     * 教师标识（edu_teacher.teacher_id）
     */
    private Long teacherClassTeacherId;

    /**
     * 班级标识（edu_class.class_id）
     */
    private Long teacherClassClassId;

    /**
     * 配置时间
     */
    private LocalDateTime createTime;
}
