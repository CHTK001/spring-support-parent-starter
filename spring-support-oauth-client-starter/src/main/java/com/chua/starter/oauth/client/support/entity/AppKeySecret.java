package com.chua.starter.oauth.client.support.entity;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 应用密钥实体类
 * 
 * @author CH
 * @since 2025/10/23 21:54
 */
@Data
@Accessors(chain = true)
public class AppKeySecret implements Serializable {

    /**
     * 用户编码
     * 示例: "user_1234567890"
     */
    private String userCode;
    /**
     * 时间戳
     * 示例: "1609459200000"
     */
    private String xTime;
    
    /**
     * 随机字符串
     * 示例: "abcdefghijklmnopqrstuvwxyz"
     */
    private String xRandom;
    
    /**
     * 签名信息
     * 示例: "e10adc3949ba59abbe56e057f20f883e"
     */
    private String xSign;
    
    /**
     * 应用ID
     * 示例: "app_1234567890"
     */
    private String appId;
    
    /**
     * 请求体内容
     * 示例: "{\"username\":\"test\",\"password\":\"123456\"}"
     */
    private String body;
}
