package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.service.HostMetricHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 主机内置指标历史查询接口（不依赖监控项）。
 *
 * <p>返回主机近 N 小时的 CPU / 内存 / 磁盘使用率、磁盘 IO 与网络收发，每行
 * {@code [时间戳ms, cpu, mem, disk, diskReadKbs, diskWriteKbs, netRxKbs, netTxKbs]}，
 * 供主机监控详情页初始化基础数据与图表历史弹窗。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/ms/monitor/host")
@RequiredArgsConstructor
public class HostMetricHistoryController {

    /**
     * 主机指标历史服务
     */
    private final HostMetricHistoryService hostMetricHistoryService;

    /**
     * 查询主机指标历史。
     *
     * @param hostId 主机台账标识
     * @param hours  回溯小时数，默认 1
     * @return data 为 [[ts, cpu, mem, disk, diskReadKbs, diskWriteKbs, netRxKbs, netTxKbs], ...] 升序
     */
    @GetMapping("/{hostId}/metric-history")
    public ReturnResult<List<List<Object>>> metricHistory(
            @PathVariable Long hostId,
            @RequestParam(defaultValue = "1") int hours) {
        try {
            return ReturnResult.ok(hostMetricHistoryService.list(hostId, hours));
        } catch (Exception e) {
            log.warn("[monitor] 主机历史查询失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }
}
