package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 创建 webhook appKey 的请求。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class AppKeyCreateRequest {

    /** 凭据名称（同时作为入站数据 source） */
    private String name;

    /** 过期时间，支持 yyyy-MM-dd HH:mm:ss / ISO 格式；留空表示永久有效 */
    private String expiresAt;

    /** 允许推送的主题，逗号分隔；留空默认 access,alarm */
    private String scopes;
}
