package com.chua.starter.server.support.model.vo;

import java.time.LocalDateTime;

/**
 * 在线远控会话概览
 * <p>
 * 只暴露前端会话列表需要的定位与状态字段，不泄露隧道内部的凭据、地址与缓冲区。
 * {@code recordId} 是审计记录标识，强制关闭会话接口按它寻址。
 * </p>
 *
 * @param tunnelId       隧道标识
 * @param hostId         主机 标识
 * @param recordId       会话审计记录 标识
 * @param protocol       远控协议
 * @param lastActiveTime 最近活跃时间
 * @author CH
 * @since 4.0.0.42
 */
public record TerminalOnlineVO(
        String tunnelId,
        Long hostId,
        Long recordId,
        String protocol,
        LocalDateTime lastActiveTime
) {
}
