package com.chua.starter.server.support.terminal.channel;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.spi.TerminalSessionChannel;
import com.chua.starter.server.support.terminal.handler.SshDirectWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * SSH 直连通道的会话治理适配
 * <p>
 * 直连通道的桥接上下文由处理器自身持有，本类只把 SPI 的三个动作转交给它，
 * 让不经 guacd 的会话同样出现在线列表、可被强制关闭、受空闲清理约束。
 * </p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Spi(value = "ssh-direct", order = 110)
public class SshDirectTerminalSessionChannel implements TerminalSessionChannel {

    /**
     * SSH 直连处理器
     */
    @Autowired
    private SshDirectWebSocketHandler handler;

    @Override
    public String channel() {
        return "ssh-direct";
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
