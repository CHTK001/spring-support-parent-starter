package com.chua.starter.server.support.terminal.channel;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.spi.TerminalSessionChannel;
import com.chua.starter.server.support.terminal.telnet.TelnetWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Telnet console 直连通道的会话治理适配
 * <p>
 * 与 {@link SshDirectTerminalSessionChannel} 同构：WS-Telnet 桥接上下文由处理器自身持有，
 * 本类只暴露在线列表、强制关闭与空闲扫描三个动作，
 * 让直连字符终端同样出现在线列表、可被强制关闭、受空闲清理约束。
 * </p>
 *
 * @author CH
 * @since 4.0.0.46
 */
@Spi(value = "telnet-direct", order = 140)
public class TelnetDirectTerminalSessionChannel implements TerminalSessionChannel {

    /**
     * Telnet console 处理器
     */
    @Autowired
    private TelnetWebSocketHandler handler;

    @Override
    public String channel() {
        return "telnet-direct";
    }

    @Override
    public List<TerminalOnlineVO> online() {
        return handler.onlineSessions();
    }

    @Override
    public boolean closeByRecordId(Long recordId, String reason) {
        return handler.closeSession(recordId, reason);
    }

    @Override
    public int sweepIdle(int idleMinutes) {
        return handler.sweepIdleSessions(idleMinutes);
    }
}
