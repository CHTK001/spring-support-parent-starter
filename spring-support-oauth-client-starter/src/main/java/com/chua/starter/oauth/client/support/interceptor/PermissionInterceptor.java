package com.chua.starter.oauth.client.support.interceptor;

import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
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
import java.util.Set;

import static com.chua.starter.oauth.client.support.contants.AuthConstant.SUPER_ADMIN;

/**
 * 权限拦截器
 * @author CH
 * @version 1.0.0
 * @since 2024/01/18
 */
@Order(1)
public class PermissionInterceptor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean, Ordered {
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
                Permission permission  = getAnnotation(invocation);
                UserResume userInfo = RequestUtils.getUserInfo(UserResume.class);
                if(null == userInfo) {
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }

                Set<String> roles = userInfo.getRoles();
                if (AuthConstant.isSuperAdmin(roles)) {
                    return invocation.proceed();
                }

                if (AuthConstant.isAdmin(roles)) {
                    if (hasSuperAdminAndMatch(permission)) {
                        throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                    }
                    return invocation.proceed();
                }

                if(!isRoleMatch(permission, roles)) {
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
        return CollectionUtils.containsIgnoreCase(roles, "ADMIN");
    }

    private boolean isPermissionMatch(Permission permission) {
        String[] permissions = permission.value();
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

    private boolean hasSuperAdminAndMatch(Permission permission) {
        String[] role = permission.role();
        if (ArrayUtils.isEmpty(role)) {
            return false;
        }

        return role.length == 1 && SUPER_ADMIN.equalsIgnoreCase(role[0]);
    }
    private boolean isRoleMatch(Permission permission, Set<String> roles) {
        String[] role = permission.role();
        if(ArrayUtils.isEmpty(role)) {
            return true;
        }

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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
