package com.chua.starter.eduai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 管理员重置教师登录口令请求。
 *
 * <p>与学生侧 {@link EduResetPasswordDTO} 分开：教师口令用于「工号即登录名」的教师登录，
 * 两者表与语义不同，共用一个 DTO 会把 {@code studentId} 这个字段名带进教师链路，
 * 读代码时极易误解。服务层同样校验归属，避免越权重置他人教师口令。</p>
 *
 * @param teacherId   教师标识
 * @param newPassword 新口令明文；只进不出，服务层读后立刻转摘要
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EduTeacherResetPasswordDTO(Long teacherId, String newPassword) {
}
