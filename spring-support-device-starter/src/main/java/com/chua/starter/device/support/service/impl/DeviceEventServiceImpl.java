package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceEventRecord;
import com.chua.starter.device.support.mapper.DeviceEventRecordMapper;
import com.chua.starter.device.support.service.DeviceEventService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.TimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
     * 事件记录 Mapper
     */
    private final DeviceEventRecordMapper eventRecordMapper;

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    @Override
    public void saveEvent(DeviceEvent event) {
        DeviceEventRecord record = new DeviceEventRecord();
        record.setDeviceEventRecordId(UUID.randomUUID().toString());
        record.setDeviceEventRecordDeviceId(event.getDeviceId());
        record.setDeviceEventRecordChannelNo(event.getChannel());
        record.setDeviceEventRecordEventType(event.getType().name());
        record.setDeviceEventRecordDetail(event.getDetail());
        record.setDeviceEventRecordOccurredAt(event.getOccurredAt());
        eventRecordMapper.insert(record);
    }

    @Override
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
    @Scheduled(fixedDelayString = "${plugin.device.event.poll-interval:30000}")
    public void pollAllDevices() {
        sessionManager.getAllSessions().forEach((deviceId, session) -> {
            if (session.isOnline()) {
                try {
                    List<DeviceEvent> events = session.pollEvents(new TimeRange(
                            LocalDateTime.now().minusMinutes(5),
                            LocalDateTime.now()));
                    events.forEach(this::saveEvent);
                } catch (Exception e) {
                    log.error("轮询设备事件失败: deviceId={}", deviceId, e);
                }
            }
        });
    }
}