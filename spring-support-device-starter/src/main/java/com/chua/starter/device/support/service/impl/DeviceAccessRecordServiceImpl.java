package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceAccessRecord;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceAccessRecordMapper;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.service.DeviceAccessRecordService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.timeseries.DeviceSeriesRecorder;
import com.chua.starter.device.support.util.DeviceStatusUtil;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.TimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门禁出入记录服务实现。
 *
 * <p>抽取时对每台在线会话查询一次 {@code queryAccessRecords}，单台失败只记日志、
 * 不阻断其它设备；同一 (deviceId, occurredAt) 已存在时跳过，保证幂等。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAccessRecordServiceImpl implements DeviceAccessRecordService {

    /**
     * 单批内最多抽取的设备数，避免一次性连接受限
     */
    private static final int MAX_EXTRACT_PER_CYCLE = 200;

    /**
     * 门禁记录 映射器
     */
    private final DeviceAccessRecordMapper accessRecordMapper;

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 设备信息 映射器（判断会话对应设备是否已停用/报废）
     */
    private final DeviceInfoMapper deviceInfoMapper;

    /**
     * 时序写入器（门禁记录同时进时序存储，供趋势/跨后端查询）
     */
    private final DeviceSeriesRecorder seriesRecorder;

    @Override
    public int extractRecent(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        int saved = 0;
        List<String> deviceIds = new ArrayList<>(sessionManager.getAllSessions().keySet());
        // 停用 / 报废设备退出采集：先把它们从候选里剔除，避免占用每轮抽取名额
        Set<String> stopped = stoppedDeviceIds(deviceIds);
        if (!stopped.isEmpty()) {
            deviceIds.removeAll(stopped);
        }
        int window = Math.min(deviceIds.size(), MAX_EXTRACT_PER_CYCLE);
        for (int i = 0; i < window; i++) {
            String deviceId = deviceIds.get(i);
            DeviceSession session = sessionManager.getSession(deviceId);
            if (session == null || !session.isOnline()) {
                continue;
            }
            try {
                List<AccessRecord> records = session.queryAccessRecords(new TimeRange(start, end));
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (AccessRecord record : records) {
                    if (record == null || record.time() == null) {
                        continue;
                    }
                    if (exists(deviceId, record.time())) {
                        continue;
                    }
                    accessRecordMapper.insert(toEntity(deviceId, record));
                    seriesRecorder.recordAccess(deviceId, record.doorId(), record.cardNo(), record.userName(),
                            record.eventType(), record.direction(), record.photoUrl(), record.time());
                    saved++;
                }
            } catch (Exception e) {
                log.warn("[Device][Access] 抽取门禁记录失败: deviceId={}, message={}", deviceId, e.getMessage());
            }
        }
        log.info("[Device][Access] 门禁记录抽取完成: 窗口 {} 台设备, 落库 {} 条", window, saved);
        return saved;
    }

    /**
     * 从设备台账里挑出已停用 / 报废的设备 ID
     *
     * @param deviceIds 会话里存在的设备 ID
     * @return 需要跳过采集的设备 ID 集合（无则空集）
     */
    private Set<String> stoppedDeviceIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Set.of();
        }
        return deviceInfoMapper.selectBatchIds(deviceIds).stream()
                .filter(device -> DeviceStatusUtil.collectionStopped(device.getDeviceInfoStatus()))
                .map(DeviceInfo::getDeviceInfoId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<DeviceAccessRecord> queryRecords(String deviceId, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<DeviceAccessRecord> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null && !deviceId.isBlank()) {
            wrapper.eq(DeviceAccessRecord::getDeviceAccessRecordDeviceId, deviceId);
        }
        if (start != null) {
            wrapper.ge(DeviceAccessRecord::getDeviceAccessRecordOccurredAt, start);
        }
        if (end != null) {
            wrapper.le(DeviceAccessRecord::getDeviceAccessRecordOccurredAt, end);
        }
        wrapper.orderByDesc(DeviceAccessRecord::getDeviceAccessRecordOccurredAt);
        return accessRecordMapper.selectList(wrapper);
    }

    /**
     * 同一 (deviceId, occurredAt) 是否已存在（幂等键）
     *
     * @param deviceId 设备 ID
     * @param time     刷卡时间
     * @return 是否已存在
     */
    private boolean exists(String deviceId, LocalDateTime time) {
        LambdaQueryWrapper<DeviceAccessRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAccessRecord::getDeviceAccessRecordDeviceId, deviceId)
                .eq(DeviceAccessRecord::getDeviceAccessRecordOccurredAt, time);
        return accessRecordMapper.selectCount(wrapper) > 0;
    }

    /**
     * 厂商传输模型 → 本地实体
     *
     * @param deviceId 设备 ID
     * @param record   厂商记录
     * @return 本地实体
     */
    private DeviceAccessRecord toEntity(String deviceId, AccessRecord record) {
        DeviceAccessRecord entity = new DeviceAccessRecord();
        entity.setDeviceAccessRecordDeviceId(deviceId);
        entity.setDeviceAccessRecordDoorId(record.doorId());
        entity.setDeviceAccessRecordCardNo(record.cardNo());
        entity.setDeviceAccessRecordUserName(record.userName());
        entity.setDeviceAccessRecordEventType(record.eventType());
        entity.setDeviceAccessRecordDirection(record.direction());
        entity.setDeviceAccessRecordPhotoUrl(record.photoUrl());
        entity.setDeviceAccessRecordOccurredAt(record.time());
        return entity;
    }
}