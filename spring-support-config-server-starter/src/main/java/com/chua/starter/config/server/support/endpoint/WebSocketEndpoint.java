package com.chua.starter.config.server.support.endpoint;

import com.chua.starter.common.support.websocket.BaseWebSocketEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

/**
 * @author CH
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ServerEndpoint("/websocket/{event}")
public class WebSocketEndpoint extends BaseWebSocketEndpoint {

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "event") String event) throws Exception {
        super.onOpen(session, event);
    }
}
