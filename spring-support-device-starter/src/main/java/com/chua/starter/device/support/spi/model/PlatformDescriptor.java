package com.chua.starter.device.spi.model;

import java.util.List;

/**
 * 平台接入描述
 *
 * @param code            平台编码，例如 cloud-eye
 * @param name            平台名称，例如 海康云眸
 * @param defaultBaseUrl  默认 endpoint，可被凭据覆盖
 * @param credentialFields 该平台需要的凭据字段
 * @since 4.0.0.42
 * @author CH
 */
public record PlatformDescriptor(
        String code,
        String name,
        String defaultBaseUrl,
        List<CredentialField> credentialFields) {

    /**
     * 规范构造器：字段列表做不可变拷贝，避免调用方修改描述符
     *
     * @param credentialFields 凭据字段
     */
    public PlatformDescriptor {
        credentialFields = credentialFields == null ? List.of() : List.copyOf(credentialFields);
    }
}
