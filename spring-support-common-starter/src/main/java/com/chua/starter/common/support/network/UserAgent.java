package com.chua.starter.common.support.network;

/**
* 用户-智能体 工具。
*
* @since 4.0.0.42
* @author CH
 */
public final class UserAgent {

    /**
    * 用户智能体。
     */
    private UserAgent() {
    }

    /**
    * 解析 用户-智能体 字符串。
    *
    * @param userAgentString 用户-智能体 字符串
    * @return UserAgent 实例
     */
    public static UserAgentInfo parseUserAgentString(String userAgentString) {
        if (userAgentString == null || userAgentString.isBlank()) {
            /**
            * 用户智能体信息。
            * @param null 空
            * @param null 空
            * @return 用户智能体信息的结果
             */
            return new UserAgentInfo(null, null);
        }
        String lower = userAgentString.toLowerCase();
        Browser browser = Browser.UNKNOWN;
        OperatingSystem os = OperatingSystem.UNKNOWN;

        if (lower.contains("edg/")) {
            browser = Browser.EDGE;
        } else if (lower.contains("chrome/") && !lower.contains("chromium")) {
            browser = Browser.CHROME;
        } else if (lower.contains("firefox/")) {
            browser = Browser.FIREFOX;
        } else if (lower.contains("safari/") && !lower.contains("chrome")) {
            browser = Browser.SAFARI;
        } else if (lower.contains("msie") || lower.contains("trident")) {
            browser = Browser.IE;
        }

        if (lower.contains("windows")) {
            os = OperatingSystem.WINDOWS;
        } else if (lower.contains("mac os") || lower.contains("macintosh")) {
            os = OperatingSystem.MACOS;
        } else if (lower.contains("linux") && !lower.contains("android")) {
            os = OperatingSystem.LINUX;
        } else if (lower.contains("android")) {
            os = OperatingSystem.ANDROID;
        } else if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) {
            os = OperatingSystem.IOS;
        }

        /**
        * 用户智能体信息。
        * @param browser browser
        * @param os os
        * @return 用户智能体信息的结果
         */
        return new UserAgentInfo(browser, os);
    }

    /**
    * 是否爬虫。
    * @param userAgent 用户智能体
    * @return 是否爬虫的结果
     */
    public static boolean isCrawler(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        String ua = userAgent.toLowerCase();
        return ua.contains("bot") || ua.contains("spider") || ua.contains("crawler") || ua.contains("python") || ua.contains("http");
    }

    /**
    * 用户-智能体 信息。
     */
    public static final class UserAgentInfo {
        /** Browser */
        private final Browser browser;
        /** Operatingsystem */
        private final OperatingSystem operatingSystem;

        /**
        * 用户智能体信息。
        * @param browser browser
        * @param operatingSystem operating系统
        * @return 用户智能体信息的结果
         */
        public UserAgentInfo(Browser browser, OperatingSystem operatingSystem) {
            this.browser = browser;
            this.operatingSystem = operatingSystem;
        }

        /**
        * 浏览器信息。
        * @return 获取browser的结果
         */
        public Browser getBrowser() {
            return browser;
        }

        /**
        * 操作系统信息。
        * @return 获取operating系统的结果
         */
        public OperatingSystem getOperatingSystem() {
            return operatingSystem;
        }
    }

    /**
    * 浏览器类型。
    * @author CH
    * @since 4.0.0
     */
    public enum Browser {
        CHROME,
        FIREFOX,
        SAFARI,
        EDGE,
        IE,
        UNKNOWN; // UNKNOWN

        @Override
        public String toString() {
            return this == UNKNOWN ? null : name();
        }
    }

    /**
    * 操作系统类型。
    * @author CH
    * @since 4.0.0
     */
    public enum OperatingSystem {
        WINDOWS,
        MACOS,
        LINUX,
        ANDROID,
        IOS,
        UNKNOWN; // UNKNOWN

        /**
        * 获取名称。
        * @return 获取名称的结果
         */
        public String getName() {
            return this == UNKNOWN ? null : name();
        }
    }
}
