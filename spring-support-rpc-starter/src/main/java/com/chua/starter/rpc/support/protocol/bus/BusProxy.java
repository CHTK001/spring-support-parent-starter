package com.chua.starter.rpc.support.protocol.bus;

import com.boren.starter.common.support.application.Binder;
import com.chua.starter.remote.support.properties.RemoteProperties;
import com.chua.starter.remote.support.protocol.AutoCloseProxy;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.serialize.Serialization;
import org.springframework.aop.framework.ProxyFactory;
import org.zbus.broker.Broker;
import org.zbus.broker.ZbusBroker;
import org.zbus.rpc.RpcCodec;
import org.zbus.rpc.RpcConfig;
import org.zbus.rpc.RpcInvoker;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * 代理
 *
 * @author CH
 */
public class BusProxy<T> implements AutoCloseProxy<T> {
    private final Class<T> type;
    private final URL url;
    private final T refer;
    private final RpcInvoker rpcInvoker;
    private final RemoteProperties remoteProperties;
    Broker broker = null;
    private final BusProtocolServer.RpcNewProcessor rpcProcessor = new BusProtocolServer.RpcNewProcessor();

    public BusProxy(Class<T> type, URL url) {
        this.type = type;
        this.url = url;
        try {
            broker = new ZbusBroker(url.getAddress());
        } catch (IOException ignored) {
        }
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setInterfaces(type);
        proxyFactory.addAdvice((MethodInterceptor) invocation -> BusProxy.this.invoke(invocation.getThis(), invocation.getMethod(), invocation.getArguments(), null));
        this.refer = (T) proxyFactory.getProxy();
        RpcConfig rpcConfig = new RpcConfig();
        ExtensionLoader<Serialization> extensionLoader = ExtensionLoader.getExtensionLoader(Serialization.class);
        Serialization serialization = extensionLoader.getExtension(url.getParameter("serialization", "hessian2"));

        rpcInvoker = new RpcInvoker(broker, new BusProtocolServer.BusRpcCodec(serialization), rpcConfig);
        this.remoteProperties = Binder.binder(RemoteProperties.PRE, RemoteProperties.class);
    }

    @Override
    public T doRefer() {
        return refer;
    }

    public Object invoke(Object obj, Method method, Object[] args, T proxy) throws Throwable {
        RpcCodec.Request request = new RpcCodec.Request();
        request.setMethod(method.getName());
        request.setModule(method.getDeclaringClass().getName());
        request.setParams(args);
        RpcCodec.Response response = rpcInvoker.invokeSync(request);
        return response.getResult();
    }

    @Override
    public void close() throws Exception {
        if (null == broker) {
            return;
        }
        broker.close();
    }
}
