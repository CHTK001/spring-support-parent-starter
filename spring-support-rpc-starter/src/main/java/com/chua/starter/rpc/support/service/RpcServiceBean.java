package com.chua.starter.rpc.support.service;

import com.chua.common.support.network.rpc.RpcServer;
import com.chua.common.support.network.rpc.RpcType;
import com.chua.starter.rpc.support.properties.RpcProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;

/**
 * rpc服务Bean
 *
 * @版本 1.0.0
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
    /**
     * 创建 rpc服务Bean 实例
     * @param type 类型
     * @param rpcType rpc类型
     * @param rpcProperties rpc属性
     * @param rpcServer rpc服务端
     * @param rpcType rpc类型
     * @param rpcProperties rpc属性
     * @param rpcServer rpc服务端
     */
    public RpcServiceBean(Class<T> type, RpcType rpcType, RpcProperties rpcProperties, RpcServer rpcServer) {
        this.type = type;
        this.rpcType = rpcType;
        this.rpcProperties = rpcProperties;
        this.rpcServer = rpcServer;
    }


    @Override
    /** 之后属性设置 */
    public void afterPropertiesSet() throws Exception {
        /**
    * 导出。
    */
        export();
    }

    /** 导出 */
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

    /**
     * 获取类型
     *
     * @return 获取类型的结果
     */
    public RpcType getType() {
        return rpcType;
    }

    /**
     * 设置类型
     *
     * @param rpcType rpc类型
     */
    public void setType(RpcType rpcType) {
        this.rpcType = rpcType;
    }


}
