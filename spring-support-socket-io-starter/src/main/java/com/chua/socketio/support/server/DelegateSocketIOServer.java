package com.chua.socketio.support.server;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Socketio服务端
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class DelegateSocketIOServer extends SocketIOServer implements InitializingBean, DisposableBean {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(DelegateSocketIOServer.class);

    /**
     * 创建 delegateSocketio服务端 实例
     * @param configuration 配置
     */
    public DelegateSocketIOServer(Configuration configuration) {
        /**
         * 父。
         * @param configuration 配置
         */
        super(configuration);
    }


    @Override
    /** 销毁 */
    public void destroy() throws Exception {
        this.stop();
    }

    @Override
    /** 之后属性设置 */
    public void afterPropertiesSet() throws Exception {
        Configuration configuration = getConfiguration();
        log.info(">>>> 正在启动 Socket.io服务器; {}:{}", configuration.getHostname(), configuration.getPort());
        this.start();
        log.info(">>>> socket.io is starting");
    }

}
