
package com.chua.starter.oauth.client.support.enums;

/**
 * 模式
 *
 * @author CH
 * @since 2022/7/26 8:20
 */
public enum AuthType {
    /**
     * 微信小程序
     */
    WX_MINI_APP,

    /**
     * 专家
     */
    PROFESSOR,
    /**
     * app
     */
    APP,
    /**
     * token
     */
    WEB,
    /**
     * 系统
     */
    SYSTEM,

    /**
     * 租户
     */
    TENANT,

    /**
     * 三方
     */
    THIRD_PARTY,
    /**
     * 手机
     */
    PHONE,
    /**
     * 根据URL自动判断是WEB还是EMBED
     */
    AUTO,

    /**
     * 嵌入式
     */
    STATIC
}
