package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.OrganizationNode;

import java.util.List;

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
}
