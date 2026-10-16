package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceProviderCredential;

import java.util.List;
import java.util.Map;

/**
 * 提供商接入凭据服务
 * <p>管理厂商自定义的 AK/SK 等接入参数，并供建连时解析。</p>
 *
 * <p>同一 (厂商, 平台类型) 可登记多套凭据；每厂商至多一套标记为默认，
 * 厂商级离线阈值取默认套的阈值。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceProviderCredentialService {

    /**
     * 列出凭据
     *
     * @param vendor 厂商标识，为空则列出全部
     * @return 凭据列表
     */
    List<DeviceProviderCredential> list(String vendor);

    /**
     * 按主键取凭据
     *
     * @param id 凭据 ID
     * @return 凭据；不存在返回 {@code null}
     */
    DeviceProviderCredential get(String id);

    /**
     * 新增凭据
     *
     * @param credential 凭据内容
     * @return 落库后的凭据（含主键）
     */
    DeviceProviderCredential create(DeviceProviderCredential credential);

    /**
     * 更新凭据
     *
     * @param id         凭据 ID
     * @param credential 待更新内容
     * @return 落库后的凭据
     */
    DeviceProviderCredential update(String id, DeviceProviderCredential credential);

    /**
     * 删除凭据
     *
     * @param id 凭据 ID
     */
    void delete(String id);

    /**
     * 建连时解析凭据：优先取该厂商默认套，其次取同平台启用中的任意一套
     *
     * @param vendor       厂商标识
     * @param platformType 平台类型；SDK 直连传 {@code null}
     * @return 凭据；无可用凭据返回 {@code null}
     */
    DeviceProviderCredential resolveFor(String vendor, String platformType);

    /**
     * 各厂商的离线检测阈值（秒），取自默认套；无默认套则回退代码内置值
     *
     * @return 厂商标识 -> 阈值（秒）
     */
    Map<String, Integer> vendorThresholds();
}