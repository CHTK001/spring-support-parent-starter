package com.chua.starter.rpc.support.filter;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.rpc.support.constant.Constant;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;

/**
 * 权限检验
 *
 * @author CH
 */
@Slf4j
@Activate(group = {CommonConstants.CONSUMER})
@ConditionalOnProperty(prefix = "plugin.rpc", name = "impl", havingValue = "dubbo", matchIfMissing = false)
public class AuthConsumerAuthFilter implements Filter, Constant {

    private final RpcProperties rpcProperties = Binder.get(SpringBeanUtils.getEnvironment()).bindOrCreate(RpcProperties.PRE, RpcProperties.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String serviceKey = rpcProperties.getServiceKey();

        if (Strings.isNullOrEmpty(serviceKey)) {
            return AsyncRpcResult.newDefaultAsyncResult(new IllegalStateException("认证失败"), invocation);
        }
        RpcContext context = RpcContext.getContext();
        context.setAttachment(SERVICE_KEY, serviceKey);

        if (!rpcProperties.isEnable()) {
            return invoker.invoke(invocation);
        }

        String currentPath = invoker.getUrl().getPath().concat("." + invocation.getMethodName());

        String accessKey = rpcProperties.getAccessKey();
        String secretKey = rpcProperties.getSecretKey();
        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            log.error(" [Rpc] Consumer 获取服务名称失败，Rpc Consumer 停止调用！Path：".concat(currentPath));
            return AsyncRpcResult.newDefaultAsyncResult(new IllegalStateException("认证失败"), invocation);
        }
        context.setAttachment(ACCESS_KEY, accessKey);
        context.setAttachment(SECRET_KEY, secretKey);

        return invoker.invoke(invocation);
    }
}
