package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceEventRecord;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceEventRecordMapper;
import com.chua.starter.device.support.service.DeviceAlarmService;
import com.chua.starter.device.support.service.DeviceEventService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.util.DeviceStatusUtil;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.TimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
     * 设备告警服务（报警类事件联动落告警）
     */
    private final DeviceAlarmService alarmService;

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
        // 报警类事件联动落告警：门禁日常进出（ACCESS）不进告警，避免刷屏
        EventType type = event.type();
        if (type != null && type != EventType.ACCESS) {
            try {
                alarmService.record(event.deviceId(), type.name(),
                        typeLabel(type),
                        StringUtils.hasText(event.detail()) ? event.detail()
                                : "事件轮询采集（" + type.name() + "）",
                        levelOf(type));
            } catch (Exception ex) {
                // 告警落库失败不影响事件记录本身
                log.warn("事件联动告警落库失败: deviceId={}, type={}, message={}",
                        event.deviceId(), type, ex.getMessage());
            }
        }
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
        sessionManager.getAllSessions().forEach((deviceId, session) -> {
            DeviceInfo device = deviceService.getDevice(deviceId);
            // 停用 / 报废设备即便会话还没回收，也不参与事件轮询
            if (device != null && DeviceStatusUtil.collectionStopped(device.getDeviceInfoStatus())) {
                return;
            }
            pollSession(deviceId, session);
        });
    }

    @Override
    /**
     * 只轮询该凭据名下设备的事件。
     *
     * @param credentialId 凭据 ID
     */
    public void pollDevicesByCredential(String credentialId) {
        for (DeviceInfo device : deviceService.listByCredentialId(credentialId)) {
            // 停用 / 报废设备不参与后台采集：即使会话还在，也不拉事件
            if (DeviceStatusUtil.collectionStopped(device.getDeviceInfoStatus())) {
                continue;
            }
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

    /**
     * 事件类型 → 告警标题（中文可读文案，与前端类型口径一致）。
     *
     * @param type 事件类型
     * @return 告警标题
     */
    private static String typeLabel(EventType type) {
        return switch (type) {
            case MOTION -> "移动侦测";
            case IO_ALARM -> "IO 报警";
            case TEMP -> "温度报警";
            case NETWORK -> "网络异常";
            case OTHER -> "其他事件";
            case ACCESS -> "门禁事件";
            // [P3C C1 修复] 补充 default 兜底：枚举扩展时不再静默漏处理
            default -> throw new IllegalStateException("未预期的取值: " + type);
        };
    }

    /**
     * 事件类型 → 告警级别：明确报警类按危险度分级，其余按信息处理。
     *
     * @param type 事件类型
     * @return 级别：critical / major / minor / info
     */
    private static String levelOf(EventType type) {
        return switch (type) {
            case TEMP -> "critical";
            case IO_ALARM -> "major";
            case MOTION, NETWORK -> "minor";
            case OTHER, ACCESS -> "info";
            // [P3C C1 修复] 补充 default 兜底：枚举扩展时不再静默漏处理
            default -> throw new IllegalStateException("未预期的取值: " + type);
        };
    }
}
