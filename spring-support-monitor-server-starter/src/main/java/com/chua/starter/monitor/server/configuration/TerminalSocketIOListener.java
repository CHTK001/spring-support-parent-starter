package com.chua.starter.monitor.server.configuration;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.common.support.function.SafeConsumer;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.session.Session;
import com.chua.common.support.session.query.ExecuteQuery;
import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.annotations.OnEvent;
import com.chua.socketio.support.session.SocketSession;
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

        Session session1 = monitorTerminalService.getSession(requestId);
        if(null == session1) {
            session.send("terminal-error", JsonObject.create().fluentPut("message", "当前请求不合法").toJSONString());
            return;
        }

        String command1 = command.getString("command");

        session1.setListener(new SafeConsumer<String>() {
            @Override
            public void safeAccept(String s) throws Throwable {
                session.send("terminal-" + requestId, JsonObject.create().fluentPut("data", s).toJSONString());
            }
        });

        if("connect".equalsIgnoreCase(command1)) {
            try {
                session1.executeQuery("cd /", new ExecuteQuery());
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            session1.executeQuery(command1, new ExecuteQuery());
        } catch (Exception ignored) {
        }
    }
}
