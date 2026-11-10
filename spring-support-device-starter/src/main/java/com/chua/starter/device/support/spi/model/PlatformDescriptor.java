package com.chua.starter.device.spi.model;

import java.util.List;
import java.util.Set;

/**
 * 平台接入描述
 *
 * <p>每个厂商下可有多个平台（例如海康同时有云曜、云眸、综合安防、萤石、ISUP），
 * 各平台能力差别很大。因此能力位必须声明在平台维度，而不是厂商维度——
 * 前端按「选中的平台」决定显示哪些功能入口，避免出现「萤石平台显示组织架构」这种错误。</p>
 *
 * @param code            平台编码，例如 cloud-eye
 * @param name            平台名称，例如 海康云眸
 * @param defaultBaseUrl  默认 endpoint，可被凭据覆盖
 * @param credentialFields 该平台需要的凭据字段
 * @param capabilities    该平台支持的能力位，前端据此显隐功能入口
 * @since 4.0.0.42
 * @author CH
 */
public record PlatformDescriptor(
        String code,
        String name,
        String defaultBaseUrl,
        List<CredentialField> credentialFields,
        Set<DeviceCapability> capabilities) {

    /**
     * 兼容构造器：未声明能力位时按空集处理，前端视为「能力未知」。
     *
     * @param code             平台编码
     * @param name             平台名称
     * @param defaultBaseUrl   默认 endpoint
     * @param credentialFields 凭据字段
     */
    public PlatformDescriptor(String code, String name, String defaultBaseUrl,
                              List<CredentialField> credentialFields) {
        this(code, name, defaultBaseUrl, credentialFields, Set.of());
    }

    /**
     * 规范构造器：集合做不可变拷贝，避免调用方修改描述符
     *
     * @param credentialFields 凭据字段
     * @param capabilities     能力位
     */
    public PlatformDescriptor {
        credentialFields = credentialFields == null ? List.of() : List.copyOf(credentialFields);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    /**
     * 该平台是否支持某能力。
     *
     * @param capability 能力位
     * @return 支持返回 true
     */
    public boolean supports(DeviceCapability capability) {
        return capabilities.contains(capability);
    }
}
