package com.chua.starter.oauth.client.support.key;

import lombok.Data;

/**
 * 应用密钥配置类
 * 用于存储应用的appId和appSecret信息
 *
 * @author CH
 * @since 2025/10/23 20:48
 */
@Data
public class AppKey {
    
    /**
     * 应用ID，用于唯一标识一个应用
     * 示例：APP_20251023_XYZ
     */
    private String appId;
    
    /**
     * 应用密钥，用于应用身份验证
     * 示例：SECRET_20251023_ABCDEFGHIJKLMN
     */
    private String appSecret;
}
