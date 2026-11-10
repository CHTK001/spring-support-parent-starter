package com.chua.starter.device.provider.sdk;

import com.chua.starter.device.spi.model.DeviceConnectInfo;

import java.util.List;

/**
 * 平台 SDK 工厂 SPI 扩展点。
 *
 * <p>本接口是 common-starter {@code ServiceProvider} 体系下的扩展点：
 * 每个实现代表一个「厂商 + 平台」的 SDK 门面提供者，按
 * {@code vendor.platform} 这样的键注册到 SPI 注册表，
 * {@link DevicePlatformSdks#of(DeviceConnectInfo)} 按需取出并实例化。</p>
 *
 * <p><b>为什么要 SPI 化</b>：设备平台会持续增加（海康系 3 个、大华系 2 个，
 * 后续还有萤石 / 宇视 / 国标 GB28181 / ONVIF 等）。若每个平台都在
 * {@link DevicePlatformSdks} 里写一个 if 分支，加第 N 个平台就要改工厂，
 * 且无法让第三方模块在不改本模块代码的前提下接入。</p>
 *
 * <p><b>扩展方式</b>：实现本接口并让 {@link #name()} 返回唯一键
 * （约定 {@code vendor.platform}），再通过 Spring Bean 装配到
 * {@link DevicePlatformSdks} 的注册表；业务侧只调用门面，不感知平台差异。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DevicePlatformSdkProvider {

    /**
     * SPI 扩展名（唯一键），约定 {@code vendor.platform}，例如 {@code hikvision.cloud-yao}。
     *
     * <p>大小写不敏感；重复注册时后者覆盖前者（并记录日志）。</p>
     *
     * @return 扩展名
     */
    String name();

    /**
     * 厂商标识。
     *
     * @return 厂商标识，例如 {@code hikvision}
     */
    default String vendor() {
        return "";
    }

    /**
     * 平台标识（与 {@code DeviceConnectInfo.platformType} 同口径）。
     *
     * @return 平台标识，例如 {@code cloud-yao}
     */
    default String platform() {
        return "";
    }

    /**
     * 是否支持该连接信息。
     *
     * @param info 连接信息
     * @return true 表示由本扩展处理
     */
    default boolean support(DeviceConnectInfo info) {
        return false;
    }

    /**
     * 创建一个平台 SDK 门面实现。
     *
     * @param info 连接信息
     * @return SDK 实现；不支持时返回 {@code null}
     */
    DevicePlatformSdk create(DeviceConnectInfo info);

    /**
     * 该扩展已实现的接口目录（用于统一诊断输出）。
     *
     * @return 接口声明列表
     */
    default List<PlatformApiSpec> apis() {
        return List.of();
    }
}
