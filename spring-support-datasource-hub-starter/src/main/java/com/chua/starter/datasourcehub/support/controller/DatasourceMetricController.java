package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.metric.DatasourceMetricStreamRegistry;
import com.chua.starter.datasourcehub.support.model.DatasourceMetricTrendVO;
import com.chua.starter.datasourcehub.support.service.DatasourceMetricService;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 数据源监控指标（SSE 推送 + 趋势）。
 *
 * <p><strong>数据源不存在时直接 404，不建流</strong>：SSE 一旦返回 200，
 * 浏览器就认为链路通了，此后既收不到数据也不会有报错，只会看到一条永远静止
 * 的图 —— 那比直接 404 难排查得多。抛 {@link ResponseStatusException} 而不是
 * 裸 {@code IllegalStateException}，是为了让状态码与原因对得上（404 = 不存在，
 * 而不是笼统的 500「服务器内部错误」）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Tag(name = "数据源中心 - 监控指标", description = "连通性与延迟的实时推送与趋势")
@RestController
@RequestMapping("/ms/datasource/metric")
@RequiredArgsConstructor
public class DatasourceMetricController {

    private final DatasourceMetricService metricService;

    private final DatasourceMetricStreamRegistry streamRegistry;

    /**
     * 数据源服务（用于校验 id 对应的数据源存在）
     */
    private final ObjectProvider<DatasourceService> datasourceServiceProvider;

    /**
     * 订阅指标实时推送（SSE）。
     *
     * <p>建连即回一个 {@code ready} 事件，之后每轮采集（默认 60s）推一个
     * {@code metric} 事件：本次连通与否、两个延迟、是否经隧道，以及最近一小段窗口。
     * 另有 20s 一次的注释帧保活，前端不会把它当事件。</p>
     *
     * <p><strong>首屏历史不在这里给</strong>：SSE 只做增量，历史走
     * {@code /metric/trend}。否则建连那一刻就要推上百个点，
     * 而页面往往在历史拉到之前就断开了。</p>
     *
     * <p><strong>订阅即开启采集</strong>：本端点是这个数据源唯一的探测触发点。
     * 关掉页面就没有采集了，历史从那一刻起不再增长。</p>
     *
     * @param datasourceId 数据源 ID
     * @return SSE 连接
     */
    @Operation(summary = "数据源指标实时推送（SSE）", description = "订阅即开启该数据源的定时探测")
    @GetMapping("/stream/{datasourceId}")
    public SseEmitter stream(@PathVariable Long datasourceId) {
        requireExisting(datasourceId);
        return streamRegistry.subscribe(datasourceId);
    }

    /**
     * 查询指标趋势（首屏历史）。
     *
     * <p>数据来自后台在被订阅期间落的 {@code ms_datasource_metric}。
     * 从未被观测过的数据源返回空点 + 说明文案，不返回一条平线 ——
     * 「没采过」和「一直是 0 延迟」在图上是两件完全不同的事。</p>
     *
     * @param datasourceId 数据源 ID
     * @param hours        回溯小时数，缺省 6，上限 168
     * @param interval     采样间隔（秒），缺省 60；点数超上限时后端自动抽稀
     * @return 趋势
     */
    @Operation(summary = "数据源指标趋势")
    @GetMapping("/trend/{datasourceId}")
    public ReturnResult<DatasourceMetricTrendVO> trend(
            @PathVariable Long datasourceId,
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) Integer interval) {
        requireExisting(datasourceId);
        return metricService.trend(datasourceId, hours, interval);
    }

    /**
     * 校验数据源存在。
     *
     * <p>SSE 建连是长连接：id 不存在时如果照常建立，前端会连上一条
     * 永远不推送、也不报错的连接 —— 比直接报错更难排查。</p>
     *
     * @param datasourceId 数据源 ID
     */
    private void requireExisting(Long datasourceId) {
        DatasourceService service = datasourceServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        ReturnResult<?> found = service.get(datasourceId);
        if (!found.isOk() || found.getData() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在: " + datasourceId);
        }
    }
}