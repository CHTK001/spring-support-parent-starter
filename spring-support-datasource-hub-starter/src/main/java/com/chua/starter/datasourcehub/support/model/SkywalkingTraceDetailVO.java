package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 链路详情（与 OtnlTrace 对齐，含 Span 明细）。
 *
 * @param traceId      链路 ID
 * @param serviceName  根 Span 所属服务
 * @param endpointName 根 Span 端点名
 * @param durationMs   总耗时毫秒
 * @param startTime    起始毫秒戳
 * @param endTime      结束毫秒戳
 * @param error        是否含错误 Span
 * @param spanCount    Span 数
 * @param spans        Span 明细
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingTraceDetailVO(String traceId,
                                      String serviceName,
                                      String endpointName,
                                      long durationMs,
                                      long startTime,
                                      long endTime,
                                      boolean error,
                                      int spanCount,
                                      List<TraceSpanVO> spans) {
}
