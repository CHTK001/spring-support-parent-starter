package com.chua.starter.common.support.network;

/**
 * User-Agent 工具。
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class UserAgent {

    private UserAgent() {
    }

    /**
     * 解析 User-Agent 字符串。
     *
     * @param userAgentString User-Agent 字符串
     * @return UserAgent 实例
     */
    public static UserAgentInfo parseUserAgentString(String userAgentString) {
        if (userAgentString == null) {
            return new UserAgentInfo(Browser.UNKNOWN, OperatingSystem.UNKNOWN);
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

        return new UserAgentInfo(browser, os);
    }

    public static boolean isCrawler(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        String ua = userAgent.toLowerCase();
        return ua.contains("bot") || ua.contains("spider") || ua.contains("crawler") || ua.contains("python") || ua.contains("http");
    }

    /**
     * User-Agent 信息。
     */
    public static final class UserAgentInfo {
        private final Browser browser;
        private final OperatingSystem operatingSystem;

        public UserAgentInfo(Browser browser, OperatingSystem operatingSystem) {
            this.browser = browser;
            this.operatingSystem = operatingSystem;
        }

        /**
         * 浏览器信息。
         */
        public Browser getBrowser() {
            return browser;
        }

        /**
         * 操作系统信息。
         */
        public OperatingSystem getOperatingSystem() {
            return operatingSystem;
        }
    }

    /**
     * 浏览器类型。
     */
    public enum Browser {
        CHROME,
        FIREFOX,
        SAFARI,
        EDGE,
        IE,
        UNKNOWN;

        @Override
        public String toString() {
            return name();
        }
    }

    /**
     * 操作系统类型。
     */
    public enum OperatingSystem {
        WINDOWS,
        MACOS,
        LINUX,
        ANDROID,
        IOS,
        UNKNOWN;

        public String getName() {
            return name();
        }
    }
}
