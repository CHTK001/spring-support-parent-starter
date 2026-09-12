package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.service.DeviceProviderConfigService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供商配置服务实现
 * <p>每个厂商的默认离线检测时间及检测逻辑。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProviderConfigServiceImpl implements DeviceProviderConfigService {

    /**
     * 厂商默认离线检测阈值（秒）
     */
    private static final Map<String, Integer> VENDOR_THRESHOLDS = new ConcurrentHashMap<>();

    static {
        VENDOR_THRESHOLDS.put("hikvision", 60);
        VENDOR_THRESHOLDS.put("dahua", 60);
        VENDOR_THRESHOLDS.put("ezviz", 120);
        VENDOR_THRESHOLDS.put("isup", 90);
    }

    /**
      * 设备 映射器
     */
    private final DeviceInfoMapper deviceInfoMapper;

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    @Override
    /** 获取默认offline阈值 */
    public int getDefaultOfflineThreshold(String vendor) {
        return VENDOR_THRESHOLDS.getOrDefault(vendor, 60);
    }

    @Override
    /** 获取阈值 */
    public Map<String, Integer> getThresholds() {
        return new HashMap<>(VENDOR_THRESHOLDS);
    }

    @Override
    /** 更新阈值 */
    public void updateThreshold(String vendor, int threshold) {
        VENDOR_THRESHOLDS.put(vendor, threshold);
        log.info("厂商离线检测阈值更新: vendor={}, threshold={}秒", vendor, threshold);
    }

    @Override
    /** 校验Online */
    public boolean checkOnline(DeviceInfo device, DeviceConnectInfo info) {
        DeviceSession session = sessionManager.getSession(device.getDeviceInfoId());
        if (session == null) {
            return false;
        }
        return session.isOnline();
    }

    @Override
    /** 列表检查devices */
    public List<DeviceInfo> listCheckableDevices() {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceInfo::getDeviceInfoOnline, true);
        return deviceInfoMapper.selectList(wrapper);
    }
}