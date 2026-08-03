package com.chua.starter.spider.flow;

/**
 * 流程执行上下文辅助工具。
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class SpiderFlowUtils {

    /**
     * 私有构造，禁止实例化。
     */
    private SpiderFlowUtils() {
    }

    /**
     * 如果字符串为空（null / ""），返回默认值。
     *
     * @param value        字符串值
     * @param defaultValue 默认值
     * @return 非空字符串或默认值
     */
    public static String defaultIfEmpty(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
