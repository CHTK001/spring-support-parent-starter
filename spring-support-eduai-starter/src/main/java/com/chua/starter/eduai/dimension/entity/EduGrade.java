package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年级维度。
 *
 * <p>学段 (PRIMARY/JUNIOR/SENIOR/UNIVERSITY) + 年级名 (1-12 / 大一-大四)。
 * 与 {@link EduSubject} 组合形成题目/试卷的完整维度过滤条件。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_grade")
public class EduGrade {

    @TableId(type = IdType.AUTO)
    /**
     * gradeid
    */
    private Long gradeId;

    /**
     * 学段：PRIMARY / JUNIOR / SENIOR / UNIVERSITY
     */
    private String gradeLevel;

    /**
     * 年级名：1-12 或 大一-大四
     */
    private String gradeName;

    /**
     * 学段内的顺序 (1..N)
     */
    private Integer gradeOrder;

    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    /**
     * 创建时间
    */
    private LocalDateTime createTime;
}
