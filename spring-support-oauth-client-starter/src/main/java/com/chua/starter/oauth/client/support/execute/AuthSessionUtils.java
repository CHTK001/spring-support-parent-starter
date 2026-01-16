package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.bean.BeanUtils;
import com.chua.starter.oauth.client.support.user.UserResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.chua.starter.common.support.utils.RequestUtils.*;

/**
 * 认证会话工具类
 * <p>提供 Session 相关的静态工具方法</p>
 *
 * @author CH
 * @since 2024/12/11
 */
public final class AuthSessionUtils {

    private AuthSessionUtils() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前请求
     *
     * @return HttpServletRequest，如果不在请求上下文中返回 null
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    /**
     * 获取租户ID
     *
     * @return 租户ID，例如："tenant_001"
     */
    public static String getTenantId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Object attribute = request.getSession().getAttribute(SESSION_TENANT_ID);
        return attribute == null ? null : attribute.toString();
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID，例如："user_001"
     */
    public static String getUserId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Object attribute = request.getSession().getAttribute(SESSION_USERID);
        return attribute == null ? null : attribute.toString();
    }

    /**
     * 获取用户名
     *
     * @return 用户名，例如："zhangsan"
     */
    public static String getUsername() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Object attribute = request.getSession().getAttribute(SESSION_USERNAME);
        return attribute == null ? null : attribute.toString();
    }

    /**
     * 设置用户名
     *
     * @param username 用户名，例如："zhangsan"
     */
    public static void setUsername(String username) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return;
        }
        request.getSession().setAttribute(SESSION_USERNAME, username);
    }

    /**
     * 设置用户信息
     *
     * @param userInfo 用户信息对象
     */
    public static void setUserInfo(Object userInfo) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return;
        }
        request.getSession().setAttribute(SESSION_USER_INFO, userInfo);
    }

    /**
     * 获取用户信息
     *
     * @param target 目标类型，例如：UserResult.class
     * @param <T>    泛型类型
     * @return 用户信息对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T getUserInfo(Class<T> target) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Object attribute = request.getSession().getAttribute(SESSION_USER_INFO);
        if (attribute == null) {
            return null;
        }
        if (target.isAssignableFrom(attribute.getClass())) {
            return (T) attribute;
        }
        return BeanUtils.copyProperties(attribute, target);
    }

    /**
     * 删除用户名
     */
    public static void removeUsername() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return;
        }
        request.getSession().removeAttribute(SESSION_USERNAME);
    }

    /**
     * 删除用户信息
     */
    public static void removeUserInfo() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return;
        }
        request.getSession().removeAttribute(SESSION_USER_INFO);
    }

    /**
     * 清除所有会话信息
     */
    public static void clearSession() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return;
        }
        request.getSession().removeAttribute(SESSION_USER_INFO);
        request.getSession().removeAttribute(SESSION_USERNAME);
        request.getSession().removeAttribute(SESSION_USERID);
        request.getSession().removeAttribute(SESSION_TENANT_ID);
        request.getSession().removeAttribute("userResume");
        request.getSession().removeAttribute("principal");
    }
}
