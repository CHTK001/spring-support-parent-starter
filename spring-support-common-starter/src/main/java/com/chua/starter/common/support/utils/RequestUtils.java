package com.chua.starter.common.support.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求工具类
 * <p>提供从 HttpServletRequest 中提取常用信息的方法</p>
 *
 * @author CH
 * @since 2024-01-01
 */
public class RequestUtils {

    /**
     * 用户ID请求头名称
     */
    private static final String USER_ID_HEADER = "X-User-Id";

    /**
     * 用户名请求头名称
     */
    private static final String USERNAME_HEADER = "X-Username";

    /**
     * 获取当前请求的 HttpServletRequest 对象
     *
     * @return HttpServletRequest，如果无法获取则返回null
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = null;
        try {
            attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return null != attributes ? attributes.getRequest() : null;
        } catch (Exception e) {
            // 忽略异常，返回null
        }
        return null;
    }

    /**
     * 获取当前请求的用户ID
     * <p>优先从请求头 X-User-Id 获取，如果不存在则从请求属性中获取</p>
     *
     * @return 用户ID，如果无法获取则返回null
     */
    public static String getUserId() {
        HttpServletRequest request = getRequest();
        if (null == request) {
            return null;
        }

        // 优先从请求头获取
        String userId = request.getHeader(USER_ID_HEADER);
        if (null != userId && !userId.isEmpty()) {
            return userId;
        }

        // 从请求属性中获取
        Object userIdAttr = request.getAttribute(USER_ID_HEADER);
        if (null != userIdAttr) {
            return userIdAttr.toString();
        }

        // 尝试从其他常见属性名获取
        Object userIdObj = request.getAttribute("userId");
        if (null != userIdObj) {
            return userIdObj.toString();
        }

        return null;
    }

    /**
     * 获取当前请求的用户名
     * <p>优先从请求头 X-Username 获取，如果不存在则从请求属性中获取</p>
     *
     * @return 用户名，如果无法获取则返回null
     */
    public static String getUsername() {
        HttpServletRequest request = getRequest();
        if (null == request) {
            return null;
        }

        // 优先从请求头获取
        String username = request.getHeader(USERNAME_HEADER);
        if (null != username && !username.isEmpty()) {
            return username;
        }

        // 从请求属性中获取
        Object usernameAttr = request.getAttribute(USERNAME_HEADER);
        if (null != usernameAttr) {
            return usernameAttr.toString();
        }

        // 尝试从其他常见属性名获取
        Object usernameObj = request.getAttribute("username");
        if (null != usernameObj) {
            return usernameObj.toString();
        }

        return null;
    }

    /**
     * 获取客户端IP地址
     * <p>优先级：X-Forwarded-For > X-Real-IP > getRemoteAddr</p>
     *
     * @param request HttpServletRequest
     * @return 客户端IP地址
     */
    public static String getIpAddress(HttpServletRequest request) {
        if (null == request) {
            return null;
        }

        // 优先从 X-Forwarded-For 获取（可能包含多个IP，取第一个）
        String ip = request.getHeader("X-Forwarded-For");
        if (null != ip && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个IP，用逗号分隔，取第一个
            int index = ip.indexOf(',');
            if (index > 0) {
                ip = ip.substring(0, index).trim();
            }
            return ip;
        }

        // 从 X-Real-IP 获取
        ip = request.getHeader("X-Real-IP");
        if (null != ip && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // 从 getRemoteAddr 获取
        ip = request.getRemoteAddr();
        if (null != ip && !ip.isEmpty()) {
            return ip;
        }

        return null;
    }

    /**
     * 获取请求URL
     * <p>包含协议、主机、端口、路径和查询参数</p>
     *
     * @param request HttpServletRequest
     * @return 请求URL
     */
    public static String getUrl(HttpServletRequest request) {
        if (null == request) {
            return null;
        }

        StringBuilder url = new StringBuilder();
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();

        url.append(scheme).append("://").append(serverName);
        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        url.append(requestURI);
        if (null != queryString && !queryString.isEmpty()) {
            url.append("?").append(queryString);
        }

        return url.toString();
    }

    /**
     * 获取请求头值
     *
     * @param request HttpServletRequest
     * @param name    请求头名称
     * @return 请求头值，如果不存在则返回null
     */
    public static String getHeader(HttpServletRequest request, String name) {
        if (null == request || null == name || name.isEmpty()) {
            return null;
        }
        return request.getHeader(name);
    }

    /**
     * 判断是否为静态资源请求
     * <p>常见的静态资源扩展名：css, js, jpg, jpeg, png, gif, ico, svg, woff, woff2, ttf, eot, map 等</p>
     *
     * @param requestURI 请求URI
     * @return 如果是静态资源返回true，否则返回false
     */
    public static boolean isResource(String requestURI) {
        if (null == requestURI || requestURI.isEmpty()) {
            return false;
        }

        // 常见的静态资源扩展名
        String[] staticExtensions = {
                ".css", ".js", ".jpg", ".jpeg", ".png", ".gif", ".ico", ".svg",
                ".woff", ".woff2", ".ttf", ".eot", ".map", ".json", ".xml",
                ".txt", ".pdf", ".zip", ".rar", ".mp4", ".mp3", ".avi"
        };

        String lowerURI = requestURI.toLowerCase();
        for (String ext : staticExtensions) {
            if (lowerURI.endsWith(ext)) {
                return true;
            }
        }

        // 检查是否为静态资源路径（如 /static/, /assets/, /resources/ 等）
        String[] staticPaths = {"/static/", "/assets/", "/resources/", "/public/", "/webjars/"};
        for (String path : staticPaths) {
            if (lowerURI.startsWith(path)) {
                return true;
            }
        }

        return false;
    }
}

