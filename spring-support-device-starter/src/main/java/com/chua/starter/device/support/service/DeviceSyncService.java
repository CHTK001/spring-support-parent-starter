package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceInfo;

import java.util.Map;

/**
 * 厂商设备目录同步服务。
 *
 * <p>调用各厂商 SPI 的 {@code fetchDevices()}，按 {@code vendor + 序列号} 幂等
 * 新增/更新本地台账；未实现目录 API 的厂商返回空列表，不影响手工录入。</p>
 *
 * <p>写回方向（本地 → 厂商）由 {@code upsertRemote / removeRemote} 提供，
 * 仅在厂商实现了 SPI 写回能力时生效，默认返回「不支持」。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceSyncService {

    /**
     * 同步厂商设备（拉取方向）。
     *
     * @param vendor 厂商标识；为空时同步全部已注册厂商
     * @return created / updated / skipped 统计
     */
    Map<String, Integer> syncDevices(String vendor);

    /**
     * 把单台本地设备写回到厂商侧（推送方向）。
     *
     * <p>仅对 {@code deviceInfoSource = vendor} 的设备有意义；厂商不支持写回时
     * 返回 {@code false} 并保持本地不动。</p>
     *
     * @param device 本地台账设备
     * @return 是否写回成功；厂商不支持时为 {@code false}
     */
    boolean pushDevice(DeviceInfo device);

    /**
     * 从厂商侧删除设备（推送方向）。
     *
     * <p>按 {@code deviceInfoExternalId} 调厂商删除接口；厂商不支持时返回
     * {@code false}，由调用方决定是否仍保留本地台账。</p>
     *
     * @param device 本地台账设备
     * @return 是否删除成功；厂商不支持时为 {@code false}
     */
    boolean removeRemote(DeviceInfo device);
}
