package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 日志列表项。
 *
 * @param timestamp    毫秒戳
 * @param serviceName  服务名
 * @param instanceName 实例名
 * @param endpointName 端点名
 * @param traceId      关联链路 ID（可空）
 * @param level        级别（INFO / WARN / ERROR …）
 * @param contentType  MIME 类型
 * @param content      日志正文
 * @param tags         标签
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingLogRecordVO(long timestamp,
                                    String serviceName,
                                    String instanceName,
                                    String endpointName,
                                    String traceId,
                                    String level,
                                    String contentType,
                                    String content,
                                    List<TagVO> tags) {
}
