package com.chua.starter.eduai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 学生自助修改口令请求。
 *
 * <p>学生标识不放在请求体里：登录态中的 {@code userId} 就是学号，
 * 由服务端自行解析——放进请求体会让「改谁的口令」变成客户端说了算，
 * 等于任何拿到令牌的人都能改他人口令。</p>
 *
 * @param oldPassword 旧口令明文；留空表示不校验（首次设密场景由服务层另行判定）
 * @param newPassword 新口令明文；只进不出，服务层读后立刻转摘要
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EduChangePasswordDTO(String oldPassword, String newPassword) {
}
