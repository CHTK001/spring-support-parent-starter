package com.chua.starter.device.spi.model;

import lombok.Data;

import java.util.Map;

/**
 * 设备连接信息模型
 * <p>支持 SDK 直连（IP+端口+用户名密码）与平台接入（AppKey+AppSecret+设备序列号）双模式。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class DeviceConnectInfo {

    /**
     * 厂商标识，例如 hikvision、dahua
     */
    private String vendor;

    /**
     * 认证类型，可选值：SDK（局域网直连）、PLATFORM（云平台接入）
     */
    private String authType;

    /**
     * 设备 IP 地址（SDK 模式）
     */
    private String ip;

    /**
     * 设备端口（SDK 模式）
     */
    private Integer port;

    /**
     * 用户名（SDK 模式）
     */
    private String username;

    /**
     * 密码（SDK 模式）
     */
    private String password;

    /**
     * 平台类型，例如 isup、ezviz、dahua-cloud
     */
    private String platformType;

    /**
     * 应用 Key（平台模式）
     */
    private String appKey;

    /**
     * 应用密钥（平台模式）
     */
    private String appSecret;

    /**
     * 设备序列号（平台模式）
     */
    private String deviceSerial;

    /**
     * 扩展字段
     */
    private Map<String, Object> extra;
}
