package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DeviceAccessRecord;
import com.chua.starter.device.support.entity.DeviceAttendanceRecord;
import com.chua.starter.device.support.mapper.DeviceAccessRecordMapper;
import com.chua.starter.device.support.mapper.DeviceAttendanceRecordMapper;
import com.chua.starter.device.support.service.DeviceAttendanceService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤记录服务实现。
 *
 * <p>归集规则：每条门禁记录生成一条考勤记录，来源键固定为
 * {@code access:<门禁记录主键>}，重复执行不会重复插入。
 * 门禁记录没有人员姓名和卡号时跳过，避免产生无法归属的考勤。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAttendanceServiceImpl implements DeviceAttendanceService {

    /**
     * 考勤记录映射器
     */
    private final DeviceAttendanceRecordMapper attendanceMapper;

    /**
     * 门禁记录映射器
     */
    private final DeviceAccessRecordMapper accessRecordMapper;

    @Override
    public int syncFromAccess(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        LambdaQueryWrapper<DeviceAccessRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(DeviceAccessRecord::getDeviceAccessRecordOccurredAt, start)
                .le(DeviceAccessRecord::getDeviceAccessRecordOccurredAt, end)
                .orderByAsc(DeviceAccessRecord::getDeviceAccessRecordOccurredAt);
        List<DeviceAccessRecord> records = accessRecordMapper.selectList(wrapper);
        int saved = 0;
        for (DeviceAccessRecord record : records) {
            if (!StringUtils.hasText(record.getDeviceAccessRecordUserName())
                    && !StringUtils.hasText(record.getDeviceAccessRecordCardNo())) {
                continue;
            }
            String sourceId = "access:" + record.getDeviceAccessRecordId();
            if (exists(sourceId)) {
                continue;
            }
            DeviceAttendanceRecord entity = new DeviceAttendanceRecord();
            entity.setDeviceAttendanceRecordDeviceId(record.getDeviceAccessRecordDeviceId());
            entity.setDeviceAttendanceRecordPersonName(record.getDeviceAccessRecordUserName());
            entity.setDeviceAttendanceRecordCardNo(record.getDeviceAccessRecordCardNo());
            entity.setDeviceAttendanceRecordDirection(record.getDeviceAccessRecordDirection());
            entity.setDeviceAttendanceRecordOccurredAt(record.getDeviceAccessRecordOccurredAt());
            entity.setDeviceAttendanceRecordSource("access-sync");
            entity.setDeviceAttendanceRecordSourceId(sourceId);
            entity.setDeviceAttendanceRecordCreatedAt(LocalDateTime.now());
            attendanceMapper.insert(entity);
            saved++;
        }
        log.info("[Device][Attendance] 门禁记录归集完成: 窗口 {} ~ {}, 门禁 {} 条, 新增考勤 {} 条",
                start, end, records.size(), saved);
        return saved;
    }

    @Override
    public List<DeviceAttendanceRecord> listRecords(String deviceId, String keyword,
                                                    LocalDateTime start, LocalDateTime end) {
        return attendanceMapper.selectList(buildQuery(deviceId, keyword, start, end));
    }

    @Override
    public PageResult<DeviceAttendanceRecord> pageRecords(int page, int size, String deviceId, String keyword,
                                                          LocalDateTime start, LocalDateTime end) {
        Page<DeviceAttendanceRecord> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        return PageResult.copy(attendanceMapper.selectPage(query,
                buildQuery(deviceId, keyword, start, end)));
    }

    /**
     * 来源键是否已归集。
     *
     * @param sourceId 来源键
     * @return 已存在返回 true
     */
    private boolean exists(String sourceId) {
        LambdaQueryWrapper<DeviceAttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAttendanceRecord::getDeviceAttendanceRecordSourceId, sourceId);
        return attendanceMapper.selectCount(wrapper) > 0;
    }

    /**
     * 组装查询条件。
     *
     * @param deviceId 设备 ID
     * @param keyword  关键字
     * @param start    开始时间
     * @param end      结束时间
     * @return 查询条件
     */
    private LambdaQueryWrapper<DeviceAttendanceRecord> buildQuery(String deviceId, String keyword,
                                                                  LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<DeviceAttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(deviceId)) {
            wrapper.eq(DeviceAttendanceRecord::getDeviceAttendanceRecordDeviceId, deviceId.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(DeviceAttendanceRecord::getDeviceAttendanceRecordPersonName, value)
                    .or().like(DeviceAttendanceRecord::getDeviceAttendanceRecordPersonNo, value)
                    .or().like(DeviceAttendanceRecord::getDeviceAttendanceRecordCardNo, value));
        }
        if (start != null) {
            wrapper.ge(DeviceAttendanceRecord::getDeviceAttendanceRecordOccurredAt, start);
        }
        if (end != null) {
            wrapper.le(DeviceAttendanceRecord::getDeviceAttendanceRecordOccurredAt, end);
        }
        wrapper.orderByDesc(DeviceAttendanceRecord::getDeviceAttendanceRecordOccurredAt);
        return wrapper;
    }
}
