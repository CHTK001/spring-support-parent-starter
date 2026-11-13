package com.chua.starter.oauth.client.support.session;

import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态协议在线会话登记表
 * <p>
 * 静态令牌自身携带用户信息且不落库，服务端无法枚举"谁在线"。
 * 这里按令牌摘要登记一份进程内会话快照，供在线用户列表与强制下线使用。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class StaticSessionRegistry {

    /**
     * 进程内单例
    */
    private static final StaticSessionRegistry INSTANCE = new StaticSessionRegistry();
    /**
     * 超过该空闲时长(毫秒)视为离线
    */
    private static final long IDLE_MILLIS = 30L * 60 * 1000;
    /**
     * 会话容器，Key 为令牌 SHA-256 摘要
    */
    private final Map<String, OnlineSession> sessions = new ConcurrentHashMap<>();

    /**
     * 创建登记表
    */
    private StaticSessionRegistry() {
    }

    /**
     * 获取单例
     *
     * @return 登记表实例
     */
    public static StaticSessionRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 登记或刷新一次会话访问
     *
     * @param tokenHash 令牌摘要
     * @param resume    认证通过的用户信息
     * @param request   当前请求，用于取 IP 与 UA
     */
    public void record(String tokenHash, UserResume resume, HttpServletRequest request) {
        if (null == tokenHash || null == resume) {
            return;
        }
        long now = System.currentTimeMillis();
        String ip = null != request ? RequestUtils.getIpAddress(request) : null;
        String ua = null != request ? request.getHeader("User-Agent") : null;
        sessions.compute(tokenHash, (key, exist) -> {
            if (null != exist) {
                exist.setLastAccessTime(now);
                if (null != ip) {
                    exist.setLoginIp(ip);
                }
                if (null == exist.getBrowser()) {
                    exist.setBrowser(browserOf(ua));
                }
                if (null == exist.getOs()) {
                    exist.setOs(osOf(ua));
                }
                return exist;
            }
            OnlineSession session = new OnlineSession();
            session.setUserId(resume.getUserId());
            session.setUsername(resume.getUsername());
            session.setNickname(resume.getNickName());
            session.setLoginType(resume.getLoginType());
            session.setExpireTime(resume.getExpireTime());
            session.setLoginIp(ip);
            session.setBrowser(browserOf(ua));
            session.setOs(osOf(ua));
            session.setLoginTime(now);
            session.setLastAccessTime(now);
            return session;
        });
    }

    /**
     * 当前在线会话（已剔除空闲超时），按登录时间倒序
     *
     * @param usernameLike 用户名模糊过滤，可为空
     * @param ipLike       IP 模糊过滤，可为空
     * @return 在线会话列表
     */
    public List<OnlineSession> online(String usernameLike, String ipLike) {
        evictIdle();
        List<OnlineSession> result = new ArrayList<>();
        String user = trim(usernameLike);
        String ip = trim(ipLike);
        for (OnlineSession session : sessions.values()) {
            if (null != user && !containsIgnoreCase(session.getUsername(), user)
                    && !containsIgnoreCase(session.getNickname(), user)) {
                continue;
            }
            if (null != ip && !containsIgnoreCase(session.getLoginIp(), ip)) {
                continue;
            }
            result.add(session);
        }
        result.sort(Comparator.comparing(OnlineSession::getLoginTime).reversed());
        return result;
    }

    /**
     * 查出某用户全部会话的令牌摘要（用于强制下线）
     *
     * @param userKey 用户标识或用户名
     * @return 令牌摘要列表
     */
    public List<String> tokenHashesOf(String userKey) {
        List<String> hashes = new ArrayList<>();
        String key = trim(userKey);
        if (null == key) {
            return hashes;
        }
        for (Map.Entry<String, OnlineSession> entry : sessions.entrySet()) {
            OnlineSession session = entry.getValue();
            if (key.equalsIgnoreCase(session.getUserId()) || key.equalsIgnoreCase(session.getUsername())) {
                hashes.add(entry.getKey());
            }
        }
        return hashes;
    }

    /**
     * 移除会话
     *
     * @param tokenHash 令牌摘要
     */
    public void remove(String tokenHash) {
        if (null != tokenHash) {
            sessions.remove(tokenHash);
        }
    }

    /**
     * 当前在线数量
     *
     * @return 数量
     */
    public int size() {
        evictIdle();
        return sessions.size();
    }

        /**
         * 剔除空闲超时与已过期的会话
         */
    private void evictIdle() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            OnlineSession session = entry.getValue();
            if (now - session.getLastAccessTime() > IDLE_MILLIS) {
                return true;
            }
            Long expireTime = session.getExpireTime();
            return null != expireTime && now > expireAtMillis(expireTime);
        });
    }

    /**
     * 秒级或毫秒级过期时间统一为毫秒
     *
     * @param expireTime 过期时间
     * @return 毫秒时间戳
     */
    public static long expireAtMillis(long expireTime) {
        // [P3C A20 豁免]
        return expireTime > 100_000_000_000L ? expireTime : expireTime * 1000L;
    }

    /**
     * 是否为有效字符串
     *
     * @param value 值
     * @return 修剪后的字符串，空白返回 空
     */
    private static String trim(String value) {
        if (null == value) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 从 UA 粗分浏览器名称
     *
     * @param ua User-Agent 头部
     * @return 浏览器名称，无法识别返回 空
     */
    private static String browserOf(String ua) {
        if (null == ua) {
            return null;
        }
        if (ua.contains("Edg/") || ua.contains("Edge/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("Firefox")) {
            return "Firefox";
        }
        if (ua.contains("Chrome")) {
            return "Chrome";
        }
        return ua.contains("Safari") ? "Safari" : null;
    }

    /**
     * 从 UA 粗分操作系统名称
     *
     * @param ua User-Agent 头部
     * @return 操作系统名称，无法识别返回 空
     */
    private static String osOf(String ua) {
        if (null == ua) {
            return null;
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            return "iOS";
        }
        if (ua.contains("Mac OS")) {
            return "macOS";
        }
        return ua.contains("Linux") ? "Linux" : null;
    }

    /**
     * 忽略大小写的包含匹配
     *
     * @param value    被匹配值
     * @param fragment 片段
     * @return 是否命中
     */
    private static boolean containsIgnoreCase(String value, String fragment) {
        return null != value && value.toLowerCase().contains(fragment.toLowerCase());
    }

    /**
     * 一条在线会话
     */
    @Data
    public static class OnlineSession {
        /**
         * 用户标识
        */
        private String userId;
        /**
         * 用户名
        */
        private String username;
        /**
         * 昵称
        */
        private String nickname;
        /**
         * 登录类型
        */
        private String loginType;
        /**
         * 令牌过期时间(秒级或毫秒级时间戳)
        */
        private Long expireTime;
        /**
         * 登录 IP
        */
        private String loginIp;
        /**
         * 浏览器
        */
        private String browser;
        /**
         * 操作系统
        */
        private String os;
        /**
         * 会话建立时间(毫秒)
        */
        private long loginTime;
        /**
         * 最后访问时间(毫秒)
        */
        private long lastAccessTime;
    }
}
