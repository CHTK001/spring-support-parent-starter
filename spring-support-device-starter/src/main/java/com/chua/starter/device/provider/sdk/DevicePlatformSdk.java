package com.chua.starter.device.provider.sdk;

import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.TimeRange;
import com.chua.starter.device.spi.model.VendorDevice;

import java.util.List;
import java.util.Map;

/**
 * 厂商平台 SDK 统一门面。
 *
 * <p><b>为什么需要它</b>：平台已有 5 个（海康安防 / 云曜 / 云眸 / 大华云睿 / 乐橙），后续还会加。
 * 各平台官方 SDK 的有无、认证方式、分页字段、错误码全都不同。如果每个平台各写一套
 * HTTP + 签名 + 字段映射，业务侧就会被平台细节污染，加第 6 个平台要改多处。</p>
 *
 * <p>本门面把「平台差异」收进实现类：业务侧（DeviceProvider / Controller）只调这里的方法，
 * 不关心底层是官方 SDK 还是 OpenAPI HTTP。新增平台 = 新增一个实现。</p>
 *
 * <p><b>官方 SDK 覆盖情况（Maven Central 实测）</b>：</p>
 * <ul>
 *   <li>海康综合安防：{@code com.hikvision.ga:artemis-http-client} 1.1.3 —— 接 SDK</li>
 *   <li>海康云曜 / 云眸 / 大华云睿 / 乐橙：官方未发 Java SDK，按官方 OpenAPI 实现</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DevicePlatformSdk {

    /**
     * 平台标识（与 {@code DeviceConnectInfo.platformType} 同口径）。
     *
     * @return 平台标识
     */
    String platform();

    /**
     * 本次实现是否基于官方 SDK（用于诊断面板与日志区分）。
     *
     * @return true 表示走官方 SDK
     */
    default boolean officialSdk() {
        return false;
    }

    /**
     * 当前实现所依据的官方接口版本。
     *
     * <p>平台接口分版本演进，且同平台不同版本路径/字段差异很大（云曜 v1 可用、v2 返回 400）。
     * 实现类必须显式声明版本，换版本时只改这一处，或由凭据 {@code apiVersion} 覆盖。</p>
     *
     * @return 版本描述
     */
    PlatformApiVersion apiVersion();

    /**
     * 该平台支持的全部官方接口目录。
     *
     * <p>这是「全功能」的核心：目录登记平台所有接口（设备/监控点/组织/人员/门禁/告警/视频…），
     * 前端按域渲染入口，业务按 {@code code} 直接调用，不需要为每个接口写一个 Java 方法。</p>
     *
     * @return 接口声明列表
     */
    List<PlatformApiSpec> apis();

    /**
     * 按接口编码调用平台接口。
     *
     * @param code   接口编码（见 {@link #apis()}）
     * @param params 请求参数；GET 走 query，POST 走 JSON body
     * @return 调用结果
     */
    PlatformApiResult invoke(String code, Map<String, Object> params);

    /**
     * 连通性探测：只验证凭据与网关，不要求设备存在。
     *
     * @return true 表示凭据有效且网关可达
     */
    boolean testConnection();

    /**
     * 拉取设备目录。
     *
     * @return 设备条目；平台不支持或为空时返回空列表
     */
    List<VendorDevice> listDevices();

    /**
     * 拉取监控点目录（设备 + 通道维度）；平台无该概念时回退空列表。
     *
     * @return 监控点条目
     */
    default List<VendorDevice> listMonitors() {
        return List.of();
    }

    /**
     * 拉取组织树。
     *
     * @return 组织节点
     */
    default List<OrganizationNode> listOrganizations() {
        return List.of();
    }

    /**
     * 拉取人员列表。
     *
     * @return 人员条目
     */
    default List<DevicePerson> listPersons() {
        return List.of();
    }

    /**
     * 获取实时预览地址。
     *
     * @param channel    通道号
     * @param protocol   协议：hls / rtsp / rtmp
     * @param streamType 码流：main / sub
     * @return 播放地址；平台未返回时为空串
     */
    default String liveUrl(int channel, String protocol, String streamType) {
        return "";
    }

    /**
     * 云台控制。
     *
     * @param channel 通道号
     * @param cmd     方向/动作
     * @param speed   速度 0~1
     */
    default void ptz(int channel, PtzCommand cmd, double speed) {
        // 平台未实现时无操作
    }

    /**
     * 查询门禁出入记录。
     *
     * @param range 时间范围
     * @return 出入记录
     */
    default List<AccessRecord> accessRecords(TimeRange range) {
        return List.of();
    }

    /**
     * 拉取事件。
     *
     * @param range 时间范围
     * @return 事件
     */
    default List<DeviceEvent> events(TimeRange range) {
        return List.of();
    }
}
