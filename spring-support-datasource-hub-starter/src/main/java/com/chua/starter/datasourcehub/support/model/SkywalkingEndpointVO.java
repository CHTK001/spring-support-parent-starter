package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 服务端点列表项。
 *
 * @param id          端点 ID
 * @param serviceName 所属服务
 * @param name        端点名（HTTP 路径 / gRPC 全限定方法名等）
 * @param calls       窗口内调用量（未知为 -1）
 * @param avgMs       平均耗时毫秒（未知为 -1）
 * @param p95Ms       P95 毫秒（未知为 -1）
 * @param p99Ms       P99 毫秒（未知为 -1）
 * @param errorRate   错误率百分比（未知为 -1）
 * @param tags        附加属性
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingEndpointVO(String id,
                                   String serviceName,
                                   String name,
                                   long calls,
                                   double avgMs,
                                   double p95Ms,
                                   double p99Ms,
                                   double errorRate,
                                   List<TagVO> tags) {
}
