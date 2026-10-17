package com.chua.starter.eduai.model.dto;

/**
 * 教师绑定 DTO
 *
 * @param teacherNo   工号（绑定键）
 * @param teacherName 姓名
 * @param subjectCode 任教学科编码（可选）
 * @since 4.0.0.42
 * @author CH
 */
public record TeacherBindDTO(
        String teacherNo,
        String teacherName,
        String subjectCode) {
}
