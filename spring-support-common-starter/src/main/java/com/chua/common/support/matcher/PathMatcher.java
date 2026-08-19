package com.chua.common.support.matcher;

/**
 * 路径匹配器枚举（单例实现）
 * <p>将包含通配符（*、?）的路径模式转换为正则表达式进行匹配。
 * "*" 匹配任意多个字符，"?" 匹配任意单个字符。</p>
 *
 * @author CH
 */
public enum PathMatcher {
    /**
     * 单例实例
     */
    INSTANCE;

    /**
     * 判断给定的路径是否匹配指定的模式
     * <p>模式中的句点（.）会被转义为字面量，星号（*）转换为 .*，问号（?）转换为 .。</p>
     *
     * @param pattern 匹配模式，支持 * 和 ? 通配符
     * @param path    待匹配的路径字符串
     * @return 匹配成功返回 true，否则返回 false
     */
    public boolean match(String pattern, String path) {
        if (pattern == null || path == null) return false;
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return path.matches(regex);
    }
}
