package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MonitorDeviceMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备指标数据Mapper
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorDeviceMetricsMapper extends BaseMapper<MonitorDeviceMetrics> {

    /**
     * 根据设备ID查询最新指标数据
     * @param deviceId 设备ID
     * @return 最新指标数据
     */
    MonitorDeviceMetrics selectLatestByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 根据设备ID和时间范围查询指标数据
     * @param deviceId 设备ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 指标数据列表
     */
    List<MonitorDeviceMetrics> selectByDeviceIdAndTimeRange(
        @Param("deviceId") String deviceId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 删除指定时间之前的数据
     * @param beforeTime 时间点
     * @return 删除的记录数
     */
    int deleteBeforeTime(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 根据IP地址和端口查询最新指标数据
     * @param ipAddress IP地址
     * @param port 端口
     * @return 最新指标数据
     */
    MonitorDeviceMetrics selectLatestByIpAndPort(
        @Param("ipAddress") String ipAddress,
        @Param("port") Integer port
    );
}
