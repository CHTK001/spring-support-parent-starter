package com.chua.starter.server.support.spi;

/**
 * 主机推送在线态查询契约。
 *
 * <p>server 只关心「这台主机是否有在线推送」，具体监控目标、Mapper 与心跳上下文
 * 由 monitor 适配，server 不反向依赖 monitor。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface HostPushStateQuery {

    /**
     * 判断主机对应的启用监控目标是否在线。
     *
     * @param agentKey 主机探针标识，可为空
     * @param hostId   主机标识，可为空
     * @return 推送在线返回 true
     */
    boolean isPushOnline(String agentKey, Long hostId);
}
