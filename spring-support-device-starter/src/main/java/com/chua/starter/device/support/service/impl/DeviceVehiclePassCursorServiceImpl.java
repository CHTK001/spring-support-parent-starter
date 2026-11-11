package com.chua.starter.device.support.service.impl;

import com.chua.starter.device.support.entity.DeviceVehiclePassCursor;
import com.chua.starter.device.support.mapper.DeviceVehiclePassCursorMapper;
import com.chua.starter.device.support.service.DeviceVehiclePassCursorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 过车拉取游标服务实现。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class DeviceVehiclePassCursorServiceImpl implements DeviceVehiclePassCursorService {

    /**
     * 游标映射器
     */
    private final DeviceVehiclePassCursorMapper cursorMapper;

    @Override
    public long getLastMsgId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return 0L;
        }
        DeviceVehiclePassCursor cursor = cursorMapper.selectById(deviceId);
        return cursor == null || cursor.getDeviceVehiclePassCursorLastMsgId() == null
                ? 0L
                : cursor.getDeviceVehiclePassCursorLastMsgId();
    }

    @Override
    public void saveLastMsgId(String deviceId, long msgId) {
        if (deviceId == null || deviceId.isBlank() || msgId <= 0) {
            return;
        }
        DeviceVehiclePassCursor cursor = cursorMapper.selectById(deviceId);
        if (cursor == null) {
            cursor = new DeviceVehiclePassCursor();
            cursor.setDeviceVehiclePassCursorDeviceId(deviceId);
            cursor.setDeviceVehiclePassCursorLastMsgId(msgId);
            cursor.setDeviceVehiclePassCursorUpdatedAt(LocalDateTime.now());
            cursorMapper.insert(cursor);
            return;
        }
        long current = cursor.getDeviceVehiclePassCursorLastMsgId() == null
                ? 0L
                : cursor.getDeviceVehiclePassCursorLastMsgId();
        if (msgId <= current) {
            return;
        }
        cursor.setDeviceVehiclePassCursorLastMsgId(msgId);
        cursor.setDeviceVehiclePassCursorUpdatedAt(LocalDateTime.now());
        cursorMapper.updateById(cursor);
    }
}
