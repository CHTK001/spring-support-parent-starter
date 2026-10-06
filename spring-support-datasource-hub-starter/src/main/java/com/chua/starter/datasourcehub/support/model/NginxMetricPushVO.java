package com.chua.starter.datasourcehub.support.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 指标 SSE 推送载荷。
 *
 * <p>每轮采集后推送<strong>该数据源每个指标的最后一个点</strong>，而不是整段历史：
 * 首屏历史由 {@code GET /metric/trend} 一次性拉取，SSE 只负责把新点增量送到，
 * 否则每 30s 就把 24h 的 288 个点全量重推一遍，前端和带宽都白费。</p>
 *
 * <p>前端按 {@code (name, time)} 去重后追加：同一时刻重复收到（多实例推送、
 * 客户端重连补发）不会在曲线上叠出两个点。</p>
 *
 * @param datasourceId 数据源 id
 * @param points       本次新增的点，每个指标一个
 * @author CH
 * @since 4.0.0
 */
public record NginxMetricPushVO(
        Long datasourceId,
        List<NginxTrendVO.Point> points
) {

    /**
     * 规范化集合组件，阻断外部别名写入。
     *
     * <p>{@code points} 由服务层保证非空（空列表直接不推），此处不加
     * {@code requireNonNull}：允许为 null，前端按空处理即可，
     * 加了会把「这轮没数据」变成 NPE。</p>
     */
    public NginxMetricPushVO {
        points = points == null ? null : List.copyOf(points);
    }

    /**
     * 按指标名去重，只留每个指标最后一个点。
     *
     * <p>{@code latestPoints} 内部已去重，这里再做一次是为了让本 VO
     * 对任何调用方都保证「同名只有一个点」，不依赖上游实现细节。</p>
     *
     * @return 指标名 → 点
     */
    public Map<String, NginxTrendVO.Point> byName() {
        if (points == null || points.isEmpty()) {
            return Map.of();
        }
        Map<String, NginxTrendVO.Point> map = points.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.name() != null)
                .collect(Collectors.toMap(
                        NginxTrendVO.Point::name,
                        Function.identity(),
                        // 同名取时间较晚的一个
                        (a, b) -> b.time() != null && a.time() != null
                                && b.time().isAfter(a.time()) ? b : a));
        return Map.copyOf(map);
    }
}
