package com.chua.starter.device.spi.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备连接信息模型
 * <p>支持 SDK 直连（IP+端口+用户名密码）与平台接入（AppKey+AppSecret+设备序列号）双模式。</p>
 *
 * @param vendor 厂商标识，例如 hikvision、dahua
 * @param authType 认证类型，可选值：SDK（局域网直连）
 *                 、PLATFORM（云平台接入）
 * @param ip 设备 IP 地址（SDK 模式）
 * @param port 设备端口（SDK 模式）
 * @param username 用户名（SDK 模式）
 * @param password 密码（SDK 模式）
 * @param platformType 平台类型，例如 isup、ezviz、dahua-cloud
 * @param appKey 应用 Key（平台模式）
 * @param appSecret 应用密钥（平台模式）
 * @param deviceSerial 设备序列号（平台模式）
 * @param extra 扩展字段
 *
 * @since 4.0.0.42
 * @author CH
 */
public record DeviceConnectInfo(
        String vendor,
        String authType,
        String ip,
        Integer port,
        String username,
        String password,
        String platformType,
        String appKey,
        String appSecret,
        String deviceSerial,
        Map<String, Object> extra
) {

    /**
     * 规范构造器：对扩展字段做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变；重建连接的调用点允许整体传 {@code null}，
     * 且扩展字段值允许为空，故保留空值语义并采用允许空值的不可变包装而非 {@code Map.copyOf}。</p>
     *
     * @param extra 扩展字段，可为 {@code null}
     */
    public DeviceConnectInfo {
        extra = extra == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }
}
