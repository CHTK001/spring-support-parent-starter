package com.chua.starter.common.support.transpond.resolve;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.log.Log;
import com.chua.common.support.net.NetAddress;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.core.streams.Pump;

/**
 * tcp代理
 *
 * @author CH
 */
@Extension("tcp")
public class TcpProxyResolver implements ProxyResolver {

    private static final Log log = Log.getLogger(TcpProxyResolver.class);
    private final Vertx vertx = Vertx.vertx();
    private final ProxyProperties.ProxyConfiguration config;
    private NetServer netServer;

    public TcpProxyResolver(ProxyProperties.ProxyConfiguration config) {
        this.config = config;
        if (null == config) {
            return;
        }
        try {
            this.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public String getName() {
        return null;
    }

    @Override
    public Object getObject() throws Exception {
        return this;
    }


    public void start() throws Exception {
        String targets = config.getTarget();
        NetAddress address = NetAddress.of(targets);

        this.netServer = vertx.createNetServer().connectHandler(socket -> vertx.createNetClient().connect(address.getPort(), address.getHost(), event -> {
            Pump.pump(socket, event.result()).start();
            Pump.pump(event.result(), socket).start();
        }));

        String[] source = config.getSource();
        NetAddress netAddress = NetAddress.of(source[0]);
        netServer.listen(netAddress.getPort(), netAddress.getHost());
        log.info("开启Tcp[{}:{}]代理监听 >>>>>>>>>>>>>>>>>>>", netAddress.getHost(), netAddress.getPort());
    }

    @Override
    public Class<?> getObjectType() {
        return TcpProxyResolver.class;
    }

    @Override
    public void destroy() throws Exception {
        try {
            netServer.close();
        } catch (Exception ignored) {
        }
        vertx.close();
        log.info("Tcp代理关闭成功 >>>>>>>>>>>>>>>>>>>");
    }
}
