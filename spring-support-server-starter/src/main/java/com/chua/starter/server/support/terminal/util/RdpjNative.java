package com.chua.starter.server.support.terminal.util;

/**
 * RDP 直连原生桥的存在性检查。
 * <p>
 * {@code utils-support-native-rdp} 在 pom 里是 {@code optional} 依赖：带了就正常直连，
 * 没带时 {@link #exists()} 返回 {@code false}，业务侧直接提示“不支持”，
 * 不去触碰 {@code RdpjBridge} 的任何成员（类不在 classpath 上时引用即报
 * {@code NoClassDefFoundError}）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class RdpjNative {

    /**
     * 桥接类全限定名
     */
    private static final String BRIDGE_CLASS = "com.chua.nativerdp.support.RdpjBridge";

    /**
     * 原生库缺失时的统一提示（业务侧直接透传给前端）
     */
    public static final String UNSUPPORTED_MESSAGE = "当前环境不支持 RDP 直连：未部署 RDP 原生库（utils-support-native-rdp）";

    /**
     * 存在性探测结果（null 表示尚未探测）
     */
    private static volatile Boolean present;

    private RdpjNative() {
    }

    /**
     * 原生桥是否在 classpath 上
     *
     * @return true 表示可以继续走 RDP 直连流程
     */
    public static boolean exists() {
        Boolean cached = present;
        if (cached != null) {
            return cached;
        }
        synchronized (RdpjNative.class) {
            if (present == null) {
                present = probe();
            }
            return present;
        }
    }

    /**
     * 探测桥接类
     *
     * @return true 表示加载成功
     */
    private static boolean probe() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            try {
                Class.forName(BRIDGE_CLASS, false, tccl);
                return true;
            } catch (ClassNotFoundException ignored) {
                // 回落到本类加载器
            }
        }
        try {
            Class.forName(BRIDGE_CLASS, false, RdpjNative.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
