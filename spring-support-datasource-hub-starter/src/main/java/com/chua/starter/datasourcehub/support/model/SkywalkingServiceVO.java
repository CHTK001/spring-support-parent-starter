package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 服务列表。
 *
 * @param id        服务 ID（SkyWalking 侧标识，可空）
 * @param name      服务名
 * @param group     服务组（可空）
 * @param layers    分层（SkyWalking 10 起有 MESH / GENERAL / VIRTUAL_DATABASE …）
 * @param instances 实例数
 * @param endpoints 端点数
 * @param calls     窗口内调用量（未知为 -1）
 * @param avgMs     平均耗时毫秒（未知为 -1）
 * @param p95Ms     P95 毫秒（未知为 -1）
 * @param p99Ms     P99 毫秒（未知为 -1）
 * @param errorRate 错误率百分比（未知为 -1）
 * @param tags      附加属性
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingServiceVO(String id,
                                  String name,
                                  String group,
                                  List<String> layers,
                                  int instances,
                                  int endpoints,
                                  long calls,
                                  double avgMs,
                                  double p95Ms,
                                  double p99Ms,
                                  double errorRate,
                                  List<TagVO> tags) {
}
