package com.chua.starter.server.support.model.vo;

/**
 * 远控短令牌签发结果
 * <p>
 * 令牌与会话地址——目标机地址、端口、凭据只留在后端令牌登记表，不进入本响应。
 * 字段名与历史 Map 载荷完全一致。
 * </p>
 *
 * @param token    随机短令牌，一次性
 * @param url      会话页地址，未配置 web 前端时为 {@code null}
 * @param hostName 主机名称，仅用于前端标题展示
 * @param protocol 协议（rdp/vnc/ssh/telnet）
 * @author CH
 * @since 4.0.0.42
 */
public record RemoteTokenVO(
        String token,
        String url,
        String hostName,
        String protocol
) {
}
