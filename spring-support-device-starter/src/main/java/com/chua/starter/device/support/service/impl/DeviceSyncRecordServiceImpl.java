package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceSyncOutbox;
import com.chua.starter.device.support.entity.DeviceSyncRecord;
import com.chua.starter.device.support.mapper.DeviceSyncOutboxMapper;
import com.chua.starter.device.support.mapper.DeviceSyncRecordMapper;
import com.chua.starter.device.support.model.SyncOutboxQuery;
import com.chua.starter.device.support.model.SyncRecordQuery;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSyncRecordService;
import com.chua.starter.device.support.service.DeviceSyncService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步记录查询服务实现。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSyncRecordServiceImpl implements DeviceSyncRecordService {

    /** 时间格式（兼容 yyyy-MM-dd HH:mm:ss） */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 重试退避基数（秒）：第 n 次失败后推迟 60 * 2^(n-1) 秒 */
    private static final long RETRY_BACKOFF_BASE_SECONDS = 60L;

    /** 单条最大重试推迟（秒） */
    private static final long RETRY_BACKOFF_MAX_SECONDS = 3600L;

    /** 同步记录映射器 */
    private final DeviceSyncRecordMapper recordMapper;

    /** 推送队列映射器 */
    private final DeviceSyncOutboxMapper outboxMapper;

    /** 设备服务（重试时取台账行） */
    private final DeviceService deviceService;

    /** 同步服务（重试时复用写回业务） */
    private final DeviceSyncService syncService;

    @Override
    public PageResult<DeviceSyncRecord> pageRecords(SyncRecordQuery query) {
        SyncRecordQuery q = query == null ? new SyncRecordQuery() : query;
        Page<DeviceSyncRecord> page = new Page<>(Math.max(q.getPageNo(), 1), Math.max(q.getPageSize(), 1));
        LambdaQueryWrapper<DeviceSyncRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(q.getVendor()), DeviceSyncRecord::getDeviceSyncRecordVendor, q.getVendor())
                .eq(StringUtils.hasText(q.getMode()), DeviceSyncRecord::getDeviceSyncRecordMode, q.getMode())
                .eq(StringUtils.hasText(q.getPhase()), DeviceSyncRecord::getDeviceSyncRecordPhase, q.getPhase())
                .eq(StringUtils.hasText(q.getStatus()), DeviceSyncRecord::getDeviceSyncRecordStatus, q.getStatus())
                .ge(StringUtils.hasText(q.getStart()), DeviceSyncRecord::getDeviceSyncRecordStartedAt, parseTime(q.getStart()))
                .le(StringUtils.hasText(q.getEnd()), DeviceSyncRecord::getDeviceSyncRecordStartedAt, parseTime(q.getEnd()))
                .orderByDesc(DeviceSyncRecord::getDeviceSyncRecordStartedAt)
                .orderByDesc(DeviceSyncRecord::getDeviceSyncRecordId);
        return PageResult.copy(recordMapper.selectPage(page, wrapper));
    }

    @Override
    public PageResult<DeviceSyncOutbox> pageOutbox(SyncOutboxQuery query) {
        SyncOutboxQuery q = query == null ? new SyncOutboxQuery() : query;
        Page<DeviceSyncOutbox> page = new Page<>(Math.max(q.getPageNo(), 1), Math.max(q.getPageSize(), 1));
        LambdaQueryWrapper<DeviceSyncOutbox> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(q.getVendor()), DeviceSyncOutbox::getDeviceSyncOutboxVendor, q.getVendor())
                .eq(StringUtils.hasText(q.getAction()), DeviceSyncOutbox::getDeviceSyncOutboxAction, q.getAction())
                .eq(StringUtils.hasText(q.getStatus()), DeviceSyncOutbox::getDeviceSyncOutboxStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getDeviceId()), DeviceSyncOutbox::getDeviceSyncOutboxDeviceId, q.getDeviceId())
                .orderByDesc(DeviceSyncOutbox::getDeviceSyncOutboxCreatedAt)
                .orderByDesc(DeviceSyncOutbox::getDeviceSyncOutboxId);
        return PageResult.copy(outboxMapper.selectPage(page, wrapper));
    }

    @Override
    public Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("records", countBy(recordMapper, DeviceSyncRecord::getDeviceSyncRecordStatus,
                new String[]{"running", "success", "partial", "failed"}));
        summary.put("outbox", countBy(outboxMapper, DeviceSyncOutbox::getDeviceSyncOutboxStatus,
                new String[]{"pending", "sent", "failed", "dead"}));
        return summary;
    }

    /**
     * 按状态列做一组 count，避免为四个数字各发一条 SQL。
     *
     * @param mapper   映射器（用 BaseMapper 的 selectCount）
     * @param column   状态列
     * @param statuses 状态清单
     * @param <T>      实体类型
     * @return {status: count}，另带 total
     */
    private <T> Map<String, Object> countBy(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column,
                                            String[] statuses) {
        Map<String, Object> counts = new LinkedHashMap<>();
        long total = 0;
        for (String status : statuses) {
            long count = mapper.selectCount(new LambdaQueryWrapper<T>().eq(column, status));
            counts.put(status, count);
            total += count;
        }
        counts.put("total", total);
        return counts;
    }

    @Override
    public DeviceSyncOutbox retryOutbox(String outboxId) {
        DeviceSyncOutbox outbox = outboxMapper.selectById(outboxId);
        if (outbox == null) {
            throw new IllegalArgumentException("推送队列记录不存在: " + outboxId);
        }
        if ("sent".equals(outbox.getDeviceSyncOutboxStatus())) {
            throw new IllegalStateException("该记录已推送成功，无需重试");
        }
        DeviceInfo device = deviceService.getDevice(outbox.getDeviceSyncOutboxDeviceId());
        if (device == null) {
            // 台账行已删除：写回失去主体，标记 dead 避免无限重试
            outbox.setDeviceSyncOutboxStatus("dead");
            outbox.setDeviceSyncOutboxMessage("本地设备已删除，无法写回");
            outboxMapper.updateById(outbox);
            return outbox;
        }
        boolean ok = false;
        String message = null;
        try {
            ok = syncService.pushDevice(device);
        } catch (Exception e) {
            message = e.getMessage();
            log.warn("[Device][Outbox] 重试推送失败: outboxId={}, device={}", outboxId, device.getDeviceInfoId(), e);
        }
        int retry = outbox.getDeviceSyncOutboxRetry() == null ? 0 : outbox.getDeviceSyncOutboxRetry();
        if (ok) {
            outbox.setDeviceSyncOutboxStatus("sent");
            outbox.setDeviceSyncOutboxMessage(null);
        } else {
            long backoff = Math.min(RETRY_BACKOFF_BASE_SECONDS * (1L << Math.min(retry, 6)), RETRY_BACKOFF_MAX_SECONDS);
            outbox.setDeviceSyncOutboxStatus("pending");
            outbox.setDeviceSyncOutboxRetry(retry + 1);
            outbox.setDeviceSyncOutboxNextAt(LocalDateTime.now().plusSeconds(backoff));
            outbox.setDeviceSyncOutboxMessage(StringUtils.hasText(message) ? message : "厂商写回未成功，已按退避推迟下次重试");
        }
        outboxMapper.updateById(outbox);
        return outbox;
    }

    /**
     * 解析时间参数：兼容 yyyy-MM-dd HH:mm:ss 与 ISO 两种格式。
     *
     * @param text 时间文本
     * @return 时间；空串返回 {@code null}
     */
    private static LocalDateTime parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        if (value.length() == 10) {
            value = value + " 00:00:00";
        }
        try {
            return LocalDateTime.parse(value, DTF);
        } catch (Exception ignored) {
            return LocalDateTime.parse(value);
        }
    }
}
