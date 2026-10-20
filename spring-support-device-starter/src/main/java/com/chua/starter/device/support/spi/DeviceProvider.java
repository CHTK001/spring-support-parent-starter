package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceCapability;
import com.chua.starter.device.spi.model.DeviceVendorDescriptor;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.VendorDevice;

import java.util.List;
import java.util.Set;

/**
 * 设备厂商 提供者 SPI
 * <p>各厂商实现由 {@code META-INF/services/com.chua.starter.device.spi.DeviceProvider} 注册。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceProvider {

    /**
     * 厂商标识
     * @return 获取供应商的结果
     */
    String getVendor();

    /**
     * 是否支持该连接信息
     * @param info 信息
     * @return 支持的结果
     */
    boolean support(DeviceConnectInfo info);

    /**
     * 连接设备，返回一个会话实例
     * @param info 信息
     * @return 连接的结果
     */
    DeviceSession connect(DeviceConnectInfo info);

    /**
     * 同步厂商组织树（可选实现）
     * <p>从厂商平台拉取组织/部门列表，调用方将其落库到 {@code device_organization}。</p>
     * <p>默认返回空列表。</p>
     *
     * @return 组织节点信息（deviceOrganizationId / device组织名称 / 父标识）
     */
    default List<OrganizationNode> fetchOrganizations() {
        return List.of();
    }

    /**
     * 同步厂商设备目录（可选实现）。
     *
     * <p>从厂商平台拉取设备列表，调用方按 {@code vendor + externalId} 幂等入库。
     * 未接入平台 API 的厂商保持默认空列表，不影响手工录入与 SDK 直连。</p>
     *
     * @return 厂商设备条目
     */
    default List<VendorDevice> fetchDevices() {
        return List.of();
    }

    /**
     * 厂商描述符：厂家名称、接入方式、平台、凭据字段与能力位。
     *
     * <p>前端“厂商接入”页据此动态渲染表单与入口；新厂商实现只需覆盖本方法，
     * 无需改前端。默认只声明厂商标识，能力为空。</p>
     *
     * @return 厂商描述符
     */
    default DeviceVendorDescriptor descriptor() {
        return new DeviceVendorDescriptor(
                getVendor(),
                getVendor(),
                List.of(),
                List.of(),
                Set.<DeviceCapability>of(),
                60,
                null,
                null);
    }
}
