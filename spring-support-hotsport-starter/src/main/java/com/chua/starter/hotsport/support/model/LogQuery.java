package com.chua.starter.hotsport.support.model;

import lombok.Data;

/**
 * 日志查询参数对象。
 *
 * <p>字段名与 {@code /api/apm/logs}、{@code /api/apm/export/logs}
 * 的 HTTP 查询参数一一对应，由 Spring 按属性名直接绑定；改动字段名即改变接口契约。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class LogQuery {

    /** 页码，从 1 起，缺省 1。 */
    private Integer page;

    /** 每页条数，缺省 20。 */
    private Integer size;

    /** 级别过滤（可空）。 */
    private String level;

    /** 关键字过滤（匹配 message/logger，可空）。 */
    private String keyword;

    /** 链路 ID 精确匹配（可空）。 */
    private String traceId;

    /** 开始时间（epoch 毫秒，可空）。 */
    private Long startTime;

    /** 结束时间（epoch 毫秒，可空）。 */
    private Long endTime;

    /** 类名过滤（包含匹配，可空）。 */
    private String className;
}
