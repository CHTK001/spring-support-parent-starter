package com.chua.starter.monitor.server.configuration;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.channel.Channel;
import com.chua.common.support.protocol.session.Session;
import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.annotations.OnEvent;
import com.chua.socketio.support.session.SocketSession;
import com.chua.ssh.support.ssh.SshClient;
import com.chua.starter.monitor.server.service.MonitorTerminalService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;


/**
 * 显示器插座定位器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/06
 */
@Configuration
public class TerminalSocketIOListener implements SocketIOListener {

    @Resource
    private MonitorTerminalService monitorTerminalService;
    @OnEvent("terminal")
    public void onEvent(SocketSession session, JsonObject command) {
        String requestId = command.getString("requestId");
        if(StringUtils.isEmpty(requestId)) {
            session.send("terminal-error", JsonObject.create().fluentPut("message", "当前请求不合法").toJSONString());
            return;
        }

        SshClient sshClient = monitorTerminalService.getClient(requestId);
        if(null == sshClient) {
            session.send("terminal-error", JsonObject.create().fluentPut("message", "当前请求不合法").toJSONString());
            return;
        }

        String command1 = command.getString("command");

        Session clientSession = sshClient.createSession(requestId);
        Channel channel = clientSession.openChannel(requestId, "terminal");
        channel.setListener(s ->session.send("terminal-" + requestId, JsonObject.create().fluentPut("data", s).toJSONString()));

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
