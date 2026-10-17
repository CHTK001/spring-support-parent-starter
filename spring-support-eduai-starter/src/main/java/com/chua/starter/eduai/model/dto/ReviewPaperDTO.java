package com.chua.starter.eduai.model.dto;

/**
 * 复习卷生成DTO
 *
 * @param studentId 学生标识
 * @param count     数量
 * @param subject   学科
 * @since 4.0.0.42
 * @author CH
 */
public record ReviewPaperDTO(
        String studentId,
        Integer count,
        String subject) {
}
