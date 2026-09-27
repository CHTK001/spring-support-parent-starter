package com.chua.starter.monitor.support.model;

import java.util.Map;
import java.util.Objects;

/**
 * Nacos 服务实例。
 *
 * @param ip          实例 IP
 * @param port        实例端口
 * @param weight      权重
 * @param healthy     是否健康
 * @param enabled     是否启用（上下线）
 * @param ephemeral   是否临时实例
 * @param clusterName 所属集群
 * @param metadata    实例元数据
 * @author CH
 * @since 4.0.0.43
 */
public record NacosInstanceVO(
        String ip,
        Integer port,
        Double weight,
        Boolean healthy,
        Boolean enabled,
        Boolean ephemeral,
        String clusterName,
        Map<String, String> metadata
) {
    /**
     * 规范构造器：实例元数据做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。元数据由 metadata 对象逐项转文本，
     * 缺项时为空映射，键取自 JSON 键、值取自文本化结果，均不为 null，
     * 故可使用拒绝 null 的 {@link Map#copyOf}。</p>
     *
     * @param metadata 实例元数据
     */
    public NacosInstanceVO {
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata 不能为 null"));
    }
}
