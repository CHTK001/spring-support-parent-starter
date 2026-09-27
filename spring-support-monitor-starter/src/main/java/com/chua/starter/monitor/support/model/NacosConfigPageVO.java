package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * Nacos 配置分页结果
 *
 * @param list     当前页配置
 * @param total    总条数
 * @param page     当前页码，从 1 开始
 * @param pageSize 每页条数
 * @author CH
 * @since 4.0.0.42
 */
public record NacosConfigPageVO(
        List<NacosConfigVO> list,
        Integer total,
        Integer page,
        Integer pageSize
) {
    /**
     * 规范构造器：当前页配置列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。列表由 Nacos 返回项逐条组装，
     * 恒不为 null 且元素恒不为 null，故可使用拒绝 null 的 {@link List#copyOf}。</p>
     *
     * @param list 当前页配置
     */
    public NacosConfigPageVO {
        list = List.copyOf(Objects.requireNonNull(list, "list 不能为 null"));
    }
}
