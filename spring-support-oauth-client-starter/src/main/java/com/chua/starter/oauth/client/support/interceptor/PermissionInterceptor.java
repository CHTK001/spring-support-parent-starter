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
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;

import static com.chua.starter.oauth.client.support.contants.AuthConstant.SUPER_ADMIN;

/**
 * 权限拦截器
 * @author CH
 * @version 1.0.0
 * @since 2024/01/18
 */
@Slf4j
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
        log.info("[权限拦截器]初始化权限拦截器");
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
                
                log.debug("[权限拦截器]========== 开始权限检查 ==========");
                log.debug("[权限拦截器]检查方法: {}", methodName);
                
                Permission permission = getAnnotation(invocation);
                log.debug("[权限拦截器]权限注解 - 要求角色: {}, 要求权限: {}", 
                         Arrays.toString(permission.role()), 
                         Arrays.toString(permission.value()));
                
                UserResume userInfo = RequestUtils.getUserInfo(UserResume.class);
                if(null == userInfo) {
                    log.warn("[权限拦截器]用户信息为空，拒绝访问 - 方法: {}", methodName);
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }
                
                log.debug("[权限拦截器]当前用户 - 用户名: {}, 用户ID: {}", 
                         userInfo.getUsername(), userInfo.getUserId());

                Set<String> roles = userInfo.getRoles();
                log.debug("[权限拦截器]用户角色: {}", roles);
                
                // 超级管理员检查
                if (AuthConstant.isSuperAdmin(roles)) {
                    log.info("[权限拦截器]超级管理员放行 - 用户: {}, 方法: {}", userInfo.getUsername(), methodName);
                    return invocation.proceed();
                }

                // 普通管理员检查
                if (AuthConstant.isAdmin(roles)) {
                    if (hasSuperAdminAndMatch(permission)) {
                        log.warn("[权限拦截器]管理员权限不足(需要超级管理员) - 用户: {}, 方法: {}", 
                                userInfo.getUsername(), methodName);
                        throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                    }
                    log.info("[权限拦截器]管理员放行 - 用户: {}, 方法: {}", userInfo.getUsername(), methodName);
                    return invocation.proceed();
                }

                // 角色检查
                if(!isRoleMatch(permission, roles)) {
                    log.warn("[权限拦截器]角色检查失败 - 用户: {}, 用户角色: {}, 要求角色: {}, 方法: {}", 
                            userInfo.getUsername(), roles, Arrays.toString(permission.role()), methodName);
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }
                log.debug("[权限拦截器]角色检查通过 - 用户: {}", userInfo.getUsername());

                // 权限检查
                if(!isPermissionMatch(permission)) {
                    log.warn("[权限拦截器]权限检查失败 - 用户: {}, 用户权限: {}, 要求权限: {}, 方法: {}", 
                            userInfo.getUsername(), userInfo.getPermission(), 
                            Arrays.toString(permission.value()), methodName);
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }
                log.debug("[权限拦截器]权限检查通过 - 用户: {}", userInfo.getUsername());
                
                log.info("[权限拦截器]权限验证通过 - 用户: {}, 方法: {}", userInfo.getUsername(), methodName);
                log.debug("[权限拦截器]========== 权限检查完成 ==========");
                
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
