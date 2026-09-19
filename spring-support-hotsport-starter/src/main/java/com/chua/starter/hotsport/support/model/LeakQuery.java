package com.chua.starter.hotsport.support.model;

import lombok.Data;

/**
 * 资源泄漏查询参数对象。
 *
 * <p>字段名与 {@code /api/apm/leaks}、{@code /api/apm/export/leaks}
 * 的 HTTP 查询参数一一对应，由 Spring 按属性名直接绑定；改动字段名即改变接口契约。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class LeakQuery {

    /**
     * 页码，从 1 起，缺省 1。
    */
    private Integer page;

    /**
     * 每页条数，缺省 20。
    */
    private Integer size;

    /**
     * 资源类型过滤（可空）。
    */
    private String resourceType;

    /**
     * 最小存活毫秒（可空）。
    */
    private Long minAgeMs;

    /**
     * 开始时间（epoch 毫秒，可空）。
    */
    private Long startTime;

    /**
     * 结束时间（epoch 毫秒，可空）。
    */
    private Long endTime;

    /**
     * 资源名过滤（包含匹配，可空）。
    */
    private String resourceName;

    /**
     * 线程名过滤（包含匹配，可空）。
    */
    private String thread;

    /**
     * 仅返回活跃泄漏（可空）。
    */
    private Boolean activeOnly;
}
