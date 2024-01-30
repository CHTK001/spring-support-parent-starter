package com.chua.starter.common.support.utils;

import com.google.common.base.Joiner;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cookie.Util
 *
 * @author xuxueli 2015-12-12 18:01:06
 */
public class CookieUtil {

    /**
     * 默认缓存时间,单位/秒, 2H
     */
    private static final int COOKIE_MAX_AGE = Integer.MAX_VALUE;
    /**
     * 保存路径,根路径
     */
    private static final String COOKIE_PATH = "/";

    /**
     * 获取cookie
     *
     * @param cookieName cookie
     * @return cookie
     */
    public static Cookie getCookie(Cookie[] cookies, String cookieName) {
        if (null == cookies || cookies.length == 0) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie;
            }
        }

        return null;
    }

    /**
     * 保存
     *
     * @param response
     * @param key
     * @param value
     * @param ifRemember
     */
    public static void set(HttpServletResponse response, String key, String value, boolean ifRemember) {
        if(null == response) {
            return;
        }
        int age = ifRemember ? COOKIE_MAX_AGE : -1;
        set(response, key, value, null, COOKIE_PATH, age, false);
    }

    /**
     * 保存
     *
     * @param response
     * @param key
     * @param value
     * @param maxAge
     */
    private static void set(HttpServletResponse response, String key, String value, String domain, String path, int maxAge, boolean isHttpOnly) {
        Cookie cookie = new Cookie(key, value);
        if (domain != null) {
            cookie.setDomain(domain);
        }
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(isHttpOnly);
        response.addCookie(cookie);
    }

    /**
     * 查询value
     *
     * @param request
     * @param key
     * @return
     */
    public static String getValue(HttpServletRequest request, String key) {
        if(request == null) {
            return null;
        }

        Cookie cookie = get(request, key);
        if (cookie != null) {
            return cookie.getValue();
        }
        return null;
    }

    /**
     * 查询Cookie
     *
     * @param request
     * @param key
     */
    public static Cookie get(HttpServletRequest request, String key) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(key)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    /**
     * 删除Cookie
     *
     * @param request  请求
     * @param response 详情
     * @param key      cookie
     */
    public static void remove(HttpServletRequest request, HttpServletResponse response, String key) {
        Cookie cookie = get(request, key);
        if (cookie != null) {
            set(response, key, "", null, COOKIE_PATH, 0, false);
        }
    }

    /**
     * 到字符串
     *
     * @param cookies Cookie
     * @return {@link String}
     */
    public static String toString(Cookie[] cookies) {
        return Joiner.on("&").withKeyValueSeparator("=").join(Arrays.stream(cookies)
                .collect(Collectors.toMap(Cookie::getName, Cookie::getValue)));
    }
}