package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceEventRecord;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceEventRecordMapper;
import com.chua.starter.device.support.service.DeviceEventService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.TimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 设备事件服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceEventServiceImpl implements DeviceEventService {

    /**
     * 事件记录 映射器
     */
    private final DeviceEventRecordMapper eventRecordMapper;

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 设备服务（按凭据列设备）
     */
    private final DeviceService deviceService;

    @Override
    /**
     * 保存事件
     * @param event 参数
     * @return 返回值
    */
    public void saveEvent(DeviceEvent event) {
        DeviceEventRecord record = new DeviceEventRecord();
        record.setDeviceEventRecordId(UUID.randomUUID().toString());
        record.setDeviceEventRecordDeviceId(event.deviceId());
        record.setDeviceEventRecordChannelNo(event.channel());
        record.setDeviceEventRecordEventType(event.type().name());
        record.setDeviceEventRecordDetail(event.detail());
        record.setDeviceEventRecordOccurredAt(event.occurredAt());
        eventRecordMapper.insert(record);
    }

    @Override
    /**
     * 查询事件
     * @param deviceId 字符串
     * @param end 参数
     * @param start 启动
     * @param type 参数
     * @return 返回值
    */
    public List<DeviceEventRecord> queryEvents(String deviceId, EventType type, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<DeviceEventRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceEventRecord::getDeviceEventRecordDeviceId, deviceId);
        if (type != null) {
            wrapper.eq(DeviceEventRecord::getDeviceEventRecordEventType, type.name());
        }
        if (start != null) {
            wrapper.ge(DeviceEventRecord::getDeviceEventRecordOccurredAt, start);
        }
        if (end != null) {
            wrapper.le(DeviceEventRecord::getDeviceEventRecordOccurredAt, end);
        }
        wrapper.orderByDesc(DeviceEventRecord::getDeviceEventRecordOccurredAt);
        return eventRecordMapper.selectList(wrapper);
    }

    @Override
    /**
     * 取出全部devices
     * <p>手工全量入口；定时驱动已改为按凭据的 {@link #pollDevicesByCredential(String)}。</p>
     */
    public void pollAllDevices() {
        sessionManager.getAllSessions().forEach((deviceId, session) -> pollSession(deviceId, session));
    }

    @Override
    /**
     * 只轮询该凭据名下设备的事件。
     *
     * @param credentialId 凭据 ID
     */
    public void pollDevicesByCredential(String credentialId) {
        for (DeviceInfo device : deviceService.listByCredentialId(credentialId)) {
            DeviceSession session = sessionManager.getSession(device.getDeviceInfoId());
            if (session == null) {
                continue;
            }
            pollSession(device.getDeviceInfoId(), session);
        }
    }

    /**
     * 轮询一个设备会话并落库事件。
     *
     * @param deviceId 设备 ID（日志定位用）
     * @param session  设备会话
     */
    private void pollSession(String deviceId, DeviceSession session) {
        if (!session.isOnline()) {
            return;
        }
        try {
            List<DeviceEvent> events = session.pollEvents(new TimeRange(
                    LocalDateTime.now().minusMinutes(5),
                    LocalDateTime.now()));
            events.forEach(this::saveEvent);
        } catch (Exception e) {
            log.error("轮询设备事件失败: deviceId={}", deviceId, e);
        }
    }
}
