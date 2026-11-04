package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * SkyWalking 总览。
 *
 * @param datasourceId     数据源 ID
 * @param datasourceName   数据源名称
 * @param url              OAP 地址（lake 地址或 host:port）
 * @param version          OAP 版本，未知为 "unknown"
 * @param available        是否可用
 * @param enabled          是否已启用
 * @param message          不可用时的原因
 * @param capabilities     能力清单
 * @param checkedAt        探测时间（毫秒戳）
 * @param serviceCount     服务数
 * @param alarmCount       活跃告警数
 * @param recentAlarmCount 最近 15 分钟告警数（0 表示未查询或暂不支持）
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingOverviewVO(Long datasourceId,
                                   String datasourceName,
                                   String url,
                                   String version,
                                   boolean available,
                                   boolean enabled,
                                   String message,
                                   List<String> capabilities,
                                   long checkedAt,
                                   int serviceCount,
                                   int alarmCount,
                                   int recentAlarmCount) {
}
