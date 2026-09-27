package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 教师绑定 DTO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class TeacherBindDTO {

    /**
     * 工号（绑定键）
     */
    private String teacherNo;

    /**
     * 姓名
     */
    private String teacherName;

    /**
     * 任教学科编码（可选）
     */
    private String subjectCode;
}
