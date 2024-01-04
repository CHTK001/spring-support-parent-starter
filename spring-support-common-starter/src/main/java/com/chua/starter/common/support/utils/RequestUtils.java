package com.chua.starter.common.support.utils;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;

/**
 * @author CH
 * @since 2022/7/25 10:16
 */
@Slf4j
public class RequestUtils {


    public static final String SESSION_USERNAME = "x-session-token-username";
    public static final String SESSION_USER_INFO = "x-session-token-userxxx";
    static String LOCAL = null;

    static {
        //根据网卡取本机配置的IP
        InetAddress inet = null;
        try {
            inet = InetAddress.getLocalHost();
        } catch (Exception e) {
            log.error("", e);
        }
        LOCAL = inet.getHostAddress();
    }

    /**
     * 获取客户端IP地址
     *
     * @return 获取客户端IP地址S
     */
    public static String getIpAddress() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return "::";
        }

        return getIpAddress(request);
    }

    /**
     * 获取客户端IP地址
     *
     * @return 获取客户端IP地址S
     */
    public static String getIpAddress(HttpServletRequest request) {
        if (null == request) {
            return "";
        }

        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknow".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
                return LOCAL;
            }
        }
        // 多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割
        if (ip != null && ip.length() > 15) {
            if (ip.indexOf(",") > 0) {
                ip = ip.substring(0, ip.indexOf(","));
            }
        }
        return StringUtils.defaultString(ip, request.getRemoteAddr());


    }

    /**
     * 获取消息头
     *
     * @param request    请求
     * @param headerName 名称
     * @return 结果
     */
    public static String getHeader(HttpServletRequest request, String headerName) {
        return null == request ? null : request.getHeader(headerName);
    }

    /**
     * 请求
     *
     * @return 请求
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attr = null;
        try {
            attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return null != attr ? attr.getRequest() : null;
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 访问地址
     *
     * @param request 请求
     * @return 地址
     */
    public static String getUrl(HttpServletRequest request) {
        if (null == request) {
            return null;
        }

        return request.getRequestURI();
    }

    public static boolean isLocal(String hostAddress) {
        return "127.0.0.1".equals(hostAddress) ||
                "0:0:0:0:0:0:0:1".equals(hostAddress) ||
                "localhost".equals(hostAddress);
    }

    /**
     * 获取用户名
     *
     * @return {@link String}
     */
    public static String getUsername() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        Object attribute = request.getSession().getAttribute(SESSION_USERNAME);
        return null == attribute ? null : attribute.toString();
    }

    /**
     * 设置用户名
     *
     * @param username 用户名
     */
    public static void setUsername(String username) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        request.getSession().setAttribute(SESSION_USERNAME, username);
    }
    /**
     * 设置用户信息
     *
     * @param userInfo 用户名
     */
    public static void setUserInfo(Object userInfo) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        request.getSession().setAttribute(SESSION_USER_INFO, userInfo);
    }
    /**
     * 获取用户信息
     *
     */
    @SuppressWarnings("ALL")
    public static <T>T getUserInfo(Class<T> target) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        Object attribute = request.getSession().getAttribute(SESSION_USER_INFO);
        if(null != attribute || target.isAssignableFrom(attribute.getClass())) {
            return (T) attribute;
        }

        return BeanUtils.copyProperties(attribute, target);
    }

    /**
     * 删除用户名
     */
    public static void removeUsername() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        request.getSession().removeAttribute(SESSION_USER_INFO);
    }

    /**
     * 删除用户信息
     */
    public static void removeUserInfo() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
       request.getSession().removeAttribute(SESSION_USER_INFO);
    }
}
