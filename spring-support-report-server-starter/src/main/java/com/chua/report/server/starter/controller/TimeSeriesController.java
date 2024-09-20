package com.chua.report.server.starter.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.indicator.DataIndicator;
import com.chua.common.support.session.indicator.TimeIndicator;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.constant.RedisConstant;
import com.chua.report.server.starter.pojo.IndicatorQuery;
import com.chua.starter.redis.support.service.TimeSeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


/**
 * 终端
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/time")
@Tag(name = "时序信息")
@RequiredArgsConstructor
@Getter
public class TimeSeriesController {

    private final TimeSeriesService timeSeriesService;
    /**
     * 查询基本信息。
     *
     * @param indicatorQuery 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询指标信息")
    @GetMapping("series")
    public ReturnResult<List<TimeIndicator>> list(IndicatorQuery indicatorQuery) {
        return timeSeriesService.range(RedisConstant.REDIS_TIME_SERIES_PREFIX + indicatorQuery.getName(),
                indicatorQuery.getFromTimestamp(),
                indicatorQuery.getToTimestamp(),
                indicatorQuery.isLatest(),
                indicatorQuery.getCount());
    }
    /**
     * 查询基本信息。
     *
     * @param indicatorQuery 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询指标信息")
    @GetMapping("multi")
    public ReturnResult<Map<String, List<TimeIndicator>>> mRange(IndicatorQuery indicatorQuery) {
        return timeSeriesService.mRange(RedisConstant.REDIS_TIME_SERIES_PREFIX + indicatorQuery.getName(),
                indicatorQuery.getFromTimestamp(),
                indicatorQuery.getToTimestamp(),
                indicatorQuery.isLatest(),
                indicatorQuery.getCount());
    }
    /**
     * 查询基本信息。
     *
     * @param indicatorQuery 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询指标信息")
    @GetMapping("get")
    public ReturnResult<DataIndicator> get(IndicatorQuery indicatorQuery) {
        return timeSeriesService.get(RedisConstant.REDIS_SIMPLE_SERIES_PREFIX + indicatorQuery.getName(), indicatorQuery.getFromTimestamp(), indicatorQuery.getToTimestamp(), indicatorQuery.getCount());
    }
}
