package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceEventRecord;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.EventType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备事件服务接口
 *
 * @since 4.0.0.42
 * @author CH
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
     * @param deviceId 设备 标识
     * @param type     事件类型，空 表示全部
     * @param start    开始时间，空 表示不限
     * @param end      结束时间，空 表示不限
     * @return 事件列表
     */
    List<DeviceEventRecord> queryEvents(String deviceId, EventType type, LocalDateTime start, LocalDateTime end);

    /**
     * 轮询所有在线设备的事件（保留给手工全量入口）
     * <p>定时触发已下沉到凭据级任务 {@code eventPoll_{credentialId}}。</p>
     */
    void pollAllDevices();

    /**
     * 只轮询某套凭据名下设备的事件。
     *
     * <p>事件轮询是每套厂商凭据实例独立的能力：按凭据查出设备，只处理这些设备的
     * 在线会话，不触碰其他厂商/凭据的设备。</p>
     *
     * @param credentialId 凭据 ID
     */
    void pollDevicesByCredential(String credentialId);
}
