package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * 服务发现实例查询结果
 *
 * @param instances 命中的实例列表，未命中时为空列表
 * @param message   查询说明：正常为 ok，否则为前端可直接展示的中文原因
 * @author CH
 * @since 4.0.0.42
 */
public record DiscoveryQueryVO(List<DiscoveryInstanceVO> instances, String message) {
    /**
     * 规范构造器：实例列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。未命中时列表为空列表而非 null，
     * 元素恒不为 null，故可使用拒绝 null 的 {@link List#copyOf}。</p>
     *
     * @param instances 命中的实例列表，未命中时为空列表
     * @param message   查询说明：正常为 ok，否则为前端可直接展示的中文原因
     */
    public DiscoveryQueryVO {
        instances = List.copyOf(Objects.requireNonNull(instances, "instances 不能为 null"));
    }
}
