package com.chua.starter.rpc.support.service;

import com.chua.common.support.network.rpc.RpcServer;
import com.chua.common.support.network.rpc.RpcType;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.rpc.support.spring.SpringBeanRpcServer;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;

/**
 * rpc服务Bean
 *
 * @since 2024/04/08
 * @author CH
 */
@Setter
@Getter
public class RpcServiceBean<T> extends AbstractRpcServiceSetting<T> implements InitializingBean {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(RpcServiceBean.class);

    /**
     * 类型
     */
    private final Class<T> type;

    /**
     * RPC类型
     */
    private RpcType rpcType;

    /**
     * RPC属性
     */
    private final RpcProperties rpcProperties;

    /**
     * RPC服务器
     */
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
    /**
     * 之后属性设置
     * @return 返回值
     * @throws Exception 异常
    */
    public void afterPropertiesSet() throws Exception {
        /**
         * 导出。
         */
        export();
    }

    /**
     * 导出
     *
     * <p>注册到 {@link #rpcServer}（远端协议）；当远端注册失败时，按 {@code degrade}
     * 暴露为本地的 Bean，使同应用上下文内的消费端可以降级直调。</p>
     */
    private void export() {
        try {
            rpcServer.register(getInterfaceName(), getRef());
        } catch (RuntimeException e) {
            // 远端不可用时暴露本地 Bean（degrade provider）
            handleExportFailure(e);
            // exception=true（默认）时仍然抛出，exception=false 时静默保留本地 Bean
            if (isException()) {
                throw e;
            }
        }
    }

    /**
     * 处理远端导出失败，按 {@code degrade} 注册本地 Bean
     *
     * @param e 远端导出异常
     */
    private void handleExportFailure(RuntimeException e) {
        if (!isDegradeEnabled()) {
            log.warn("[RPC] 远端导出失败，未配置 degrade: {}", e.getMessage(), e);
            return;
        }
        try {
            registerLocalBean();
        } catch (Throwable ignore) {
            log.debug("[RPC] 本地 Bean 注册失败: {}", e.getMessage(), ignore);
        }
    }

    /**
     * 是否允许 degrade 提供者
     *
     * @return 是否
     */
    private boolean isDegradeEnabled() {
        String degrade = getDegrade();
        return degrade == null || "springbean".equalsIgnoreCase(degrade);
    }

    /**
     * 将服务注册为 Application Context 内的本地 Bean
     */
    private void registerLocalBean() {
        try {
            SpringBeanRpcServer server = new SpringBeanRpcServer();
            ApplicationContext context = SpringBeanUtils.getApplicationContext();
            if (context != null) {
                server.setApplicationContext(context);
            }
            server.register(getInterfaceName(), getRef());
        } catch (Exception e) {
            log.debug("[RPC] 本地 Bean 注册失败: {}", e.getMessage(), e);
        }
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
