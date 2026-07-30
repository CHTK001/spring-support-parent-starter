package com.chua.starter.server.support.model;

import lombok.Builder;

/**
 * 服务发现标准结果。
 *
 * @param serviceName 服务名称
 * @param serviceType 服务类型
 * @param runtimeStatus 运行状态
 * @param installPath 安装路径
 * @param description 描述信息
 * @param metadataJson 扩展元数据
 * @author CH
 */
@Builder
public record ServerDetectedService(
        String serviceName,
        String serviceType,
        String runtimeStatus,
        String installPath,
        String description,
        String metadataJson
) {
}

