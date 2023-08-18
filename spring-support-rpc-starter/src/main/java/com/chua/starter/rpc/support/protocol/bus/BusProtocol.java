package com.chua.starter.rpc.support.protocol.bus;

import com.chua.starter.remote.support.protocol.AutoCloseProxy;
import com.chua.starter.remote.support.protocol.EnableStartProtocolServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Transporter;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.AbstractProxyProtocol;
import org.apache.dubbo.rpc.protocol.dubbo.DubboCodec;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.remoting.Constants.*;
import static org.apache.dubbo.rpc.Constants.DEFAULT_REMOTING_SERVER;
import static org.apache.dubbo.rpc.Constants.IS_SERVER_KEY;

/**
 * zbus
 *
 * @author CH
 */
@Slf4j
public class BusProtocol extends AbstractProxyProtocol {

    protected final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();
    protected final Map<String, EnableStartProtocolServer> serverMap = new ConcurrentHashMap<>();
    protected final Map<String, AutoCloseProxy> clientMap = new ConcurrentHashMap<>();

    protected final Set<Invoker<?>> invokers = new ConcurrentHashSet<>();

    private final BusExchangeHandler requestHandler = new BusExchangeHandler();

    public static final String NAME = "zbus";

    public static final int DEFAULT_PORT = 5555;

    private final AtomicBoolean destroyed = new AtomicBoolean();


    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    /**
     * 开启服务
     *
     * @param url 地址
     * @return EnableStartProtocolServer
     */
    private EnableStartProtocolServer openServer(URL url) {
        checkDestroyed();
        // find server.
        String key = url.getAddress();
        // client can export a service which only for server to invoke
        boolean isServer = url.getParameter(IS_SERVER_KEY, true);

        if (isServer) {
            EnableStartProtocolServer server = serverMap.get(key);
            if (server == null) {
                synchronized (this) {
                    server = serverMap.get(key);
                    if (server == null) {
                        serverMap.put(key, (server = createServer(url)));
                        return server;
                    }
                }
            }

            // server supports reset, use together with override
            server.reset(url);
        }
        return serverMap.get(key);
    }

    /**
     * 服务
     *
     * @param url 地址
     * @return 协议服务
     */
    private EnableStartProtocolServer createServer(URL url) {
        url = URLBuilder.from(url)
                // send readonly event when server closes, it's enabled by default
                .addParameterIfAbsent(CHANNEL_READONLYEVENT_SENT_KEY, Boolean.TRUE.toString())
                // enable heartbeat by default
                .addParameterIfAbsent(HEARTBEAT_KEY, String.valueOf(DEFAULT_HEARTBEAT))
                .addParameter(CODEC_KEY, DubboCodec.NAME)
                .build();

        String transporter = url.getParameter(SERVER_KEY, DEFAULT_REMOTING_SERVER);
        if (StringUtils.isNotEmpty(transporter) && !url.getOrDefaultFrameworkModel().getExtensionLoader(Transporter.class).hasExtension(transporter)) {
            throw new RpcException("Unsupported server type: " + transporter + ", url: " + url);
        }

        transporter = url.getParameter(CLIENT_KEY);
        if (StringUtils.isNotEmpty(transporter) && !url.getOrDefaultFrameworkModel().getExtensionLoader(Transporter.class).hasExtension(transporter)) {
            throw new RpcException("Unsupported client type: " + transporter);
        }

        EnableStartProtocolServer protocolServer = new BusProtocolServer();
        protocolServer.start(url);
        return protocolServer;
    }


    @Override
    protected <T> Runnable doExport(T impl, Class<T> type, URL url) throws RpcException {
        checkDestroyed();
        // export service.
        String key = serviceKey(url);
        Class implClass = url.getServiceModel().getProxyObject().getClass();
        EnableStartProtocolServer protocolServer = openServer(url);
        optimizeSerialization(url);
        protocolServer.deploy(implClass, url.getServiceModel().getProxyObject(), url);
        return () -> {
            protocolServer.undeploy(implClass);
        };
    }

    @Override
    protected <T> T doRefer(Class<T> type, URL url) throws RpcException {
        checkDestroyed();
        return (T) clientMap.computeIfAbsent(url.getAddress(), it -> new BusProxy<T>(type, url)).doRefer();
    }

    @Override
    public void destroy() {
        for (Invoker<?> invoker : invokers) {
            if (invoker != null) {
                try {
                    if (log.isInfoEnabled()) {
                        log.info("Destroy reference: " + invoker.getUrl());
                    }
                    invoker.destroy();
                } catch (Throwable t) {
                    log.warn(t.getMessage(), t);
                }
            }
        }
        invokers.clear();

        exporterMap.forEach((key, exporter) -> {
            if (exporter != null) {
                try {
                    if (log.isInfoEnabled()) {
                        log.info("Unexport service: " + exporter.getInvoker().getUrl());
                    }
                    exporter.unexport();
                } catch (Throwable t) {
                    log.warn(t.getMessage(), t);
                }
            }
        });
        exporterMap.clear();

        for (Map.Entry<String, EnableStartProtocolServer> entry : serverMap.entrySet()) {
            EnableStartProtocolServer value = entry.getValue();
            value.stop();
        }

        serverMap.clear();

        for (Map.Entry<String, AutoCloseProxy> entry : clientMap.entrySet()) {
            AutoCloseProxy value = entry.getValue();
            try {
                value.close();
            } catch (Exception ignored) {
            }
        }

        clientMap.clear();
    }

    /**
     * 检测是否销毁
     */
    private void checkDestroyed() {
        if (destroyed.get()) {
            throw new IllegalStateException(getClass().getSimpleName() + " is destroyed");
        }
    }
}
