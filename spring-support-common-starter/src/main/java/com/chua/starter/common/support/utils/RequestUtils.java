// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求工具类
 * <p>提供从 HttpServletRequest 中提取常用信息的方法</p>
 *
 * @since 2024-01-01
 * @author CH
 */
public class RequestUtils {

    /**
     * 私有构造函数，防止实例化
     */
    private RequestUtils() {
    }

    /**
     * 用户标识请求头名称
     */
    private static final String USER_ID_HEADER = "X-User-Id";

    /**
     * 用户名请求头名称
     */
    private static final String USERNAME_HEADER = "X-Username";

    /**
     * 租户标识请求头名称
     */
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    /**
     * 获取当前请求的 httpservlet请求 对象
     *
     * @return HttpServletRequest，如果无法获取则返回null
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = null;
        try {
            attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return null != attributes ? attributes.getRequest() : null;
        } catch (Exception e) {
 // 忽略异常，返回空
        }
        return null;
    }

    /**
     * 获取当前请求的用户标识
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

        // 会话兜底：同 {@link #getUsername()}，取会话里的用户标识。
        HttpSession session = request.getSession(false);
        if (null != session) {
            Object sessionUserId = session.getAttribute("userId");
            if (null != sessionUserId && !sessionUserId.toString().isEmpty()) {
                return sessionUserId.toString();
            }
            String fromResume = readIdentity(session.getAttribute("userResume"), "getUserId");
            if (null != fromResume) {
                return fromResume;
            }
            String fromInfo = readIdentity(session.getAttribute("userInfo"), "getUserId");
            if (null != fromInfo) {
                return fromInfo;
            }
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

        // 会话兜底。
        //
        // <p>oauth-client 把身份写进会话有两条路：{@code AuthFilter#render} 写
        // {@code username}/{@code userId} 字符串，本地管理员兜底与
        // {@code AbstractProtocol} 则只写 {@code userInfo}（{@code UserResume} 对象）。
        // 单体部署常常没有网关写的 {@code X-Username} 头，上面两条就是唯一的身份来源。</p>
        //
        // <p>用 {@code getSession(false)}：读操作不该给无状态请求新建会话。
        // 对象形态用反射取，避免 common-starter 反向依赖 oauth-client。</p>
        HttpSession session = request.getSession(false);
        if (null != session) {
            Object sessionUsername = session.getAttribute("username");
            if (null != sessionUsername && !sessionUsername.toString().isEmpty()) {
                return sessionUsername.toString();
            }
            String fromResume = readIdentity(session.getAttribute("userResume"), "getUsername");
            if (null != fromResume) {
                return fromResume;
            }
            String fromInfo = readIdentity(session.getAttribute("userInfo"), "getUsername");
            if (null != fromInfo) {
                return fromInfo;
            }
        }

        return null;
    }

    /**
     * 反射读取会话里用户对象上的某个身份字段。
     *
     * <p>会话里的用户对象类型在 oauth-client（{@code UserResume}），common-starter 不能
     * 反向依赖它，所以按方法名反射取值；取不到一律返回 {@code null}，不抛异常 ——
     * 少一个身份字段不该影响主流程。</p>
     *
     * @param target 会话属性值，可为 {@code null}
     * @param getter 取值方法名
     * @return 非空字符串或 {@code null}
     */
    private static String readIdentity(Object target, String getter) {
        if (null == target) {
            return null;
        }
        try {
            Object value = target.getClass().getMethod(getter).invoke(target);
            return null != value && !value.toString().isEmpty() ? value.toString() : null;
        } catch (Exception ignored) {
            // 方法不存在 / 不可访问 / 调用失败：按取不到处理
            return null;
        }
    }

    /**
     * 获取当前请求的租户标识
     * <p>优先从请求头 X-Tenant-Id 获取，如果不存在则从请求属性中获取</p>
     *
     * @return 租户ID，如果无法获取则返回null
     */
    public static String getCurrentTenantId() {
        HttpServletRequest request = getRequest();
        if (null == request) {
            return null;
        }

        // 优先从请求头获取
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (null != tenantId && !tenantId.isEmpty()) {
            return tenantId;
        }

        // 从请求属性中获取
        Object tenantIdAttr = request.getAttribute(TENANT_ID_HEADER);
        if (null != tenantIdAttr) {
            return tenantIdAttr.toString();
        }

        // 尝试从其他常见属性名获取
        Object tenantIdObj = request.getAttribute("tenantId");
        if (null != tenantIdObj) {
            return tenantIdObj.toString();
        }

        return null;
    }

    /**
     * 获取客户端IP地址
     * <p>优先级：X-Forwarded-For > X-Real-IP > getRemoteAddr</p>
     *
     * @param request httpservlet请求
     * @return 客户端IP地址
     */
    public static String getIpAddress(HttpServletRequest request) {
        if (null == request) {
            return null;
        }

 // 优先从 X-远期-For 获取（可能包含多个IP，取第一个）
        String ip = request.getHeader("X-Forwarded-For");
        if (null != ip && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
 // X-远期-For 可能包含多个IP，用逗号分隔，取第一个
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

 // 从 获取远程addr 获取
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
     * @param request httpservlet请求
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
     * 获取去掉 context-path 的请求路径（策略匹配用）。
     *
     * <p>限流与接口路由策略都按「应用内路径」匹配，不含 context-path 与查询串：
     * {@code /system/api/v2/user/me?x=1} → {@code /v2/user/me}。这样配置里写
     * {@code /v2/user/me} 就能命中，不需要知道部署时的 context-path。</p>
     *
     * @param request request
     * @return 去掉 context-path 与查询串后的路径，为空返回空串
     */
    public static String getPathWithinApplication(HttpServletRequest request) {
        if (null == request) {
            return "";
        }
        String uri = request.getRequestURI();
        if (null == uri || uri.isEmpty()) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (null != contextPath && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (uri.isEmpty()) {
            return "/";
        }
        return uri.startsWith("/") ? uri : "/" + uri;
    }

    /**
     * 获取请求头值
     *
     * @param request httpservlet请求
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

 // 检查是否为静态资源路径（如 /静态/, /assets/, /resources/ 等）
        String[] staticPaths = {"/static/", "/assets/", "/resources/", "/public/", "/webjars/"};
        for (String path : staticPaths) {
            if (lowerURI.startsWith(path)) {
                return true;
            }
        }

        return false;
    }
}

