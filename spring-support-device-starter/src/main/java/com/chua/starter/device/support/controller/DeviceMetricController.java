package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.entity.DeviceMetricLatest;
import com.chua.starter.device.support.service.DeviceMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备环境指标控制器。
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
