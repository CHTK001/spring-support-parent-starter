package com.chua.starter.monitor.support.mapper;
import com.chua.starter.monitor.support.entity.MsMonitorMetric;
import com.chua.starter.monitor.support.model.MetricAggregate;
import com.chua.starter.mybatis.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用指标 映射器
 *
 * @since 2026/8/4
 * @author CH
 */
public interface MsMonitorMetricMapper extends BaseMapperX<MsMonitorMetric> {

    List<MetricAggregate> selectHourlyAggregates(
            @Param("hourStart") LocalDateTime hourStart,
            @Param("hourEnd") LocalDateTime hourEnd);
}
