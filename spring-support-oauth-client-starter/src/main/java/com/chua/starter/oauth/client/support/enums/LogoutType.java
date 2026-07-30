
package com.chua.starter.oauth.client.support.enums;

/**
 * 模式
 *
 * @author CH
 */
public enum LogoutType {
    /**
     * session
     */
    NONE,
    /**
     * token
     */
    LOGOUT,
    /**
     * token
     */
    LOGOUT_ALL,
    /**
     * 注销
     */
    UN_REGISTER,
    /**
     * 踢下线
     */
    KICK
}
