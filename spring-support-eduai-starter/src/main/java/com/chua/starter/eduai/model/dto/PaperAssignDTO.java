package com.chua.starter.eduai.model.dto;

/**
 * 班级试卷布置请求。
 *
 * @param paperId 试卷标识
 * @param classId 班级标识（必须是当前老师名下的班级）
 * @param remark  备注，可空（如截止时间、说明）
 * @since 4.0.0.42
 * @author CH
 */
public record PaperAssignDTO(Long paperId, Long classId, String remark) {
}
