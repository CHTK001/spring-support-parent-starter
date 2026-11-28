package com.chua.starter.common.support.message;

/**
 * 站内消息接收人查询契约。
 *
 * <p>实现方负责把平台用户标识解析为站内消息所需的用户 ID，业务模块不穿透用户持久层。</p>
 */
public interface StationUserDirectory {

    /**
     * 按登录用户名查询用户 ID。
     *
     * @param username 登录用户名
     * @return 用户 ID；不存在时返回 {@code null}
     */
    Integer findUserIdByUsername(String username);
}
