package com.chua.starter.cdp.proxy;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时页面调试会话
 *
 * <p>一个会话对应一个被调试的上游站点：持有当前上游地址（相对地址的解析基准）、
 * 服务端 Cookie 罐与关联的 CDP 页面目标。无头浏览器与用户浏览器（iframe）都经由
 * 同一会话访问上游，因此共享这份 Cookie 罐——登录态在两边同时有效。</p>
 *
 * <p>会话不持锁：并发请求只做 {@code volatile} 写与 {@link ConcurrentHashMap} 操作。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public class ProxySession {

    /**
     * 会话标识（同时是代理路径的第一段）
     */
    private final String id;

    /**
     * 初始上游地址
     */
    private final String initialUrl;

    /**
     * 当前上游地址；随每次代理请求更新，作为空路径 / 相对地址的解析基准
     */
    private volatile String currentUrl;

    /**
     * 关联的 CDP 页面目标标识（无头浏览器经由本代理打开同一地址）
     */
    private volatile String targetId;

    /**
     * 创建时间（毫秒）
     */
    private final long createdAt = System.currentTimeMillis();

    /**
     * 最近访问时间（毫秒），TTL 判定依据
     */
    private volatile long lastAccess = createdAt;

    /**
     * Cookie 罐：key = name|domain|path
     */
    private final Map<String, StoredCookie> cookies = new ConcurrentHashMap<>();

    /**
     * 创建会话
     *
     * @param id         会话标识
     * @param initialUrl 初始上游地址
     */
    public ProxySession(String id, String initialUrl) {
        this.id = id;
        this.initialUrl = initialUrl;
        this.currentUrl = initialUrl;
    }

    public String getId() {
        return id;
    }

    public String getInitialUrl() {
        return initialUrl;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String currentUrl) {
        this.currentUrl = currentUrl;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastAccess() {
        return lastAccess;
    }

    /**
     * 刷新最近访问时间
     */
    public void touch() {
        lastAccess = System.currentTimeMillis();
    }

    /**
     * 会话是否已空闲超时
     *
     * @param ttlMillis 空闲 TTL（毫秒）
     * @return true 表示已超时
     */
    public boolean isExpired(long ttlMillis) {
        return System.currentTimeMillis() - lastAccess > ttlMillis;
    }

    /**
     * 记录上游 Set-Cookie
     *
     * @param setCookie   上游 Set-Cookie 头原值
     * @param requestHost 发起请求的上游主机（Domain 属性缺省时归属它）
     */
    public void storeCookie(String setCookie, String requestHost) {
        if (setCookie == null || setCookie.isBlank()) {
            return;
        }
        String[] parts = setCookie.split(";");
        String first = parts[0];
        int eq = first.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String name = first.substring(0, eq).trim();
        String value = first.substring(eq + 1).trim();
        String host = requestHost == null ? "" : requestHost.toLowerCase();
        String domain = host;
        String path = "/";
        long expiresAt = 0L;
        boolean expired = false;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            int p = part.indexOf('=');
            String key = p > 0 ? part.substring(0, p).trim().toLowerCase() : part.toLowerCase();
            String val = p > 0 ? part.substring(p + 1).trim() : "";
            switch (key) {
                case "domain" -> {
                    if (!val.isBlank()) {
                        domain = val.startsWith(".") ? val.substring(1).toLowerCase() : val.toLowerCase();
                    }
                }
                case "path" -> path = val.isBlank() ? "/" : val;
                case "max-age" -> {
                    try {
                        long maxAge = Long.parseLong(val);
                        if (maxAge <= 0) {
                            expired = true;
                        } else {
                            expiresAt = System.currentTimeMillis() + maxAge * 1000L;
                        }
                    } catch (NumberFormatException ignored) {
                        // 非法 Max-Age 视作会话 Cookie
                    }
                }
                case "expires" -> {
                    try {
                        long t = ZonedDateTime.parse(val, DateTimeFormatter.RFC_1123_DATE_TIME)
                                .toInstant().toEpochMilli();
                        if (t <= System.currentTimeMillis()) {
                            expired = true;
                        } else {
                            expiresAt = t;
                        }
                    } catch (Exception ignored) {
                        // 非法 Expires 视作会话 Cookie
                    }
                }
                default -> {
                    // Secure/HttpOnly/SameSite 等属性与共享罐无关，忽略
                }
            }
        }
        String cacheKey = name + "|" + domain + "|" + path;
        if (expired || value.isEmpty()) {
            // 删除语义：上游要求清掉这条 Cookie
            cookies.remove(cacheKey);
            return;
        }
        cookies.put(cacheKey, new StoredCookie(name, value, domain, path, expiresAt));
    }

    /**
     * 为上游请求拼 Cookie 头
     *
     * @param host 上游主机
     * @return Cookie 头值；罐里没有可发条目时返回 {@code null}
     */
    public String cookieHeader(String host) {
        String lowerHost = host == null ? "" : host.toLowerCase();
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, StoredCookie> entry : cookies.entrySet()) {
            StoredCookie cookie = entry.getValue();
            if (cookie.expiresAt() > 0 && cookie.expiresAt() < now) {
                cookies.remove(entry.getKey());
                continue;
            }
            if (!matches(lowerHost, cookie)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(cookie.name()).append('=').append(cookie.value());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 域匹配：精确或其注册子域
     */
    private boolean matches(String host, StoredCookie cookie) {
        String domain = cookie.domain();
        if (domain == null || domain.isBlank()) {
            return true;
        }
        return host.equals(domain) || host.endsWith("." + domain);
    }

    /**
     * 存储型 Cookie
     *
     * @param name      名称
     * @param value     值
     * @param domain    归属域（无 Domain 属性时为请求主机，即 host-only）
     * @param path      路径
     * @param expiresAt 过期时间戳；0 = 会话 Cookie
     */
    private record StoredCookie(String name, String value, String domain, String path, long expiresAt) {
    }
}
