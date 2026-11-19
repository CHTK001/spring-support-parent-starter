package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DeviceAlarm;
import com.chua.starter.device.support.mapper.DeviceAlarmMapper;
import com.chua.starter.device.support.model.AlarmQuery;
import com.chua.starter.device.support.realtime.DeviceAlarmEvent;
import com.chua.starter.device.support.realtime.DeviceRealtimePublisher;
import com.chua.starter.device.support.service.DeviceAlarmService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备告警查询与处理服务实现。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceAlarmServiceImpl implements DeviceAlarmService {

    /** 允许的处理状态 */
    private static final Set<String> VALID_STATUS = Set.of("unhandled", "handling", "resolved", "ignored");

    /** 时间格式 */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 告警映射器 */
    private final DeviceAlarmMapper alarmMapper;

    /** 实时事件发布器（平台自检新告警推前端） */
    private final DeviceRealtimePublisher realtimePublisher;

    @Override
    public PageResult<DeviceAlarm> page(AlarmQuery query) {
        AlarmQuery q = query == null ? new AlarmQuery() : query;
        int pageNo = q.getPageNo() == null || q.getPageNo() < 1 ? 1 : q.getPageNo();
        // [P3C A20 豁免]
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 20 : Math.min(q.getPageSize(), 200);
        Page<DeviceAlarm> page = new Page<>(pageNo, pageSize);
        alarmMapper.selectPage(page, buildFilter(q));
        return PageResult.copy(page);
    }

    @Override
    public DeviceAlarm updateStatus(String id, String status, String handler) {
        if (!VALID_STATUS.contains(status)) {
            throw new IllegalArgumentException("不支持的处理状态: " + status);
        }
        DeviceAlarm entity = alarmMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("告警不存在: " + id);
        }
        entity.setDeviceAlarmStatus(status);
        // handler 空串（前端不传）落 null，避免查「处理人」时出现空壳字符串
        entity.setDeviceAlarmHandler(StringUtils.hasText(handler) ? handler : null);
        entity.setDeviceAlarmHandledAt(LocalDateTime.now());
        alarmMapper.updateById(entity);
        return entity;
    }

    @Override
    public int updateStatusBatch(List<String> ids, String status, String handler) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("未选择要处理的告警");
        }
        if (!VALID_STATUS.contains(status)) {
            throw new IllegalArgumentException("不支持的处理状态: " + status);
        }
        LambdaUpdateWrapper<DeviceAlarm> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(DeviceAlarm::getDeviceAlarmId, ids)
                .set(DeviceAlarm::getDeviceAlarmStatus, status)
                // handler 空串（前端不传）落 null，避免查「处理人」时出现空壳字符串
                .set(DeviceAlarm::getDeviceAlarmHandler, StringUtils.hasText(handler) ? handler : null)
                .set(DeviceAlarm::getDeviceAlarmHandledAt, LocalDateTime.now());
        return alarmMapper.update(null, wrapper);
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", alarmMapper.selectCount(null));
        stats.put("unhandled", alarmMapper.selectCount(new LambdaQueryWrapper<DeviceAlarm>()
                .eq(DeviceAlarm::getDeviceAlarmStatus, "unhandled")));
        stats.put("critical", alarmMapper.selectCount(new LambdaQueryWrapper<DeviceAlarm>()
                .in(DeviceAlarm::getDeviceAlarmLevel, Arrays.asList("critical", "major"))));
        LocalDateTime today = LocalDate.now().atStartOfDay();
        stats.put("today", alarmMapper.selectCount(new LambdaQueryWrapper<DeviceAlarm>()
                .ge(DeviceAlarm::getCreateTime, today)));
        return stats;
    }

    /**
     * 平台自检告警防抖窗口（秒）：同设备同类型已有未处理告警时不重复插入
     */
    private static final long DEDUPE_WINDOW_SECONDS = 600L;

    /** 自动消警处理人标识（平台自检恢复，非人工操作） */
    private static final String HANDLER_SYSTEM = "system";

    @Override
    public void record(String deviceId, String type, String title, String detail, String level) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        String alarmType = StringUtils.hasText(type) ? type.trim() : "OTHER";
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<DeviceAlarm> open = new LambdaQueryWrapper<>();
        open.eq(DeviceAlarm::getDeviceAlarmDeviceId, deviceId)
                .eq(DeviceAlarm::getDeviceAlarmType, alarmType)
                .in(DeviceAlarm::getDeviceAlarmStatus, Arrays.asList("unhandled", "handling"))
                .ge(DeviceAlarm::getCreateTime, now.minusSeconds(DEDUPE_WINDOW_SECONDS));
        if (alarmMapper.selectCount(open) > 0) {
            return;
        }
        DeviceAlarm entity = new DeviceAlarm();
        entity.setDeviceAlarmSource("platform");
        entity.setDeviceAlarmExternalId("platform:" + deviceId + ":" + alarmType + ":" + System.currentTimeMillis());
        entity.setDeviceAlarmDeviceId(deviceId);
        entity.setDeviceAlarmType(alarmType);
        entity.setDeviceAlarmLevel(StringUtils.hasText(level) ? level.trim() : "info");
        entity.setDeviceAlarmTitle(title);
        entity.setDeviceAlarmDetail(detail);
        entity.setDeviceAlarmStatus("unhandled");
        entity.setDeviceAlarmOccurredAt(now);
        entity.setCreateTime(now);
        alarmMapper.insert(entity);
        // 实时通道：平台自检新告警推给前端（红点 / 列表首行），失败不影响入库
        realtimePublisher.publish("alarm", new DeviceAlarmEvent(
                entity.getDeviceAlarmId(),
                entity.getDeviceAlarmDeviceId(),
                entity.getDeviceAlarmType(),
                entity.getDeviceAlarmLevel(),
                entity.getDeviceAlarmTitle(),
                entity.getDeviceAlarmStatus(),
                entity.getDeviceAlarmOccurredAt()));
        log.info("平台自检告警入库: deviceId={}, type={}, level={}", deviceId, alarmType, entity.getDeviceAlarmLevel());
    }

    @Override
    public int resolveOpen(String deviceId, String type) {
        if (!StringUtils.hasText(deviceId)) {
            return 0;
        }
        LambdaQueryWrapper<DeviceAlarm> open = new LambdaQueryWrapper<>();
        open.eq(DeviceAlarm::getDeviceAlarmDeviceId, deviceId);
        if (StringUtils.hasText(type)) {
            open.eq(DeviceAlarm::getDeviceAlarmType, type.trim());
        }
        open.in(DeviceAlarm::getDeviceAlarmStatus, Arrays.asList("unhandled", "handling"));
        List<DeviceAlarm> openAlarms = alarmMapper.selectList(open);
        if (openAlarms.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DeviceAlarm alarm : openAlarms) {
            alarm.setDeviceAlarmStatus("resolved");
            alarm.setDeviceAlarmHandler(HANDLER_SYSTEM);
            alarm.setDeviceAlarmHandledAt(now);
            alarmMapper.updateById(alarm);
        }
        return openAlarms.size();
    }

    /**
     * 按条件拼装查询器。
     *
     * @param q 查询条件
     * @return 查询器
     */
    private static LambdaQueryWrapper<DeviceAlarm> buildFilter(AlarmQuery q) {
        LambdaQueryWrapper<DeviceAlarm> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getLevel())) {
            wrapper.eq(DeviceAlarm::getDeviceAlarmLevel, q.getLevel().trim());
        }
        if (StringUtils.hasText(q.getStatus())) {
            wrapper.eq(DeviceAlarm::getDeviceAlarmStatus, q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getDeviceId())) {
            wrapper.eq(DeviceAlarm::getDeviceAlarmDeviceId, q.getDeviceId().trim());
        }
        if (StringUtils.hasText(q.getType())) {
            wrapper.eq(DeviceAlarm::getDeviceAlarmType, q.getType().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            wrapper.and(w -> w.like(DeviceAlarm::getDeviceAlarmTitle, kw)
                    .or().like(DeviceAlarm::getDeviceAlarmDetail, kw)
                    .or().like(DeviceAlarm::getDeviceAlarmSource, kw));
        }
        LocalDateTime start = parseTime(q.getStart());
        LocalDateTime end = parseTime(q.getEnd());
        if (start != null) {
            wrapper.ge(DeviceAlarm::getDeviceAlarmOccurredAt, start);
        }
        if (end != null) {
            wrapper.le(DeviceAlarm::getDeviceAlarmOccurredAt, end);
        }
        // 未处理 / 处理中置顶，其余按发生时间倒序。
        // 告警表只有一张（device_alarm），「已解决」只是状态位的更新，
        // 所以这里用状态表达式排序，不额外建「待处理」视图或第二张表。
        wrapper.last("ORDER BY (device_alarm_status IN ('unhandled','handling')) DESC,"
                + " device_alarm_occurred_at DESC");
        return wrapper;
    }

    /**
     * 解析时间；空值返回 null（不加时间条件）。
     *
     * @param text 时间文本
     * @return 时间，可空
     */
    private static LocalDateTime parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(value, DTF);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
