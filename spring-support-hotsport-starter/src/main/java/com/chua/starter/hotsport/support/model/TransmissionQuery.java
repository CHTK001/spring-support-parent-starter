package com.chua.starter.hotsport.support.model;

import lombok.Data;

/**
 * 传输记录查询参数对象。
 *
 * <p>字段名与 {@code /api/apm/transmissions}、{@code /api/apm/export/transmissions}
 * 的 HTTP 查询参数一一对应，由 Spring 按属性名直接绑定；改动字段名即改变接口契约。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class TransmissionQuery {

    /** 页码，从 1 起，缺省 1。 */
    private Integer page;

    /** 每页条数，缺省 20。 */
    private Integer size;

    /** 源端过滤（包含匹配，可空）。 */
    private String source;

    /** 目标端过滤（包含匹配，可空）。 */
    private String target;

    /** 协议过滤（可空）。 */
    private String protocol;

    /** 仅看错误（可空）。 */
    private Boolean errorOnly;

    /** 开始时间（epoch 毫秒，可空）。 */
    private Long startTime;

    /** 结束时间（epoch 毫秒，可空）。 */
    private Long endTime;

    /** 链路 ID 精确匹配（可空）。 */
    private String traceId;

    /** 最小耗时（毫秒，可空）。 */
    private Long durationMin;

    /** 最大耗时（毫秒，可空）。 */
    private Long durationMax;

    /** 操作名模糊过滤（可空）。 */
    private String operation;
}
