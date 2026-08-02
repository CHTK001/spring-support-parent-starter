package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceEventRecord;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.EventType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备事件服务接口
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface DeviceEventService {

    /**
     * 保存事件
     *
     * @param event 事件对象
     */
    void saveEvent(DeviceEvent event);

    /**
     * 查询事件
     *
     * @param deviceId 设备 ID
     * @param type     事件类型，null 表示全部
     * @param start    开始时间，null 表示不限
     * @param end      结束时间，null 表示不限
     * @return 事件列表
     */
    List<DeviceEventRecord> queryEvents(String deviceId, EventType type, LocalDateTime start, LocalDateTime end);

    /**
     * 轮询所有在线设备的事件
     * <p>由 Spring 定时任务驱动，每个厂商可独立配置轮询间隔。</p>
     */
    void pollAllDevices();
}