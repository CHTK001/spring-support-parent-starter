package com.chua.starter.rpc.support.filter;

import com.chua.rpc.support.annotation.RpcSecret;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.rpc.support.constant.Constant;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.chua.starter.rpc.support.secret.SecretResolver;
import com.google.common.base.Strings;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限检验
 *
 * @author CH
 */
@Slf4j
@Activate(group = {CommonConstants.PROVIDER})
@ConditionalOnProperty(prefix = "plugin.rpc", name = "impl", havingValue = "dubbo", matchIfMissing = false)
public class AuthProviderAuthFilter implements Filter, Constant {

    private final Map<String, MethodValue> methodMap = new ConcurrentHashMap<>();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcProperties rpcProperties = SpringBeanUtils.bindOrCreate(RpcProperties.PRE, RpcProperties.class);

        RpcContext context = RpcContext.getContext();
        String serviceKey = context.getAttachment(SERVICE_KEY);
        if (Strings.isNullOrEmpty(serviceKey) || !serviceKey.equals(rpcProperties.getServiceKey())) {
            return AsyncRpcResult.newDefaultAsyncResult(new IllegalStateException("认证失败"), invocation);
        }

        if (!rpcProperties.isEnable()) {
            return invoker.invoke(invocation);
        }

        String path = invoker.getUrl().getPath();
        String currentPath = path.concat("." + invocation.getMethodName());

        String accessKey = context.getAttachment(ACCESS_KEY);
        String secretKey = context.getAttachment(SECRET_KEY);
        InetSocketAddress remoteAddress = context.getRemoteAddress();

        MethodValue methodValue = methodMap.computeIfAbsent(currentPath, s -> {
            Class<?> aClass = null;
            try {
                aClass = ClassUtils.forName(path, Thread.currentThread().getContextClassLoader());
            } catch (Exception ignored) {
            }
            Method declaredMethod = null;
            try {
                declaredMethod = aClass.getDeclaredMethod(invocation.getMethodName(), invocation.getParameterTypes());
            } catch (NoSuchMethodException e) {
                return null;
            }
            declaredMethod.setAccessible(true);
            return new MethodValue(declaredMethod);
        });

        if (methodValue.isEmpty()) {
            return AsyncRpcResult.newDefaultAsyncResult(new IllegalStateException("方法不存在"), invocation);
        }

        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            log.error("[Rpc] Consumer 获取服务名称失败，停止调用！Path：".concat(currentPath));
            return AsyncRpcResult.newDefaultAsyncResult(new IllegalStateException("认证失败"), invocation);
        }

        if (!methodValue.hasCheck()) {
            return invoker.invoke(invocation);
        }

        boolean isPass = false;
        RpcSecret secret = methodValue.getSecret();
        String beanName = secret.value();

        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if (Strings.isNullOrEmpty(beanName)) {
            Map<String, SecretResolver> beansOfType = applicationContext.getBeansOfType(SecretResolver.class);
            for (SecretResolver secretResolver : beansOfType.values()) {
                boolean resolve = secretResolver.resolve(accessKey, secretKey, remoteAddress);
                if (resolve) {
                    isPass = true;
                    break;
                }
            }
        } else {
            SecretResolver secretResolver = null;
            try {
                secretResolver = applicationContext.getBean(beanName, SecretResolver.class);
            } catch (Throwable ignored) {
            }

            if (null != secretResolver) {
                isPass = secretResolver.resolve(accessKey, secretKey, remoteAddress);
            }
        }

        if (isPass) {
            return invoker.invoke(invocation);
        }

        return AsyncRpcResult.newDefaultAsyncResult(new IllegalStateException("认证失败"), invocation);
    }


    @Data
    private final class MethodValue {

        private final RpcSecret secret;
        private Method method;

        public MethodValue(Method method) {
            this.method = method;
            this.secret = method.getDeclaredAnnotation(RpcSecret.class);
        }

        /**
         * 是否为空
         *
         * @return 是否为空
         */
        public boolean isEmpty() {
            return null == method;
        }

        /***
         * 是否校验
         * @return 是否校验
         */
        public boolean hasCheck() {
            return null != secret;
        }

        /**
         * 获取鉴权
         *
         * @return 鉴权
         */
        public RpcSecret getSecret() {
            return secret;
        }
    }
}
