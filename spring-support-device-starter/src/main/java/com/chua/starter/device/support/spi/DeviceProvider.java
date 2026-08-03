package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.OrganizationNode;

import java.util.List;

/**
 * 设备厂商 Provider SPI
 * <p>各厂商实现由 {@code META-INF/services/com.chua.starter.device.spi.DeviceProvider} 注册。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface DeviceProvider {

    /**
     * 厂商标识
     */
    String getVendor();

    /**
     * 是否支持该连接信息
     */
    boolean support(DeviceConnectInfo info);

    /**
     * 连接设备，返回一个会话实例
     */
    DeviceSession connect(DeviceConnectInfo info);

    /**
     * 同步厂商组织树（可选实现）
     * <p>从厂商平台拉取组织/部门列表，调用方将其落库到 {@code device_organization}。</p>
     * <p>默认返回空列表。</p>
     *
     * @return 组织节点信息（deviceOrganizationId / deviceOrganizationName / parentId）
     */
    default List<OrganizationNode> fetchOrganizations() {
        return List.of();
    }
}
