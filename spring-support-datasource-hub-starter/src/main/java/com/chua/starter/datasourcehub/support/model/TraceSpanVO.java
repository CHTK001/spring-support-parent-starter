package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 链路详情中的 Span。
 *
 * @param traceId      链路 ID
 * @param segmentId    段 ID
 * @param spanId       Span ID
 * @param parentSpanId 父 Span ID，根 Span 为空
 * @param serviceName  所属服务
 * @param instanceName 所属实例
 * @param endpointName 端点名/操作名
 * @param layer        分层（HTTP / RPC_FRAMEWORK / DB / CACHE / MQ …）
 * @param type         类型（Entry / Exit / Local）
 * @param component    组件名
 * @param peer         对端地址（出口 Span 才有）
 * @param startTime    起始毫秒戳
 * @param endTime      结束毫秒戳
 * @param durationMs   耗时毫秒
 * @param error        是否错误
 * @param tags         标签
 * @param logs         日志事件
 * @author CH
 * @since 4.0.0.51
 */
public record TraceSpanVO(String traceId,
                          String segmentId,
                          String spanId,
                          String parentSpanId,
                          String serviceName,
                          String instanceName,
                          String endpointName,
                          String layer,
                          String type,
                          String component,
                          String peer,
                          long startTime,
                          long endTime,
                          long durationMs,
                          boolean error,
                          List<TagVO> tags,
                          List<TraceSpanLogVO> logs) {
}
