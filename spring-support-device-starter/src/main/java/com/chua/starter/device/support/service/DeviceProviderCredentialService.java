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
     * 只切换启用/停用，不触碰 AK/SK、名称等其它字段。
     *
     * @param id      凭据 ID
     * @param enabled 是否启用
     * @return 落库后的凭据
     */
    DeviceProviderCredential updateEnabled(String id, boolean enabled);

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
     * 按凭据 ID 解析（设备可显式绑定某套 AK/SK）
     * <p>返回的 AppSecret 为明文，仅供建连使用，不会进入响应。</p>
     *
     * @param id 凭据 ID
     * @return 凭据；不存在返回 {@code null}
     */
    DeviceProviderCredential resolveById(String id);

    /**
     * 记录一次连通性探测结果（健康留痕）
     * <p>只更新探测时间与结果两列，不触碰 AK/SK、名称等业务字段。</p>
     *
     * @param id 凭据 ID
     * @param ok 本次探测是否成功
     */
    void recordTestResult(String id, boolean ok);

    /**
     * 各厂商的离线检测阈值（秒），取自默认套；无默认套则回退代码内置值
     *
     * @return 厂商标识 -> 阈值（秒）
     */
    Map<String, Integer> vendorThresholds();
}