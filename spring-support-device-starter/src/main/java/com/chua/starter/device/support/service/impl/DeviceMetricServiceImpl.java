package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceMetricLatest;
import com.chua.starter.device.support.mapper.DeviceMetricLatestMapper;
import com.chua.starter.device.support.service.DeviceMetricService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.timeseries.DeviceSeriesRecorder;
import com.chua.starter.device.support.util.DeviceStatusUtil;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceMetric;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备环境指标服务实现。
 *
 * <p>每次采集只覆盖最新快照，不追加历史行。单台设备失败不影响同凭据下
 * 其它设备；没有环境指标能力的平台由 {@link DeviceSession#readMetrics()} 显式抛错。</p>
 *
 * @since 4.0.0.54
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMetricServiceImpl implements DeviceMetricService {

    /** 节点标识最大长度，与表字段保持一致 */
    private static final int MAX_KEY_LENGTH = 128;

    /** 指标快照映射器 */
    private final DeviceMetricLatestMapper metricMapper;

    /** 设备服务：设备不存在、停用时按业务规则跳过 */
    private final DeviceService deviceService;

    /** 设备会话管理器 */
    private final DeviceSessionManager sessionManager;

    /** 时序写入器：历史读数只进时序存储，不追加明细行 */
    private final DeviceSeriesRecorder seriesRecorder;

    @Override
    public List<DeviceMetricLatest> listLatest(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return List.of();
        }
        return metricMapper.selectList(new LambdaQueryWrapper<DeviceMetricLatest>()
                .eq(DeviceMetricLatest::getDeviceInfoId, deviceId)
                .orderByAsc(DeviceMetricLatest::getDeviceMetricName)
                .orderByAsc(DeviceMetricLatest::getDeviceMetricKey));
    }

    @Override
    public int collectDevice(String deviceId) {
        DeviceInfo device = deviceService.getDevice(deviceId);
        if (device == null || DeviceStatusUtil.collectionStopped(device.getDeviceInfoStatus())) {
            return 0;
        }
        try {
            return collect(device);
        } catch (UnsupportedOperationException ex) {
            log.debug("设备不支持环境指标: id={}, message={}", deviceId, ex.getMessage());
            return 0;
        } catch (Exception ex) {
            log.warn("设备环境指标采集失败: id={}, message={}", deviceId, ex.getMessage());
            return 0;
        }
    }

    @Override
    public int collectByCredential(String credentialId) {
        if (credentialId == null || credentialId.isBlank()) {
            return 0;
        }
        List<DeviceInfo> devices = deviceService.listByCredentialId(credentialId);
        int count = 0;
        for (DeviceInfo device : devices) {
            if (DeviceStatusUtil.collectionStopped(device.getDeviceInfoStatus())) {
                continue;
            }
            try {
                count += collect(device);
            } catch (UnsupportedOperationException ex) {
                log.debug("设备不支持环境指标: id={}, message={}",
                        device.getDeviceInfoId(), ex.getMessage());
            } catch (Exception ex) {
                log.warn("设备环境指标采集失败: id={}, message={}",
                        device.getDeviceInfoId(), ex.getMessage());
            }
        }
        return count;
    }

    /**
     * 采集一台设备并覆盖最新快照。
     *
     * @param device 设备
     * @return 写入行数
     */
    private int collect(DeviceInfo device) {
        String deviceId = device.getDeviceInfoId();
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null) {
            deviceService.reconnect(deviceId);
            session = sessionManager.getSession(deviceId);
        }
        if (session == null) {
            return 0;
        }
        List<DeviceMetric> metrics = session.readMetrics();
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, DeviceMetricLatest> existing = new LinkedHashMap<>();
        for (DeviceMetricLatest row : listLatest(deviceId)) {
            existing.put(row.getDeviceMetricKey(), row);
        }
        Set<String> used = new HashSet<>();
        int written = 0;
        for (DeviceMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String key = normalizeKey(metric.key(), metric.name());
            String uniqueKey = key;
            int suffix = 2;
            while (!used.add(uniqueKey)) {
                uniqueKey = key + "#" + suffix++;
            }
            DeviceMetricLatest row = existing.get(uniqueKey);
            if (row == null) {
                row = new DeviceMetricLatest();
                row.setDeviceInfoId(deviceId);
                row.setDeviceMetricKey(uniqueKey);
                fill(row, metric, now);
                metricMapper.insert(row);
            } else {
                fill(row, metric, now);
                metricMapper.updateById(row);
            }
            // 历史读数旁路写时序：最新快照仍只保留一行，趋势查询走时序引擎
            seriesRecorder.recordMetric(deviceId, uniqueKey, row.getDeviceMetricName(),
                    row.getDeviceMetricValue(), row.getDeviceMetricUnit(), row.getDeviceMetricDataTime());
            written++;
        }
        return written;
    }

    /**
     * 填充快照字段。
     *
     * @param row    目标行
     * @param metric 指标
     * @param now    采集时间
     */
    private static void fill(DeviceMetricLatest row, DeviceMetric metric, LocalDateTime now) {
        row.setDeviceMetricName(metric.name() == null || metric.name().isBlank()
                ? row.getDeviceMetricKey() : metric.name().trim());
        row.setDeviceMetricValue(metric.value() == null ? "" : metric.value().trim());
        row.setDeviceMetricUnit(metric.unit() == null ? "" : metric.unit().trim());
        row.setDeviceMetricDataTime(metric.dataTime() == null ? now : metric.dataTime());
        row.setDeviceMetricCollectedAt(now);
    }

    /**
     * 规范化节点标识，避免空值或超长值破坏唯一键。
     *
     * @param key  厂商节点标识
     * @param name 节点名称
     * @return 可用标识
     */
    private static String normalizeKey(String key, String name) {
        String value = key;
        if (value == null || value.isBlank()) {
            value = name == null || name.isBlank() ? "metric" : name;
        }
        value = value.trim();
        return value.length() <= MAX_KEY_LENGTH ? value : value.substring(0, MAX_KEY_LENGTH);
    }
}
