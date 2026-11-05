// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.realtime.DeviceRealtimePublisher;
import com.chua.starter.device.support.realtime.DeviceStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 设备在线状态服务
 *
 * <p>统一处理“在线/离线”落库与事件发布，避免调度器、手动刷新、重连三处各写一套：</p>
 * <ul>
 *   <li>状态变化时写 {@code online} 与 {@code last_online_at}/{@code last_offline_at}；</li>
 *   <li>在线设备按阈值周期刷新心跳时间，供“最后在线”与在线率使用；</li>
 *   <li>只有状态真正变化才发事件，避免每秒刷屏。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusService {

    /**
     * 心跳刷新最小间隔（秒），避免每个检测周期都写库
     */
    private static final long MIN_HEARTBEAT_REFRESH_SEC = 30;

    /**
     * 设备信息 映射器
     */
    private final DeviceInfoMapper deviceInfoMapper;

    /**
     * 实时事件发布器
     */
    private final DeviceRealtimePublisher realtimePublisher;

    /**
     * 提供商配置服务（取厂商默认阈值）
     */
    private final DeviceProviderConfigService providerConfigService;

    /**
     * 更新在线状态
     *
     * @param device 设备（会被同步更新内存字段）
     * @param online 是否在线
     * @return 状态是否发生变化
     */
    public boolean updateOnline(DeviceInfo device, boolean online) {
        return updateOnline(device, online, false);
    }

    /**
     * 更新在线状态
     *
     * @param device     设备（会被同步更新内存字段）
     * @param online     是否在线
     * @param forceWrite 是否强制写库（手动刷新/重连时使用）
     * @return 状态是否发生变化
     */
    public boolean updateOnline(DeviceInfo device, boolean online, boolean forceWrite) {
        if (device == null || device.getDeviceInfoId() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean current = Boolean.TRUE.equals(device.getDeviceInfoOnline());
        boolean changed = current != online;
        boolean refreshHeartbeat = online && needRefreshHeartbeat(device, now);
        if (!changed && !refreshHeartbeat && !forceWrite) {
            return false;
        }

        DeviceInfo update = new DeviceInfo();
        update.setDeviceInfoId(device.getDeviceInfoId());
        update.setDeviceInfoOnline(online);
        update.setDeviceInfoUpdatedAt(now);
        if (online) {
            update.setDeviceInfoLastOnlineAt(now);
            device.setDeviceInfoLastOnlineAt(now);
        } else {
            update.setDeviceInfoLastOfflineAt(now);
            device.setDeviceInfoLastOfflineAt(now);
        }
        deviceInfoMapper.updateById(update);

        device.setDeviceInfoOnline(online);
        device.setDeviceInfoUpdatedAt(now);
        if (changed) {
            publishStatus(device, now);
            log.info("设备状态变更: id={}, name={}, online={}", device.getDeviceInfoId(),
                    device.getDeviceInfoName(), online);
        } else {
            log.debug("设备心跳刷新: id={}, online={}", device.getDeviceInfoId(), online);
        }
        return changed;
    }

    /**
     * 是否需要刷新心跳时间
     *
     * @param device 设备
     * @param now    当前时间
     * @return true 表示需要刷新
     */
    private boolean needRefreshHeartbeat(DeviceInfo device, LocalDateTime now) {
        LocalDateTime lastOnlineAt = device.getDeviceInfoLastOnlineAt();
        if (lastOnlineAt == null) {
            return true;
        }
        long interval = heartbeatInterval(device);
        return Duration.between(lastOnlineAt, now).getSeconds() >= interval;
    }

    /**
     * 心跳刷新间隔：优先设备阈值，最小 30 秒、最大 1 小时
     *
     * @param device 设备
     * @return 间隔秒数
     */
    private long heartbeatInterval(DeviceInfo device) {
        Integer threshold = device.getDeviceInfoOfflineThreshold();
        if (threshold == null || threshold <= 0) {
            threshold = providerConfigService.getDefaultOfflineThreshold(device.getDeviceInfoVendor());
        }
        return Math.max(MIN_HEARTBEAT_REFRESH_SEC, Math.min(threshold, 3600));
    }

    /**
     * 发布状态事件
     *
     * @param device 设备
     * @param now    事件时间
     */
    private void publishStatus(DeviceInfo device, LocalDateTime now) {
        try {
            realtimePublisher.publish("status", new DeviceStatusEvent(
                    device.getDeviceInfoId(),
                    device.getDeviceInfoName(),
                    device.getDeviceInfoVendor(),
                    Boolean.TRUE.equals(device.getDeviceInfoOnline()),
                    device.getDeviceInfoLatitude(),
                    device.getDeviceInfoLongitude(),
                    device.getDeviceInfoLastOnlineAt(),
                    device.getDeviceInfoLastOfflineAt(),
                    now));
        } catch (Exception e) {
            // 实时通道故障不能影响状态落库
            log.warn("设备状态事件发布失败: id={}, message={}", device.getDeviceInfoId(), e.getMessage());
        }
    }
}