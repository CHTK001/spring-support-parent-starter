package com.chua.starter.monitor.server.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.indicator.TimeIndicator;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.pojo.IndicatorQuery;
import com.chua.starter.monitor.server.pojo.JvmQuery;
import com.chua.starter.redis.support.service.TimeSeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.chua.starter.monitor.server.constant.RedisConstant.REDIS_TIME_SERIES_INDICATOR_PREFIX;
import static com.chua.starter.monitor.server.constant.RedisConstant.REDIS_TIME_SERIES_REPORT_PREFIX;


/**
 * 终端
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/time/series")
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
    @GetMapping
    public ReturnResult<List<TimeIndicator>> list(IndicatorQuery indicatorQuery) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(indicatorQuery.getId())) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        return timeSeriesService.range(REDIS_TIME_SERIES_INDICATOR_PREFIX + indicatorQuery.getId() + ":" + indicatorQuery.getType()+ ":" + indicatorQuery.getName(), indicatorQuery.getFromTimestamp(), indicatorQuery.getToTimestamp(), indicatorQuery.getCount());
    }
    /**
     * 查询基本信息。
     *
     * @param jvmQuery 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询JVM信息")
    @GetMapping("jvm")
    public ReturnResult<List<TimeIndicator>> listJvm(JvmQuery jvmQuery) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(jvmQuery.getId())) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        return timeSeriesService.range(REDIS_TIME_SERIES_REPORT_PREFIX +
                        jvmQuery.getAppName() + ":" +
                        jvmQuery.getServerHost() + "_" +
                        jvmQuery.getServerPort() + ":" +
                        jvmQuery.getType(),
                jvmQuery.getFromTimestamp(),
                jvmQuery.getToTimestamp(),
                jvmQuery.getCount());
    }
}