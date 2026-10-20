package com.chua.starter.device.support.service.impl;

import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.model.VendorDevice;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.model.DeviceSaveRequest;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 厂商设备目录同步实现：按 vendor + 序列号幂等 upsert。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSyncServiceImpl implements DeviceSyncService {

    private final List<DeviceProvider> providers;
    private final DeviceInfoMapper deviceInfoMapper;
    private final DeviceService deviceService;

    @Override
    public Map<String, Integer> syncDevices(String vendor) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (DeviceProvider provider : providers) {
            if (StringUtils.hasText(vendor) && !vendor.equals(provider.getVendor())) {
                continue;
            }
            List<VendorDevice> remoteDevices;
            try {
                remoteDevices = provider.fetchDevices();
            } catch (Exception e) {
                log.warn("[Device][Sync] 厂商 {} 拉取设备失败: {}", provider.getVendor(), e.getMessage());
                continue;
            }
            for (VendorDevice remote : remoteDevices) {
                if (remote == null || !StringUtils.hasText(remote.externalId())) {
                    skipped++;
                    continue;
                }
                String serial = StringUtils.hasText(remote.serial()) ? remote.serial() : remote.externalId();
                try {
                    if (upsert(provider, remote, serial)) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    skipped++;
                    log.warn("[Device][Sync] 同步设备 {} 失败: {}", remote.externalId(), e.getMessage());
                }
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        log.info("[Device][Sync] vendor={} 完成: {}", vendor, result);
        return result;
    }

    /**
     * @return true 表示新增，false 表示更新
     */
    private boolean upsert(DeviceProvider provider, VendorDevice remote, String serial) {
        DeviceInfo existing = deviceInfoMapper.selectByVendorAndSerial(provider.getVendor(), serial);
        if (existing != null) {
            existing.setDeviceInfoName(remote.name());
            existing.setDeviceInfoModel(remote.model());
            existing.setDeviceInfoCategory(remote.category());
            existing.setDeviceInfoSerial(serial);
            existing.setDeviceInfoLatitude(remote.latitude());
            existing.setDeviceInfoLongitude(remote.longitude());
            existing.setDeviceInfoAddress(remote.address());
            existing.setDeviceInfoChannelCount(remote.channelCount());
            existing.setDeviceInfoOnline(Boolean.TRUE.equals(remote.online()));
            if (Boolean.TRUE.equals(remote.online())) {
                existing.setDeviceInfoLastOnlineAt(LocalDateTime.now());
            }
            existing.setDeviceInfoUpdatedAt(LocalDateTime.now());
            deviceInfoMapper.updateById(existing);
            return false;
        }

        DeviceSaveRequest request = new DeviceSaveRequest();
        request.setDeviceInfoName(remote.name());
        request.setDeviceInfoCode(serial);
        request.setDeviceInfoVendor(provider.getVendor());
        request.setDeviceInfoModel(remote.model());
        request.setDeviceInfoCategory(StringUtils.hasText(remote.category()) ? remote.category() : "camera");
        request.setDeviceInfoSerial(serial);
        request.setDeviceSerial(serial);
        request.setDeviceInfoIp(remote.ip());
        request.setDeviceInfoPort(remote.port());
        request.setDeviceInfoLatitude(remote.latitude());
        request.setDeviceInfoLongitude(remote.longitude());
        request.setDeviceInfoAddress(remote.address());
        request.setDeviceInfoChannelCount(remote.channelCount());
        request.setDeviceInfoSource("vendor");
        request.setDeviceInfoStatus("normal");
        request.setAuthType("PLATFORM");
        if (!provider.descriptor().platforms().isEmpty()) {
            request.setPlatformType(provider.descriptor().platforms().get(0).code());
        }
        // 同步只落台账，不在同步线程里建立厂商会话；在线状态由定时检测校准
        request.setConnect(false);
        DeviceInfo created = deviceService.addDevice(request);
        if (Boolean.TRUE.equals(remote.online())) {
            created.setDeviceInfoOnline(true);
            created.setDeviceInfoLastOnlineAt(LocalDateTime.now());
            deviceInfoMapper.updateById(created);
        }
        return true;
    }
}
