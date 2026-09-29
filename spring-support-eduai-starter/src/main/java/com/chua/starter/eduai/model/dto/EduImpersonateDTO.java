package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * 管理员代入学生身份的请求体。
 *
 * @param studentId     学生标识，必填
 * @param expireSeconds 临时令牌有效期（秒）；为空由服务端取默认值
 * @param allowedUrls   允许访问的路径前缀；为空时收窄到学生端路径
 *
 * @since 4.0.0.42
 * @author CH
 */
public record EduImpersonateDTO(Long studentId, Integer expireSeconds, List<String> allowedUrls) {
}
