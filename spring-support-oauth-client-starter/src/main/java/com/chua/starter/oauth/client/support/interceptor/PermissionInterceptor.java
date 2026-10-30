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
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;

import static com.chua.starter.oauth.client.support.contants.AuthConstant.SUPER_ADMIN;

/**
 * 权限拦截器
 * @since 2024/01/18
 * @author CH
 */
@Slf4j
@Order(1)
public class PermissionInterceptor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean, Ordered {

    /**
     * 只读角色：拥有该角色的账号禁止写操作（优先于所有权限，含超级管理员）
    */
    private static final String READ_ONLY_ROLE = "READ_ONLY";

    /**
     * 安全方法（只读操作放行）
    */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    /**
     * Matches
     * @param method 参数
     * @param targetClass 参数
     * @return 返回值
    */
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
 // 跳过 @令牌forignore 标注的方法（登录、登出、令牌 刷新等免鉴权场景）
        if (AnnotatedElementUtils.hasAnnotation(method, com.chua.starter.oauth.client.support.annotation.TokenForIgnore.class)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, Permission.class) ||
                AnnotatedElementUtils.hasAnnotation(targetClass, Permission.class);
    }

    @Override
    /**
     * 之后属性设置
     * @return 返回值
     * @throws Exception 异常
    */
    public void afterPropertiesSet() throws Exception {
        log.info("[权限拦截器]初始化权限拦截器");
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            /**
             * 调用
             * @param invocation 参数
             * @return 返回值
             * @throws Throwable 异常
            */
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
                
                log.debug("[权限拦截器]========== 开始权限检查 ==========");
                log.debug("[权限拦截器]检查方法: {}", methodName);
                
                Permission permission = getAnnotation(invocation);
                log.debug("[权限拦截器]权限注解 - 要求角色: {}, 要求权限: {}", 
                         Arrays.toString(permission.role()), 
                         Arrays.toString(permission.value()));
                
                UserResume userInfo = com.chua.starter.oauth.client.support.execute.AuthSessionUtils.getUserInfo(UserResume.class);
                if (null == userInfo) {
                    if (isLocalAdminFallback()) {
 // 本地联调模式（无远程 o认证 服务）显式开启兜底：注入 admin 角色以放行 @权限 拦截。
                        log.warn("[权限拦截器]已开启本地 admin 兜底，注入 admin 上下文 - 方法: {}", methodName);
                        userInfo = new UserResume();
                        userInfo.setUserId("1");
                        userInfo.setUsername("admin");
                        userInfo.setUid("1");
                        userInfo.setRoles(com.google.common.collect.Sets.newHashSet(
                                com.chua.starter.oauth.client.support.contants.AuthConstant.SUPER_ADMIN,
                                com.chua.starter.common.support.constant.Constant.ADMIN));
                        // 权限集合必须一并授予：@Permission 的 value（权限名）与 role（角色名）是两套独立判定，
                        // 只授角色时 isRoleMatch 通过但 isPermissionMatch 必然失败，@Permission("admin") 仍会被拒。
                        userInfo.setPermission(com.google.common.collect.Sets.newHashSet(
                                com.chua.starter.common.support.constant.Constant.ADMIN));
                        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUserInfo(userInfo);
                    } else if (currentRequest() != null) {
 // HTTP 请求无用户上下文即未登录，不得伪造身份放行
                        log.warn("[权限拦截器]无用户上下文且未开启本地兜底，拒绝访问 - 方法: {}", methodName);
                        /**
                         * OAuth异常。
                         * @param "登录状态已失效，请重新登录" "登录状态已失效，请重新登录"
                         * @return oauth异常的结果
                         */
                        throw new OauthException("登录状态已失效，请重新登录", 401);
                    }
                    // 非 HTTP 调用（定时任务、异步线程）无请求上下文，按原样放行且不带身份
                }
                
                log.debug("[权限拦截器]当前用户 - 用户名: {}, 用户ID: {}", 
                         userInfo.getUsername(), userInfo.getUserId());

                Set<String> roles = userInfo.getRoles();
                log.debug("[权限拦截器]用户角色: {}", roles);

 // 只读账号优先于所有权限：账号级 readonly（o认证 服务端数据库返回）或
 // 读取_ONLY 角色（兼容兜底）任一命中即禁止写操作（POST/放入/删除/PATCH）；
                // 置于超级管理员检查之前，对超管同样生效；无请求上下文时不阻断（非 HTTP 调用）
                if (userInfo.isReadonly() || CollectionUtils.containsIgnoreCase(roles, READ_ONLY_ROLE)) {
                    HttpServletRequest request = currentRequest();
                    if (request != null && !isSafeMethod(request.getMethod())) {
                        log.warn("[权限拦截器]只读角色禁止写操作 - 用户: {}, 方法: {}", userInfo.getUsername(), methodName);
                        /**
                         * OAuth异常。
                         * @param "只读角色不允许执行写操作!" "只读角色不允许执行写操作!"
                         * @return oauth异常的结果
                         */
                        throw new OauthException("只读角色不允许执行写操作!");
                    }
                }

                // 特权放行：内置 admin 账号（全部权限+菜单，与角色授权无关）或持有超管角色；
                // 仅被授予 ADMIN 角色的账号不再全量放行，落到下面的角色/权限匹配，受该角色授权控制
                if (AuthConstant.isPrivileged(userInfo.getUsername(), roles)) {
                    log.info("[权限拦截器]特权账号放行 - 用户: {}, 方法: {}", userInfo.getUsername(), methodName);
                    return invocation.proceed();
                }

                // 角色检查
                if(!isRoleMatch(permission, roles)) {
                    log.warn("[权限拦截器]角色检查失败 - 用户: {}, 用户角色: {}, 要求角色: {}, 方法: {}", 
                            userInfo.getUsername(), roles, Arrays.toString(permission.role()), methodName);
                    /**
                     * OAuth异常。
                     * @param "您的账号没有权限 "您的账号没有权限
                     * @param 请联系管理员分配!" 请联系管理员分配!"
                     * @return oauth异常的结果
                     */
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }
                log.debug("[权限拦截器]角色检查通过 - 用户: {}", userInfo.getUsername());

                // 权限检查
                if(!isPermissionMatch(permission)) {
                    log.warn("[权限拦截器]权限检查失败 - 用户: {}, 用户权限: {}, 要求权限: {}, 方法: {}", 
                            userInfo.getUsername(), userInfo.getPermission(), 
                            Arrays.toString(permission.value()), methodName);
                    /**
                     * OAuth异常。
                     * @param "您的账号没有权限 "您的账号没有权限
                     * @param 请联系管理员分配!" 请联系管理员分配!"
                     * @return oauth异常的结果
                     */
                    throw new OauthException("您的账号没有权限, 请联系管理员分配!");
                }
                log.debug("[权限拦截器]权限检查通过 - 用户: {}", userInfo.getUsername());
                
                log.info("[权限拦截器]权限验证通过 - 用户: {}, 方法: {}", userInfo.getUsername(), methodName);
                log.debug("[权限拦截器]========== 权限检查完成 ==========");
                
                return invocation.proceed();
            }


        });
    }

    /**
     * 是否开启「无用户上下文即本地 admin」兜底
     *
     * @return 是否开启兜底的结果
     */
    private boolean isLocalAdminFallback() {
        com.chua.starter.oauth.client.support.properties.AuthClientProperties properties =
                com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext.getAuthClientProperties();
        return properties != null && properties.isLocalAdminFallback();
    }

    /**
     * 是否权限匹配
     *
     * @param permission 权限
     * @return 是否权限匹配的结果
     */
    private boolean isPermissionMatch(Permission permission) {
        String[] permissions = permission.value();
        if(ArrayUtils.isEmpty(permissions)) {
            return true;
        }

        UserResume userInfo = com.chua.starter.oauth.client.support.execute.AuthSessionUtils.getUserInfo(UserResume.class);
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

    /**
     * 是否角色匹配
     *
     * @param permission 权限
     * @param roles 角色
     * @return 是否角色匹配的结果
     */
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
     * @return {@link 权限}
     */
    private Permission getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Permission permission = method.getDeclaredAnnotation(Permission.class);
        if(null != permission) {
            return permission;
        }
        return method.getDeclaringClass().getDeclaredAnnotation(Permission.class);
    }

    /**
     * 获取当前 HTTP 请求；无请求上下文（非 HTTP 调用）返回 空
     *
     * @return 当前请求的结果
     */
    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes == null ? null : attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 是否安全方法（只读操作）
     *
     * @param method 方法
     * @return 是否safe方法的结果
     */
    private static boolean isSafeMethod(String method) {
        return method != null && SAFE_METHODS.contains(method.toUpperCase());
    }

    @Override
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
