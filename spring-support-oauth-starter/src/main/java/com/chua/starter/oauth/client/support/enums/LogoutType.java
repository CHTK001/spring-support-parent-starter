package com.chua.starter.oauth.client.support.enums;

/**
 * 登出/会话操作类型
 *
 * <p>{@code uid} 的语义随类型变化，调用方必须按注释传参：</p>
 * <ul>
 *   <li>{@link #LOGOUT}：uid = 令牌</li>
 *   <li>{@link #LOGOUT_ALL} / {@link #UN_REGISTER}：uid = 令牌（服务端从令牌结构中提取用户 id 哈希）</li>
 *   <li>{@link #KICK}：uid = 用户 id（也兼容旧调用方传令牌）</li>
 *   <li>{@link #KICK_OTHERS}：uid = 当前令牌（保留该令牌，踢掉同用户其它会话）</li>
 *   <li>{@link #DISABLE} / {@link #ENABLE}：uid = 用户 id（写/删用户级黑名单并吊销全部会话）</li>
 * </ul>
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
     * 踢下线：uid 为用户 id（兼容旧调用方传令牌），吊销该用户全部会话
     */
    KICK,
    /**
     * 踢掉其它设备：uid 为当前令牌，保留该令牌、吊销同用户其它会话
     *
     * <p>典型场景：用户修改密码后，其它设备需要重新登录，当前设备保持在线。</p>
     */
    KICK_OTHERS,
    /**
     * 禁用账号：uid 为用户 id，写入用户级黑名单并吊销其全部会话
     *
     * <p>黑名单会同时挡住 {@code /verify} 与 {@code /upgrade(REFRESH)}，
     * 避免被禁用账号靠旧 refreshToken 继续续期。</p>
     */
    DISABLE,
    /**
     * 启用账号：uid 为用户 id，移出用户级黑名单
     */
    ENABLE
}
