// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceCapability;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.DeviceVendorDescriptor;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.VendorDevice;

import java.util.List;
import java.util.Set;

/**
 * 设备厂商 提供者 SPI
 * <p>各厂商实现由 {@code META-INF/services/com.chua.starter.device.spi.DeviceProvider} 注册。</p>
 * @apiNote 实际经 Spring ComponentScan + List 注入（见 DeviceAutoConfiguration#deviceProviders），并非 Java ServiceLoader 发现。
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
     * 按凭据建连后的会话语境拉取设备目录（凭据维度同步入口）。
     *
     * <p>同步服务先按凭据建连，再以该凭据的会话调用本方法，确保每个平台账号只拉取
     * 自己名下的设备目录；厂商未反转会话版本时回退到 {@link #fetchDevices()}。</p>
     *
     * @param session 按凭据建连后的会话
     * @return 厂商设备条目
     */
    default List<VendorDevice> fetchDevices(DeviceSession session) {
        return fetchDevices();
    }

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
     * 按凭据建连后的会话语境拉取组织树（与 fetchDevices(DeviceSession) 对齐）。
     *
     * <p>同步服务先按凭据建连，再以该凭据的会话调用本方法，确保每个平台账号只拉取
     * 自己名下的组织树。</p>
     *
     * @param session 按凭据建连后的会话
     * @return 组织节点
     */
    default List<OrganizationNode> fetchOrganizations(DeviceSession session) {
        return fetchOrganizations();
    }

    /**
     * 按凭据建连后的会话语境拉取人员列表（与 fetchDevices(DeviceSession) 对齐）。
     *
     * <p>人员放入与组织表同级的 {@code device_person}（按 vendor + 厂商侧人员标识
     * 幂等入库），不与人力的组织关系表耦合，只拉取平台可暴露的低门槛字段。</p>
     *
     * @param session 按凭据建连后的会话
     * @return 人员条目（组织标识/姓名/工号）
     */
    default List<DevicePerson> fetchPersons(DeviceSession session) {
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

    /**
     * 把本地台账设备写回到厂商侧（可选实现）。
     *
     * <p>与 {@link #fetchDevices()} 的方向相反：本地编辑了厂商来源设备的名称、
     * 位置等字段后，由业务层把 {@link VendorDevice} 推回厂商平台，保证两侧台账一致。</p>
     *
     * <p>默认返回 {@code false}，表示厂商未实现写回能力；实现了的厂商应在平台
     * 侧创建/更新成功后返回 {@code true}。</p>
     *
     * @param device 本地台账映射后的厂商设备条目
     * @return 是否写回成功；{@code false} 表示不支持或失败
     */
    default boolean upsertRemote(VendorDevice device) {
        return false;
    }

    /**
     * 从厂商侧删除设备（可选实现）。
     *
     * <p>本地删除厂商来源设备时调用，按 {@code externalId} 在平台侧一并移除。</p>
     *
     * @param externalId 厂商侧设备唯一标识（同步幂等键）
     * @return 是否删除成功；{@code false} 表示不支持或失败
     */
    default boolean removeRemote(String externalId) {
        return false;
    }
}