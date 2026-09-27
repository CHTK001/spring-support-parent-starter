package com.chua.starter.server.support.model.vo;

/**
 * SIP 三种角色的部署命令
 * <p>
 * 由当前生效的 中继地址/控制端口/口令 生成，前端按角色直接展示可复制的启动命令；
 * 键名与历史 Map 载荷完全一致。
 * </p>
 *
 * @param server   中继服务端命令
 * @param provider 内网提供方命令（把本机 RDP/SSH 挂到中继）
 * @param visit    访问端命令（通过中继回连提供方）
 * @author CH
 * @since 4.0.0.42
 */
public record SipDeployCmdsVO(
        String server,
        String provider,
        String visit
) {
}
