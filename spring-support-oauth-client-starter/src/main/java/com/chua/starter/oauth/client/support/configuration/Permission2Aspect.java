package com.chua.starter.oauth.client.support.configuration;

import com.chua.common.support.annotations.Permission;
import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 权限切面
 *
 * @author CH
 * @since 2022/7/29 8:25
 */
@Aspect
public class Permission2Aspect {

    @Resource
    private AuthClientProperties authProperties;

    /**
     * 环绕
     *
     * @param joinPoint      point
     * @param userPermission 权限
     * @return 结果
     */
    @Around("@annotation(userPermission)")
    public Object permission(ProceedingJoinPoint joinPoint, Permission userPermission) throws Throwable {
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

        if (PermissionAspect.isPass(value, role, userResume)) {
            return joinPoint.proceed();
        }

        throw new AuthenticationException("权限不足");
    }

}
