package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.entity.DeviceMetricLatest;
import com.chua.starter.device.support.service.DeviceMetricService;
import com.chua.starter.device.support.timeseries.DeviceSeriesCategory;
import com.chua.starter.device.support.timeseries.DeviceSeriesPoint;
import com.chua.starter.device.support.timeseries.DeviceSeriesQuery;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备环境指标控制器。
 *
 * <p>「最新快照」走明细表（每个节点一行），「历史读数」走时序引擎：
 * 传感器按分钟上报，历史行只进时序存储，不撑大设备库。</p>
 *
 * @since 4.0.0.54
 * @author CH
 */
@RestController
@RequestMapping("/api/device/metrics")
@RequiredArgsConstructor
public class DeviceMetricController {

    /** 环境指标服务 */
    private final DeviceMetricService metricService;

    /** 时序引擎（历史读数） */
    private final DeviceTimeSeriesEngine seriesEngine;

    /**
     * 查询设备最新指标。
     *
     * @param deviceId 设备 ID
     * @return 指标快照
     */
    @GetMapping("/{deviceId}")
    public List<DeviceMetricLatest> latest(@PathVariable String deviceId) {
        return metricService.listLatest(deviceId);
    }

    /**
     * 查询设备历史读数（传感器记录）。
     *
     * @param deviceId 设备 ID
     * @param key      节点标识，可空（空表示该设备全部节点）
     * @param start    起始时间，可空
     * @param end      结束时间，可空
     * @param limit    条数，默认 500，上限 5000
     * @param order    排序：desc（默认，最新的在前）/ asc
     * @return 历史读数时序点
     */
    @GetMapping("/{deviceId}/history")
    public List<DeviceSeriesPoint> history(@PathVariable String deviceId,
                                           @RequestParam(required = false) String key,
                                           @RequestParam(required = false) String start,
                                           @RequestParam(required = false) String end,
                                           @RequestParam(defaultValue = "0") int limit,
                                           @RequestParam(defaultValue = "desc") String order) {
        DeviceSeriesQuery query = new DeviceSeriesQuery();
        query.setCategory(DeviceSeriesCategory.METRIC.code());
        query.setDeviceId(deviceId);
        query.setKey(key);
        query.setFrom(DeviceSeriesQuery.parseTime(start));
        query.setTo(DeviceSeriesQuery.parseTime(end));
        query.setLimit(limit);
        query.setAscending("asc".equalsIgnoreCase(order == null ? "" : order.trim()));
        return seriesEngine.query(query);
    }

    /**
     * 立即采集设备指标（详情页手动刷新使用）。
     *
     * @param deviceId 设备 ID
     * @return 写入行数
     */
    @PostMapping("/{deviceId}/collect")
    public int collect(@PathVariable String deviceId) {
        return metricService.collectDevice(deviceId);
    }
}
