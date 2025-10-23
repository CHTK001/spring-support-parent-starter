package com.chua.starter.oauth.client.support.entity;

import lombok.Builder;
import lombok.Data;

/**
 * 厂商用户
 *
 * @author CH
 * @since 2025/10/23 21:05
 */
@Data
@Builder
public class ManufacturerUser {

    /**
     * 用户ID
     * <p>示例: "USER001"</p>
     */
    private String userId;
    /**
     * 客户端IP
     * <p>示例: "192.168.1.1"</p>
     */
    private String clientIp;
    /**
     * 用户名
     * <p>示例: "张三"</p>
     */
    private String username;

    /**
     * 用户编码
     * <p>示例: "USER001"</p>
     */
    private String userCode;
}
