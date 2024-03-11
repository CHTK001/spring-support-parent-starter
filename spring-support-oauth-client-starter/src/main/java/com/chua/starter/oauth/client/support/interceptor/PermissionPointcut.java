package com.chua.starter.oauth.client.support.interceptor;

import com.chua.common.support.utils.AnnotationUtils;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.exception.OauthException;
import com.chua.starter.oauth.client.support.user.UserResume;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.AnnotatedElementUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

/**
 * @author CH
 * @version 1.0.0
 * @since 2024/01/18
 */
public class PermissionPointcut extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, Permission.class) ||
                AnnotatedElementUtils.hasAnnotation(targetClass, Permission.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                Permission permission  = getAnnotation(invocation);

                if(isAdmin()) {
                    return invocation.proceed();
                }

                if(!isRoleMatch(permission)) {
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }

                if(!isPermissionMatch(permission)) {
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }
                return invocation.proceed();
            }
        });
    }

    private boolean isAdmin() {
        UserResume userInfo = RequestUtils.getUserInfo(UserResume.class);
        if(null == userInfo) {
            return false;
        }
        Set<String> roles = userInfo.getRoles();
        if(CollectionUtils.containsIgnoreCase(roles, "ADMIN")) {
            return true;
        }

        return false;
    }

    private boolean isPermissionMatch(Permission permission) {
        Map<String, Object> as = AnnotationUtils.getAnnotationAttributes(permission);
        String[] permissions = MapUtils.getStringArray(as, "value");
        if(ArrayUtils.isEmpty(permissions)) {
            return true;
        }

        UserResume userInfo = RequestUtils.getUserInfo(UserResume.class);
        if(null == userInfo) {
            return false;
        }

        Set<String> infoPermission = userInfo.getPermission();
        for (String s : infoPermission) {
            if(ArrayUtils.containsIgnoreCase(permissions, s)) {
                return true;
            }
        }

        return false;

    }

    private boolean isRoleMatch(Permission permission) {
        String[] role = permission.role();
        if(ArrayUtils.isEmpty(role)) {
            return true;
        }

        UserResume userInfo = RequestUtils.getUserInfo(UserResume.class);
        if(null == userInfo) {
            return false;
        }

        Set<String> roles = userInfo.getRoles();
        for (String s : roles) {
            if(ArrayUtils.containsIgnoreCase(role, s)) {
                return true;
            }
        }

        return false;

    }

    /**
     * @param invocation
     * @return {@link Permission}
     */
    private Permission getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Permission permission = method.getDeclaredAnnotation(Permission.class);
        if(null != permission) {
            return permission;
        }
        return method.getDeclaringClass().getDeclaredAnnotation(Permission.class);
    }
}
