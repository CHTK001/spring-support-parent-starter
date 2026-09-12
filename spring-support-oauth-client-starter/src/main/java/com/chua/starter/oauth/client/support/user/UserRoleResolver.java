package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.spi.annotations.Spi;

import java.util.Set;

/**
 * 用户角色解析器（SPI）
 * <p>
   * 用于 http / http-lite 协议下，OAuth 服务端 /验证 返回的角色可能为空时，
   * 由业务侧（如系统管理）根据 用户标识 补充用户的角色编码。
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("default")
public interface UserRoleResolver {

    /**
     * 解析用户角色编码集合
     *
     * @param userId   用户标识（OAuth /验证 返回的 用户标识）
     * @param username 用户名
     * @return 角色编码集合；无角色或失败时返回 空
     */
    Set<String> resolve(String userId, String username);
}
