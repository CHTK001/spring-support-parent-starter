package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.service.DeviceProviderConfigService;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 提供商配置服务实现
 * <p>厂商离线检测阈值读取自 {@code device_provider_credential} 表（默认套的阈值）。</p>
 *
 * <p><b>改动说明</b>：此前阈值只存在于本类的 {@code static final ConcurrentHashMap} 中，
 * {@link #updateThreshold} 仅改内存，重启即回到硬编码值。现改为落库，由
 * {@link DeviceProviderCredentialService} 提供。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProviderConfigServiceImpl implements DeviceProviderConfigService {

    /** 代码内置兜底阈值：表里查不到默认套时使用 */
    private static final int FALLBACK_THRESHOLD = 60;

    /** 设备 映射器 */
    private final DeviceInfoMapper deviceInfoMapper;

    /** 设备会话管理器 */
    private final DeviceSessionManager sessionManager;

    /** 提供商接入凭据服务（阈值与 AK/SK 的唯一来源） */
    private final DeviceProviderCredentialService credentialService;

    @Override
    public int getDefaultOfflineThreshold(String vendor) {
        Integer threshold = credentialService.vendorThresholds().get(vendor);
        return threshold == null ? FALLBACK_THRESHOLD : threshold;
    }

    @Override
    public Map<String, Integer> getThresholds() {
        return credentialService.vendorThresholds();
    }

    @Override
    public void updateThreshold(String vendor, int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("离线检测阈值必须大于 0");
        }
        Map<String, Integer> thresholds = credentialService.vendorThresholds();
        if (!thresholds.containsKey(vendor)) {
            // 尚无该厂商凭据时无法定位承载行——此前此写法只改内存，重启即丢；
            // 现在明确拒绝，避免调用方以为已持久化。
            throw new IllegalStateException(
                    "厂商 " + vendor + " 尚无凭据记录，请先在「提供商配置」中新增后再设置阈值");
        }
        com.chua.starter.device.support.entity.DeviceProviderCredential target =
                credentialService.resolveFor(vendor, null);
        if (target == null) {
            throw new IllegalStateException("厂商 " + vendor + " 无可用凭据，无法写入阈值");
        }
        target.setDeviceCredentialOfflineThreshold(threshold);
        credentialService.update(target.getDeviceCredentialId(), target);
        log.info("厂商离线检测阈值更新: vendor={}, threshold={}秒", vendor, threshold);
    }

    @Override
    public boolean checkOnline(DeviceInfo device, DeviceConnectInfo info) {
        DeviceSession session = sessionManager.getSession(device.getDeviceInfoId());
        if (session == null) {
            return false;
        }
        return session.isOnline();
    }

    @Override
    public List<DeviceInfo> listCheckableDevices() {
        // 待检测的是「本应在线」的设备：SDK 直连（IP+端口）或平台接入（有序列号）且未停用。
        // 早前只查 device_info_online=true 的行，而在线标记本身就是检测的产物——
        // 设备一旦掉线就不再进入候选集，掉线永远检测不出来。
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .isNotNull(DeviceInfo::getDeviceInfoIp)
                .or()
                .isNotNull(DeviceInfo::getDeviceInfoSerial));
        wrapper.ne(DeviceInfo::getDeviceInfoOnline, Boolean.FALSE);
        return deviceInfoMapper.selectList(wrapper);
    }
}