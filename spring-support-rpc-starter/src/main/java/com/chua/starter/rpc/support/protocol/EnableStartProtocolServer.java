package com.chua.starter.rpc.support.protocol;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.ProtocolServer;

/**
 * 可以启动的服务
 *
 * @author CH
 */
public interface EnableStartProtocolServer extends ProtocolServer {
    /**
     * 启动
     *
     * @param url 地址
     */
    void start(URL url);

    /**
     * 注册类型
     *
     * @param resourceDef      it could be either resource interface or resource impl
     * @param resourceInstance resource
     * @param contextPath      path
     */
    void deploy(Class resourceDef, Object resourceInstance, URL contextPath);

    /**
     * 注销类
     *
     * @param resourceDef 类
     */
    void undeploy(Class resourceDef);

    /**
     * 关闭
     */
    void stop();
}
