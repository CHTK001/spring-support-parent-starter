package com.chua.starter.strategy.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Strategy 控制台登录会话工具。
 */
public final class StrategyConsoleSessionSupport {

    public static final String SESSION_AUTHENTICATED = "strategy.console.authenticated";
    public static final String SESSION_USERNAME = "strategy.console.username";

    private StrategyConsoleSessionSupport() {
    }

    public static boolean isAuthenticated(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        return isAuthenticated(session);
    }

    public static boolean isAuthenticated(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTHENTICATED));
    }

    public static void markAuthenticated(HttpSession session, String username) {
        if (session == null) {
            return;
        }
        session.setAttribute(SESSION_AUTHENTICATED, Boolean.TRUE);
        session.setAttribute(SESSION_USERNAME, username);
    }

    public static void clear(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(SESSION_AUTHENTICATED);
        session.removeAttribute(SESSION_USERNAME);
    }

    public static String username(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_USERNAME);
        return value == null ? null : String.valueOf(value);
    }
}
