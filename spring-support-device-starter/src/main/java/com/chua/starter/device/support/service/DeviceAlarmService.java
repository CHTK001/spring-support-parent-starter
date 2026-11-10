package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceAlarm;
import com.chua.starter.device.support.model.AlarmQuery;
import com.chua.starter.mybatis.entity.PageResult;

import java.util.Map;

/**
 * 设备告警查询与处理服务。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceAlarmService {

    /**
     * 分页查询告警。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DeviceAlarm> page(AlarmQuery query);

    /**
     * 更新处理状态。
     *
     * @param id      告警 ID
     * @param status  目标状态：unhandled / handling / resolved / ignored
     * @param handler 处理人，可空
     * @return 更新后的告警
     */
    DeviceAlarm updateStatus(String id, String status, String handler);

    /**
     * 概览指标：总数 / 未处理 / 严重 / 今日新增。
     *
     * @return 指标键值对
     */
    Map<String, Object> stats();
}
