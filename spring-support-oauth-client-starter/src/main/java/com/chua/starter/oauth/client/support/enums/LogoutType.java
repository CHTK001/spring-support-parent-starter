
package com.chua.starter.oauth.client.support.enums;

/**
* 模式
*
* @author CH
* @since 4.0.0
 */
public enum LogoutType {
    /**
    * 会话
     */
    NONE,
    /**
    * 令牌
     */
    LOGOUT,
    /**
    * 令牌
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
