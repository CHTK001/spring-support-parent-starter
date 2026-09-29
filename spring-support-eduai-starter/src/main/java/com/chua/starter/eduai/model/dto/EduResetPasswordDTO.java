package com.chua.starter.eduai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 管理员重置学生口令请求。
 *
 * <p>与「学生自助改密」分成两个端点：管理员侧无需旧口令（他本就掌握档案），
 * 但服务层仍会校验归属，避免越权重置他人学生口令。</p>
 *
 * @param studentId   学生标识
 * @param newPassword 新口令明文；只进不出，服务层读后立刻转摘要
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EduResetPasswordDTO(Long studentId, String newPassword) {
}
