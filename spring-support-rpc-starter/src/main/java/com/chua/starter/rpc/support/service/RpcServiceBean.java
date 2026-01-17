package com.chua.starter.rpc.support.service;

import com.chua.common.support.network.rpc.AbstractRpcServiceSetting;
import com.chua.common.support.network.rpc.RpcServer;
import com.chua.common.support.network.rpc.enums.RpcType;
import com.chua.starter.rpc.support.properties.RpcProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;

/**
 * rpc服务bean
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/08
 */
@Setter
@Getter
public class RpcServiceBean<T> extends AbstractRpcServiceSetting<T> implements InitializingBean {

    private final Class<T> type;
    private RpcType rpcType;
    private final RpcProperties rpcProperties;
    private final RpcServer rpcServer;
    private String interfaceName;
    private Class<?> interfaceClass;
    private Object ref;

    public RpcServiceBean(Class<T> type, RpcType rpcType, RpcProperties rpcProperties, RpcServer rpcServer) {
        this.type = type;
        this.rpcType = rpcType;
        this.rpcProperties = rpcProperties;
        this.rpcServer = rpcServer;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        export();
    }

    private void export() {
        rpcServer.register(getInterfaceName(), getRef());
    }

    /**
     * 获取接口名称
     *
     * @return 接口名称
     */
    public String getInterfaceName() {
        if (interfaceName != null) {
            return interfaceName;
        }
        if (interfaceClass != null) {
            return interfaceClass.getName();
        }
        if (type != null) {
            return type.getName();
        }
        return null;
    }

    /**
     * 获取引用对象
     *
     * @return 引用对象
     */
    public Object getRef() {
        return ref;
    }

    public RpcType getType() {
        return rpcType;
    }

    public void setType(RpcType rpcType) {
        this.rpcType = rpcType;
    }


}
