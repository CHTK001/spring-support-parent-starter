package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DeviceAlarm;
import com.chua.starter.device.support.mapper.DeviceAlarmMapper;
import com.chua.starter.device.support.model.AlarmQuery;
import com.chua.starter.device.support.service.DeviceAlarmService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 设备告警查询与处理服务实现。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class DeviceAlarmServiceImpl implements DeviceAlarmService {

    /** 允许的处理状态 */
    private static final Set<String> VALID_STATUS = Set.of("unhandled", "handling", "resolved", "ignored");

    /** 时间格式 */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 告警映射器 */
    private final DeviceAlarmMapper alarmMapper;

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
        entity.setDeviceAlarmHandler(handler);
        entity.setDeviceAlarmHandledAt(LocalDateTime.now());
        alarmMapper.updateById(entity);
        return entity;
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
                .ge(DeviceAlarm::getDeviceAlarmCreatedAt, today)));
        return stats;
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
        wrapper.orderByDesc(DeviceAlarm::getDeviceAlarmOccurredAt);
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
