package com.chua.socketio.support.server;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * SocketIOServer
 *
 * @author CH
 */
@Slf4j
public class DelegateSocketIOServer extends SocketIOServer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DelegateSocketIOServer.class);

    public DelegateSocketIOServer(Configuration configuration) {
        super(configuration);
    }


    @Override
    public void destroy() throws Exception {
        this.stop();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Configuration configuration = getConfiguration();
        log.info(">>>> 正在启动 Socket.io服务器; {}:{}", configuration.getHostname(), configuration.getPort());
        this.start();
        log.info(">>>> socket.io is starting");
    }

}
