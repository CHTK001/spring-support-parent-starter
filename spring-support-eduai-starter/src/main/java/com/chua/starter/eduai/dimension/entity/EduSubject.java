package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学科维度。
 *
 * <p>独立建表的理由：不同教育机构对"学科"的口径不一致（小学 vs 高中，编程/通用技术等细分）。
 * 把学科作为本模块维度独立管理，避免与 {@code spring-support-common-starter} 的组织/字典冲突。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_subject")
public class EduSubject {

    @TableId(type = IdType.AUTO)
    /**
     * 主题标识
    */
    private Long subjectId;

    /**
     * 学科编码（业务唯一，如 chinese/math/english）
     */
    private String subjectCode;

    /**
     * 学科名（如 语文/数学/英语）
     */
    private String subjectName;

    /**
     * 适用学段，逗号分隔 PRIMARY/JUNIOR/SENIOR/UNIVERSITY
     */
    private String subjectGradeLevels;

    /**
     * 排序
     */
    private Integer subjectSortOrder;

    /**
     * 状态：0 停用 / 1 启用
     */
    private Integer subjectStatus;

    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    /**
     * 创建时间
    */
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
    */
    private LocalDateTime updateTime;
}
