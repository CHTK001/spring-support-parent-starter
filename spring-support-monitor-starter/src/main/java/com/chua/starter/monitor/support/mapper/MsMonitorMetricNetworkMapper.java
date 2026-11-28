package com.chua.starter.monitor.support.mapper;
import com.chua.starter.monitor.support.entity.MsMonitorMetricNetwork;
import com.chua.starter.mybatis.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 网络指标 映射器
 *
 * @since 2026/8/4
 * @author CH
 */
public interface MsMonitorMetricNetworkMapper extends BaseMapperX<MsMonitorMetricNetwork> {

    List<MsMonitorMetricNetwork> selectPreviousByInterfaces(
            @Param("monitorId") Long monitorId,
            @Param("interfaces") List<String> interfaces,
            @Param("collectTime") LocalDateTime collectTime);
}
