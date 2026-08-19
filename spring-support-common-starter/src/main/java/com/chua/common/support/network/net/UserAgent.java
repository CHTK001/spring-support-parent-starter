package com.chua.common.support.network.net;

/**
 * User-Agent 工具类
 *
 * @author CH
 */
public class UserAgent {

    /**
     * 判断给定的 User-Agent 字符串是否来自爬虫/机器人
     * <p>通过匹配 "bot"、"spider"、"crawl"、"curl" 等关键字进行判断，忽略大小写差异。</p>
     *
     * @param userAgent HTTP User-Agent 请求头字符串
     * @return 如果认定为爬虫返回 true，否则返回 false；参数为 null 时返回 false
     */
    public static boolean isCrawler(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        String lower = userAgent.toLowerCase();
        return lower.contains("bot") || lower.contains("spider") || lower.contains("crawl") || lower.contains("curl");
    }
}
