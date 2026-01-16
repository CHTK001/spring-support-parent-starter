package com.chua.starter.oauth.client.support.interceptor;

import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.annotation.TokenForType;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.exception.OauthException;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;


/**
 * 令牌类型拦截器
 * @author CH
 * @version 1.0.0
 * @since 2024/01/18
 */
@Order(1)
public class TokenForTypeInterceptor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean, Ordered {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, TokenForType.class) ||
                AnnotatedElementUtils.hasAnnotation(targetClass, TokenForType.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                TokenForType tokenForType = getAnnotation(invocation);
                UserResume userResume = RequestUtils.getUserInfo(UserResume.class);
                if (null == userResume) {
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }

                AuthType[] authTypes = tokenForType.value();
                if(null != authTypes && isAll(authTypes)) {
                    return invocation.proceed();
                }

                String loginType = userResume.getLoginType().toUpperCase();
                if (!isEquals(authTypes, loginType)) {
                    return null;
                }
                return invocation.proceed();
            }


        });
    }

    private boolean isEquals(AuthType[] authTypes, String loginType) {
        if(null == authTypes) {
            return true;
        }
        for (AuthType authType : authTypes) {
            if (authType.name().equalsIgnoreCase(loginType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否匹配所有认证类型
     * <p>空数组表示匹配所有认证类型</p>
     *
     * @param authTypes 认证类型数组
     * @return 是否匹配所有
     */
    private boolean isAll(AuthType[] authTypes) {
        return authTypes == null || authTypes.length == 0;
    }


    /**
     * 获取注解
     * @param invocation 方法调用
     * @return {@link TokenForType}
     */
    private TokenForType getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        TokenForType permission = method.getDeclaredAnnotation(TokenForType.class);
        if (null != permission) {
            return permission;
        }
        return method.getDeclaringClass().getDeclaredAnnotation(TokenForType.class);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
