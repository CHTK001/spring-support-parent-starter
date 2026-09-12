package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.utils.BeanUtils;
import com.chua.starter.oauth.client.support.user.UserResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 认证会话工具类
 * <p>提供 Session 相关的静态工具方法</p>
 *
 * @since 2024/12/11
 * @author CH
 */
public final class AuthSessionUtils {

    /**
     * 认证会话工具。
     */
    private AuthSessionUtils() {
    }

    /**
      * 会话 中存储用户信息的键
     */
    public static final String SESSION_USER_INFO = "userInfo";

    /**
      * 会话 中存储用户名的键
     */
    public static final String SESSION_USERNAME = "username";

    /**
       * 会话 中存储用户标识的键
     */
    public static final String SESSION_USERID = "userId";

    /**
       * 会话 中存储租户标识的键
     */
    public static final String SESSION_TENANT_ID = "tenantId";

    /**
     * 获取当前请求
     *
     * @return HttpServletRequest，如果不在请求上下文中返回 空
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    /**
      * 获取租户标识
     *
     * @return 租户ID，例如："tenant_001"
     */
    public static String getTenantId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
 // 读取操作不强制创建会话，避免无状态请求产生不必要的 会话
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(SESSION_TENANT_ID);
        return attribute == null ? null : attribute.toString();
    }

    /**
      * 获取用户标识
     *
     * @return 用户ID，例如："user_001"
     */
    public static String getUserId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
 // 读取操作不强制创建会话，避免无状态请求产生不必要的 会话
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(SESSION_USERID);
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
 // 读取操作不强制创建会话，避免无状态请求产生不必要的 会话
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(SESSION_USERNAME);
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
     * @param target 目标类型，例如：用户结果.类
     * @param <T>    泛型类型
     * @return 用户信息对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T getUserInfo(Class<T> target) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
 // 读取操作不强制创建会话，避免无状态请求产生不必要的 会话
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(SESSION_USER_INFO);
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
