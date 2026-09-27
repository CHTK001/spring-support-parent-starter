package com.chua.starter.server.support.model.vo;

/**
 * 当前可用代理
 * <p>
 * 表内第一条可用代理；无可用代理时 {@code proxy} 为空串（保持历史 Map 载荷的键与语义）。
 * </p>
 *
 * @param proxy 代理地址，形如 {@code host:port}，无代理时为空串
 * @author CH
 * @since 4.0.0.42
 */
public record ProxyCurrentVO(
        String proxy
) {
}
