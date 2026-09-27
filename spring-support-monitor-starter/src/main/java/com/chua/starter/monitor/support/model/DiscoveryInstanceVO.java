package com.chua.starter.monitor.support.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务发现实例
 *
 * @param id       实例标识
 * @param host     实例主机
 * @param port     实例端口
 * @param protocol 实例协议
 * @param weight   负载权重
 * @param env      环境标识
 * @param uriSpec  服务标识（注册时使用的服务路径）
 * @param metadata 实例元数据，由各发现源自由扩展
 * @author CH
 * @since 4.0.0.42
 */
public record DiscoveryInstanceVO(
        String id,
        String host,
        int port,
        String protocol,
        double weight,
        String env,
        String uriSpec,
        Map<String, String> metadata
) {
    /**
     * 规范构造器：实例元数据做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。元数据由各发现源自由扩展，
     * 键与值均不排除为 null，且部分发现源未提供元数据时整体为 null，
     * 因此保留 null 语义并采用可容纳 null 的不可变包装。</p>
     *
     * @param metadata 实例元数据，由各发现源自由扩展
     */
    public DiscoveryInstanceVO {
        metadata = metadata == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
