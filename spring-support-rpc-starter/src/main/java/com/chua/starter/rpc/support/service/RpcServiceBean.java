package com.chua.starter.rpc.support.service;

import com.chua.common.support.network.rpc.RpcServer;
import com.chua.common.support.network.rpc.RpcType;
import com.chua.starter.rpc.support.properties.RpcProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;

/**
 * rpc服务bean
 *
 * @version 1.0.0
 * @since 2024/04/08
 * @author CH
 */
@Setter
@Getter
public class RpcServiceBean<T> extends AbstractRpcServiceSetting<T> implements InitializingBean {

    /** 类型 */
    private final Class<T> type;
    /** RPC类型 */
    private RpcType rpcType;
    /** RPC属性 */
    private final RpcProperties rpcProperties;
    /** RPC服务器 */
    private final RpcServer rpcServer;
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
        String interfaceName = super.getInterfaceName();
        if (interfaceName != null && !interfaceName.isEmpty()) {
            return interfaceName;
        }
        Class<?> interfaceClass = super.getInterfaceClass();
        if (interfaceClass != null) {
            return interfaceClass.getName();
        }
        if (type != null) {
            return type.getName();
        }
        return null;
    }

    public RpcType getType() {
        return rpcType;
    }

    public void setType(RpcType rpcType) {
        this.rpcType = rpcType;
    }


}
