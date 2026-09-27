package com.chua.starter.server.support.terminal.channel;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.spi.TerminalSessionChannel;
import com.chua.starter.server.support.terminal.GuacdSession;
import com.chua.starter.server.support.terminal.GuacdSessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * guacd 中转通道的会话治理适配
 * <p>
 * 把 {@link GuacdSessionRegistry} 的隧道视角翻译成 SPI 的会话视角，
 * 使管理端无需了解 guacd 的 tunnelid / 发送锁等内部结构。
 * </p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Spi(value = "guacd", order = 100)
public class GuacdTerminalSessionChannel implements TerminalSessionChannel {

    /**
     * guacd 会话注册表
     */
    @Autowired
    private GuacdSessionRegistry sessionRegistry;

    @Override
    public String channel() {
        return "guacd";
    }

    @Override
    public List<TerminalOnlineVO> online() {
        List<GuacdSession> sessions = sessionRegistry.snapshot();
        List<TerminalOnlineVO> result = new ArrayList<>(sessions.size());
        for (GuacdSession session : sessions) {
            result.add(new TerminalOnlineVO(session.getTunnelId(), session.getHostId(),
                    session.getSessionId(), session.getProtocol().toUpperCase(Locale.ROOT),
                    session.getLastActiveTime()));
        }
        return result;
    }

    @Override
    public boolean closeByRecordId(Long recordId, String reason) {
        return sessionRegistry.closeByRecordId(recordId, reason);
    }

    @Override
    public int sweepIdle(int idleMinutes) {
        return sessionRegistry.sweepIdle(idleMinutes);
    }
}
