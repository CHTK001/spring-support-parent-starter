package com.chua.starter.oauth.client.support.interceptor;

import com.chua.common.support.utils.EnumUtils;
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
                if(null != authTypes && isMatch(authTypes, userResume)) {
                    return invocation.proceed();
                }
                String annotationLoginType = tokenForType.value().toUpperCase();
                if ("ALL".equals(annotationLoginType)) {
                    return invocation.proceed();
                }
                String loginType = userResume.getLoginType().toUpperCase();
                if (!annotationLoginType.equals(loginType)) {
                    return null;
                }
                return invocation.proceed();
            }


        });
    }

    private boolean isMatch(AuthType[] authTypes, UserResume userResume) {
        if(EnumUtils.inArray(ALL, authTypes)) {
            return true;
        }


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
