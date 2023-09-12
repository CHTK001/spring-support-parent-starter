
package com.chua.starter.oauth.client.support.enums;

/**
 * 模式
 *
 * @author CH
 * @since 2022/7/26 8:20
 */
public enum AuthType {
    /**
     * session
     */
    APP,
    /**
     * token
     */
    WEB,
    /**
     * 浙里办
     */
    ZLB,
    /**
     * 浙里办-微信小程序
     */
    ZLB_WECHAT,
    /**
     * 浙政钉
     */
    ZZD,
    /**
     * 其它
     */
    CUSTOM,
    /**
     * 根据URL自动判断是WEB还是ENBED
     */
    AUTO,

    /**
     * 嵌入式
     */
    EMBED
}
