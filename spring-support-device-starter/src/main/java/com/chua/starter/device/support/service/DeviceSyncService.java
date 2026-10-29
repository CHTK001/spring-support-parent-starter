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
     * 按凭据同步设备（拉取方向）。
     *
     * <p>以 {@code credentialId} 为维度建连，只拉取该凭据名下（凭据所属厂商/平台/产品/项目）
     * 的设备目录，按 vendor + externalId 幂等 upsert；设备的 {@code deviceInfoCredentialId}
     * 回写凭据 ID，使在线检测/视频/云台等方向知道该设备由哪套凭据维系。</p>
     *
     * @param credentialId 凭据 ID
     * @return created / updated / skipped 统计
     */
    Map<String, Integer> syncDevicesByCredential(String credentialId);

    /**
     * 按凭据同步组织树（与 syncDevicesByCredential 平齐的维度）。
     *
     * <p>以厂商侧组织标识在凭据维度建连拉取，按 vendor + 厂商侧标识幂等 upsert；
     * 落库后更新设备基准的状态。</p>
     *
     * @param credentialId 凭据 ID
     * @return created / updated / skipped 统计
     */
    Map<String, Integer> syncOrganizationsByCredential(String credentialId);

    /**
     * 按凭据同步人员列表（与 syncDevicesByCredential 平齐的维度）。
     *
     * <p>人员与组织表解耦，单建 {@code device_person}（按 vendor + 厂商侧人员标识
     * 幂等入库；行政归属挂接厂商侧组织标识），避免 API 人员结构换表时耦合到
     * org-person 关联表。</p>
     *
     * @param credentialId 凭据 ID
     * @return created / updated / skipped 统计
     */
    Map<String, Integer> syncPersonsByCredential(String credentialId);

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
