package com.chua.starter.device.support.service;

import java.util.Map;

/**
 * 厂商设备目录同步服务。
 *
 * <p>调用各厂商 SPI 的 {@code fetchDevices()}，按 {@code vendor + 序列号} 幂等
 * 新增/更新本地台账；未实现目录 API 的厂商返回空列表，不影响手工录入。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceSyncService {

    /**
     * 同步厂商设备。
     *
     * @param vendor 厂商标识；为空时同步全部已注册厂商
     * @return created / updated / skipped 统计
     */
    Map<String, Integer> syncDevices(String vendor);
}
