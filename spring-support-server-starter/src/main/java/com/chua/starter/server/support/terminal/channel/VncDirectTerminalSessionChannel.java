package com.chua.starter.server.support.terminal.channel;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.spi.TerminalSessionChannel;
import com.chua.starter.server.support.terminal.handler.VncWebsockifyWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * VNC 直连通道的会话治理适配
 * <p>
 * 与 {@link SshDirectTerminalSessionChannel} 同构：WS-TCP 字节桥由处理器持有，
 * 浏览器侧 noVNC 讲 RFB，本类只暴露在线列表、强制关闭与空闲扫描三个动作。
 * </p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Spi(value = "vnc-direct", order = 120)
public class VncDirectTerminalSessionChannel implements TerminalSessionChannel {

    /**
     * VNC websockify 桥接处理器
     */
    @Autowired
    private VncWebsockifyWebSocketHandler handler;

    @Override
    public String channel() {
        return "vnc-direct";
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
