package com.chua.starter.oauth.client.support.configuration;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.oauth.client.support.annotation.UserPermission;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * 权限切面
 *
 * @author CH
 * @since 2022/7/29 8:25
 */
@Aspect
public class PermissionAspect {

    protected static final String ANY = "*";
    protected static final String ONE = "?";
    @Resource
    private AuthClientProperties authProperties;

    protected static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 环绕
     *
     * @param joinPoint      point
     * @param userPermission 权限
     * @return 结果
     */
    @Around("@annotation(userPermission)")
    public Object permission(ProceedingJoinPoint joinPoint, UserPermission userPermission) throws Throwable {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        WebRequest webRequest = new WebRequest(authProperties, request, null);

        AuthenticationInformation authentication = webRequest.authentication();
        UserResume userResume = authentication.getReturnResult();

        String[] value = userPermission.value();
        String[] role = userPermission.role();

        if(isPass(value, role, userResume)) {
            return joinPoint.proceed();
        }

        return ReturnResult.noAuth();
    }

    protected static boolean isPass(String[] value, String[] role, UserResume userResume) {
        if (userResume.isAdmin()) {
            return true;
        }
        return userResume.hasRole(role) && userResume.hasPermission(value);
    }


    /**
     * 权限鉴定
     *
     * @param value      值
     * @param permission 权限
     * @return 鉴定结果
     */
    protected boolean hasPermissions(String[] value, Set<String> permission) {
        if (permission == null) {
            return false;
        }

        for (String s : value) {
            if (hasPermission(s, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 权限鉴定
     *
     * @param value      值
     * @param permission 权限
     * @return 鉴定结果
     */
    private boolean hasPermission(String value, Set<String> permission) {
        if (value.contains(ANY) || value.contains(ONE)) {
            for (String s : permission) {
                if (PATH_MATCHER.match(value, s)) {
                    return true;
                }
            }
            return false;
        }

        return permission.contains(value);
    }
}
