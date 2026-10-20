package com.chua.starter.device.spi.model;

import java.util.List;
import java.util.Set;

/**
 * 设备厂商描述符
 * <p>由各厂商 {@code DeviceProvider#descriptor()} 提供，前端据此渲染厂商下拉、
 * 平台下拉、凭据表单与能力徽标。</p>
 *
 * @param vendor                  厂商标识，例如 hikvision
 * @param name                    厂商名称，例如 海康威视
 * @param authTypes               支持的接入方式
 * @param platforms               支持的平台
 * @param capabilities            能力位
 * @param defaultOfflineThreshold 默认离线阈值（秒）
 * @param docUrl                  对接文档
 * @param description             说明
 * @since 4.0.0.42
 * @author CH
 */
public record DeviceVendorDescriptor(
        String vendor,
        String name,
        List<AuthTypeDescriptor> authTypes,
        List<PlatformDescriptor> platforms,
        Set<DeviceCapability> capabilities,
        int defaultOfflineThreshold,
        String docUrl,
        String description) {

    /**
     * 规范构造器：集合组件做不可变拷贝
     *
     * @param authTypes   接入方式
     * @param platforms   平台
     * @param capabilities 能力位
     */
    public DeviceVendorDescriptor {
        authTypes = authTypes == null ? List.of() : List.copyOf(authTypes);
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
