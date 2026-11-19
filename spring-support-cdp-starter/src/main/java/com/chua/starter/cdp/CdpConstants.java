package com.chua.starter.cdp;

/**
 * CDP 调试模块常量
 *
 * <p>REST 路径前缀、WebSocket 路径默认值的统一维护点。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class CdpConstants {

    /**
     * REST 路径前缀
     */
    public static final String API_PREFIX = "/ms/cdp";

    /**
     * 默认 WebSocket 路径
     */
    public static final String WS_PATH = "/ws/cdp";

    /**
     * 无参私有构造
     */
    private CdpConstants() {
    }
}
