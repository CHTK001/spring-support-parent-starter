package com.chua.starter.common.support.utils;

import com.google.common.base.Joiner;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cookie.Util
 *
 * @author xuxueli 2015-12-12 18:01:06
 */
public class CookieUtil {

    /**
     * 默认缓存时间,单位/�? 2H
     */
    public static final int COOKIE_MAX_AGE = Integer.MAX_VALUE;
    /**
     * 保存路径,根路�?
     */
    private static final String COOKIE_PATH = "/";

    /**
     * 获取cookie
     *
     * @param cookieName cookie
     * @return cookie
     */
    public static Cookie getCookie(Cookie[] cookies, String cookieName) {
        if (null == cookies) {
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
     * 将键值对存储到HttpServletResponse中，可以选择是否长期保存�?
     *
     * @param response 用于设置Cookie的HttpServletResponse对象�?
     * @param key Cookie的键�?
     * @param value Cookie的值�?
     * @param ifRemember 是否记住该Cookie，如果为true，则设置较长的过期时间；否则，设置为会话Cookie�?
     */
    public static void set(HttpServletResponse response, String key, String value, boolean ifRemember) {
        // 如果response为null，则直接返回，不执行任何操作�?
        if(null == response) {
            return;
        }
        // 根据ifRemember的值确定Cookie的过期时间�?
        int age = ifRemember ? COOKIE_MAX_AGE : -1;
        // 调用另一个set方法，完成Cookie的设置�?
        set(response, key, value, null, COOKIE_PATH, age, true);
    }


    /**
     * 设置Cookie�?
     *
     * 该方法用于创建并配置一个Cookie，然后将其添加到HTTP响应中。Cookie是一种在客户端和服务器之间传递信息的技术�?
     * 通过此方法，可以设置Cookie的名称、值、域、路径、生存时间和是否只通过HTTP访问�?
     *
     * @param response HTTP响应对象，用于将Cookie添加到响应中�?
     * @param key Cookie的名称�?
     * @param value Cookie的值�?
     * @param domain Cookie适用的域名。如果不设置，则默认为当前域名�?
     * @param path Cookie适用的路径。如果不设置，则默认为根路径�?
     * @param maxAge Cookie的生存时间，以秒为单位。设置为0会导致Cookie在浏览器关闭时被删除；负值表示Cookie不会被保存在客户端�?
     * @param isHttpOnly 设置是否只能通过HTTP协议访问Cookie，以增强安全性。true表示只能通过HTTP协议访问，false表示可以通过脚本等访问�?
     */
    public static void set(HttpServletResponse response, String key, String value, String domain, String path, int maxAge, boolean isHttpOnly) {
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
     * 从HttpServletRequest中获取指定键对应的Cookie值�?
     * 此方法首先检查请求是否为null，然后尝试根据键查找Cookie。如果找到，则返回Cookie的值�?
     *
     * @param request HttpServletRequest对象，从中检索Cookie�?
     * @param key 要查找的Cookie的键�?
     * @return 如果找到指定键的Cookie，则返回其值；否则返回null�?
     */
    public static String getValue(HttpServletRequest request, String key) {
        // 检查请求对象是否为空，为空则直接返回null
        if(request == null) {
            return null;
        }

        // 尝试根据键获取Cookie对象
        Cookie cookie = get(request, key);
        // 如果Cookie存在，则返回其�?
        if (cookie != null) {
            return cookie.getValue();
        }
        // 如果Cookie不存在，则返回null
        return null;
    }

    /**
     * 从HttpServletRequest中获取指定名称的Cookie�?
     * 这个方法用于在服务器端根据Cookie的名称查找并返回对应的Cookie对象�?
     * 如果找不到匹配的Cookie，则返回null�?
     *
     * @param request HttpServletRequest对象，从中获取Cookie�?
     * @param key 指定的Cookie名称�?
     * @return 返回名称匹配的Cookie对象，如果不存在则返回null�?
     */
    public static Cookie get(HttpServletRequest request, String key) {
        // 获取请求中的所有Cookie
        Cookie[] cookies = request.getCookies();
        return get(cookies, key);
    }
    /**
     * 从Cookie数组中获取指定名称的Cookie�?
     * 这个方法用于在服务器端根据Cookie的名称查找并返回对应的Cookie对象�?
     * 如果找不到匹配的Cookie，则返回null�?
     *
     * @param cookies Cookie数组，从中获取Cookie�?
     * @param key 指定的Cookie名称�?
     * @return 返回名称匹配的Cookie对象，如果不存在则返回null�?
     */
    public static Cookie get( Cookie[] cookies, String key) {
        // 检查是否有Cookie被返回，以及数组是否为空
        if (cookies != null) {
            // 遍历所有Cookie，寻找匹配名称的Cookie
            for (Cookie cookie : cookies) {
                // 如果当前Cookie的名称与参数key匹配，则返回该Cookie
                if (cookie.getName().equals(key)) {
                    return cookie;
                }
            }
        }
        // 如果没有找到匹配的Cookie，返回null
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
    public static String toString(Cookie... cookies) {
        return Joiner.on(";").withKeyValueSeparator("=").join(Arrays.stream(cookies)
                .collect(Collectors.toMap(Cookie::getName, Cookie::getValue)));
    }
}
