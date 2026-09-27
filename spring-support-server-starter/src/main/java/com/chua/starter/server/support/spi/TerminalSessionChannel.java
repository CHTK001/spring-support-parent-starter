package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.model.vo.TerminalOnlineVO;

import java.util.List;

/**
 * 远控会话通道 SPI
 * <p>
 * 平台有三条远控通道（guacd 中转、SSH 直连、VNC 直连），各自的会话容器互不相同。
 * 管理端点（在线列表、强制关闭）与空闲清理只关心"有哪些会话、关掉某一条"，
 * 因此把这三件事收敛成本接口：新增通道只需实现本接口、标注 {@code @Spi}，
 * 并在 {@code META-INF/extensions/com.chua.starter.server.support.spi.TerminalSessionChannel}
 * 中登记，治理链路零改动。
 * </p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface TerminalSessionChannel {

    /**
     * 通道标识，仅用于注册中心日志与诊断输出
     *
     * @return 通道名（如 guacd / ssh-direct / vnc-direct）
     */
    String channel();

    /**
     * 本通道当前在线会话快照
     *
     * @return 在线会话列表，无会话时为空列表
     */
    List<TerminalOnlineVO> online();

    /**
     * 按审计记录标识强制关闭本通道的会话
     *
     * @param recordId 会话审计记录 标识
     * @param reason   关闭原因
     * @return true 表示本通道持有该会话并已触发关闭
     */
    boolean closeByRecordId(Long recordId, String reason);

    /**
     * 关闭空闲超时的会话
     *
     * @param idleMinutes 最大空闲分钟数
     * @return 本次关闭的会话数
     */
    int sweepIdle(int idleMinutes);
}
