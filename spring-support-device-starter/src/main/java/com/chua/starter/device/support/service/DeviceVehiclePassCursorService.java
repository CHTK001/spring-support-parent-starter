package com.chua.starter.device.support.service;

/**
 * 过车拉取游标服务。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceVehiclePassCursorService {

    /**
     * 读取设备最后处理的消息 ID。
     *
     * @param deviceId 设备标识
     * @return 游标；无记录时为 0
     */
    long getLastMsgId(String deviceId);

    /**
     * 保存设备最后处理的消息 ID（只前进不后退）。
     *
     * @param deviceId 设备标识
     * @param msgId    消息 ID
     */
    void saveLastMsgId(String deviceId, long msgId);
}
