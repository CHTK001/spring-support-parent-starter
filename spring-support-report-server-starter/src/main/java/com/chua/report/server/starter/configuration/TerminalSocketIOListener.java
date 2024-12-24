package com.chua.report.server.starter.configuration;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.channel.Channel;
import com.chua.common.support.protocol.session.ChannelSession;
import com.chua.report.server.starter.service.TerminalService;
import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.annotations.OnEvent;
import com.chua.socketio.support.session.SocketSession;
import com.chua.ssh.support.ssh.SshClient;
import lombok.RequiredArgsConstructor;


/**
 * 显示器插座定位器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/06
 */
@RequiredArgsConstructor
public class TerminalSocketIOListener implements SocketIOListener {

    final TerminalService terminalService;
    @OnEvent("terminal")
    public void onEvent(SocketSession session, JsonObject command) {
        String genId = command.getString("genId");
        if(StringUtils.isEmpty(genId)) {
            session.send("terminal-error", JsonObject.create().fluentPut("message", "当前请求不合法").toJSONString());
            return;
        }

        SshClient sshClient = null;
        try {
            sshClient = terminalService.getClient(genId);
        } catch (Exception e) {
            session.send("terminal-" + genId,  ReturnResult.illegal(e.getMessage()));
            return;
        }
        if(null == sshClient) {
            session.send("terminal-" + genId,
                    ReturnResult.illegal("当前请求不合法"));
            return;
        }

        String command1 = command.getString("command");

        ChannelSession clientSession = (ChannelSession) sshClient.getSession();
        Channel channel = clientSession.openChannel(genId, "terminal");
//        clientSession.closeChannel(requestId);
        channel.setListener(s -> {
            session.send("terminal-" + genId,
                    ReturnResult.success(s));
        });

        if("connect".equalsIgnoreCase(command1)) {
            try {
                channel.execute("cd /", 1000);
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            channel.execute(command1, 1000);
        } catch (Exception ignored) {
        }
    }
}
