package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控指标 Service
 * <p>
 * 负责指标点批量入库与时序查询, 强调高吞吐与背压友好。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
public interface MetricService {

    /**
     * 批量写入一次采集结果
     *
     * @param result 采集结果
     * @return 写入结果
     */
    ReturnResult<Void> writeCollectResult(CollectResult result);

    /**
     * 直接写入指标点列表(指定主机)
     *
     * @param monitorId 主机 id
     * @param points 指标点
     * @param collectTime 采集时间
     * @return 写入结果
     */
    ReturnResult<Void> writePoints(Long monitorId, List<MetricPoint> points, LocalDateTime collectTime);

    /**
     * 触发小时聚合
     *
     * @return 聚合结果
     */
    ReturnResult<Void> aggregateHourly();
}