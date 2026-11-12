package com.chua.starter.server.support.terminal.util;

import com.chua.common.support.nativeapi.NativeServices;
import com.chua.common.support.nativeapi.RdpNativeSpi;

/**
 * RDP 直连原生桥的可用性检查。
 * <p>
 * {@code utils-support-native-rdp} 不在编译期依赖里：部署时带上就有直连能力，
 * 没带时 {@link #exists()} 返回 {@code false}，业务侧直接提示“不支持”。
 * 本类只查 {@link RdpNativeSpi} 契约的 SPI 实现，不引用 native 模块或 JNA 类型。
 * </p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class RdpjNative {

    /**
     * 原生库缺失时的统一提示（业务侧直接透传给前端）
     */
    public static final String UNSUPPORTED_MESSAGE = "当前环境不支持 RDP 直连：未部署 RDP 原生库（utils-support-native-rdp）";

    private RdpjNative() {
    }

    /**
     * 原生桥是否可用
     *
     * @return true 表示可以继续走 RDP 直连流程
     */
    public static boolean exists() {
        return NativeServices.get(RdpNativeSpi.class) != null;
    }
}
