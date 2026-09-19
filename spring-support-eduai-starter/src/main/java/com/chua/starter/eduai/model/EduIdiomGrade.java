package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成语学段映射实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_idiom_grade")
public class EduIdiomGrade {

    /**
     * 映射标识
     */
    @TableId(type = IdType.AUTO)
    private Long mapId;

    /**
     * 成语标识
     */
    private Long idiomId;

    /**
     * 学段（小学/初中/高中）
     */
    private String gradeLevel;

    /**
     * 年级（一年级/二年级...）
     */
    private String grade;

    /**
     * 学期（上/下）
     */
    private String semester;

    /**
     * 教材版本（人教版/苏教版等）
     */
    private String textbookVersion;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
