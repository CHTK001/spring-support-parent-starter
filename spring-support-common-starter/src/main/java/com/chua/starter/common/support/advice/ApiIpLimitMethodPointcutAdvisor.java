package com.chua.starter.common.support.advice;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.net.NetUtils;
import com.chua.starter.common.support.annotations.ApiIpLimit;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * api ip限制
 *
 * @author CH
 */
public class ApiIpLimitMethodPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor {

    public ApiIpLimitMethodPointcutAdvisor() {
        this.setAdvice(new MethodInvocationImpl());
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, ApiIpLimit.class) || AnnotatedElementUtils.hasAnnotation(targetClass, ApiIpLimit.class);
    }

    static final class MethodInvocationImpl implements MethodInterceptor {
        @Nullable
        @Override
        public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
            String ipAddress = RequestUtils.getIpAddress();
            if (StringUtils.isBlank(ipAddress)) {
                return invocation.proceed();
            }

            Method method = invocation.getMethod();
            if (AnnotatedElementUtils.hasAnnotation(method, ApiIpLimit.class)) {
                ApiIpLimit apiIpLimit = method.getDeclaredAnnotation(ApiIpLimit.class);
                if (null != apiIpLimit && !isMatch(apiIpLimit, ipAddress)) {
                    throw new RuntimeException(apiIpLimit.message());
                }
            }

            ApiIpLimit apiIpLimit = invocation.getThis().getClass().getDeclaredAnnotation(ApiIpLimit.class);
            if (null != apiIpLimit && !isMatch(apiIpLimit, ipAddress)) {
                throw new RuntimeException(apiIpLimit.message());
            }

            return invocation.proceed();
        }

        /**
         * 是否匹配
         *
         * @param apiIpLimit apiIpLimit
         * @param ipAddress  ipAddress
         * @return 是否匹配
         */
        private boolean isMatch(ApiIpLimit apiIpLimit, String ipAddress) {
            ApiIpLimit.IpType type = apiIpLimit.type();
            if (type == ApiIpLimit.IpType.PRIVATE) {
                return NetUtils.isLocalHost(ipAddress);
            }

            String[] value = apiIpLimit.value();
            for (String s : value) {
                if (s.contains(CommonConstant.SYMBOL_ASTERISK) && PathMatcher.INSTANCE.match(s, ipAddress)) {
                    return true;
                }

                if (s.equals(ipAddress)) {
                    return true;
                }
            }
            return false;
        }
    }
}
