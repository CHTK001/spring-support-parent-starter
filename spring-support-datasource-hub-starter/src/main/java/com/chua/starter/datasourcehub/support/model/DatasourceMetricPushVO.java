package com.chua.starter.datasourcehub.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SSE 推送载荷：一次探测的最新结果 + 最近的若干个点。
 *
 * <p>一次推送同时给「当前值」和「一小段窗口」而不是只给一个点：页面刚打开时
 * 首屏历史可能还没拉到，如果 SSE 只推增量点，那段时间图上会有一段空白；
 * 带一小段窗口可以让曲线在首屏历史到达之前就已经有形状，
 * 之后两者重叠的部分值一致，不会出现「图先跳一下再被历史覆盖」。</p>
 *
 * @param datasourceId 数据源 id
 * @param success      本次连通与否，未判定为 null
 * @param message      结果说明 / 失败原因
 * @param connectLatencyMs 建连耗时（毫秒）
 * @param sessionLatencyMs 会话耗时（毫秒）
 * @param tunneled     本次是否经隧道
 * @param connectionCount 本次采样时刻客户端连接数，取不到为 null
 * @param points       最近窗口内的采样点
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "数据源指标 SSE 推送载荷")
public record DatasourceMetricPushVO(
        @Schema(description = "数据源 ID") Long datasourceId,
        @Schema(description = "本次连通与否") Boolean success,
        @Schema(description = "结果说明") String message,
        @Schema(description = "建连耗时（毫秒）") Long connectLatencyMs,
        @Schema(description = "会话耗时（毫秒）") Long sessionLatencyMs,
        @Schema(description = "本次是否经隧道") Boolean tunneled,
        @Schema(description = "本次采样时刻客户端连接数") Long connectionCount,
        @Schema(description = "最近窗口内的采样点") DatasourceMetricTrendVO points
) {
}