package com.chua.socketio.support.server;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * SocketIOServer
 * @author CH
 */
public class DelegateSocketIOServer extends SocketIOServer implements InitializingBean, DisposableBean {
    public DelegateSocketIOServer(Configuration configuration) {
        super(configuration);
    }

    @Override
    public void destroy() throws Exception {
        this.stop();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.start();
    }
}
