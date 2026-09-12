package com.chua.common.support.function;

import java.util.Collection;
import java.util.List;

/**
 * 字符串连接器
 * <p>
 * 用于将多个元素以指定分隔符连接为字符串
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
public class Joiner {

    /** Separator */
    private final String separator;
    /** Omit是否为空字符串 */
    private boolean omitEmptyStrings;
    /** Trimresults */
    private boolean trimResults;

    /**
     * 构造连接器
     *
     * @param separator 分隔符
     */
    public Joiner(String separator) {
        this.separator = separator;
    }

    /**
     * 使用指定分隔符构造连接器
     *
     * @param separator 分隔符
     * @return Joiner 实例
     */
    public static Joiner on(String separator) {
        /**
         * 连接。
         * @param separator separator
         */
        return new Joiner(separator);
    }

    /**
     * 设置忽略空字符串
     *
     * @return 当前 Joiner 实例
     */
    public Joiner omitEmptyStrings() {
        this.omitEmptyStrings = true;
        return this;
    }

    /**
     * 设置去除元素中的空白字符
     *
     * @return 当前 Joiner 实例
     */
    public Joiner trimResults() {
        this.trimResults = true;
        return this;
    }

    /**
     * 将集合元素连接为字符串
     *
     * @param parts 元素集合
     * @return 连接后的字符串
     */
    public String join(Collection<?> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object part : parts) {
            String value = part == null ? "" : part.toString();
            if (trimResults) {
                value = value.trim();
            }
            if (omitEmptyStrings && value.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(separator);
            }
            sb.append(value);
            first = false;
        }
        return sb.toString();
    }

    /**
     * with类型
     *
     * @param type 类型
     * @return with类型的结果
     */
    public <T> Joiner withType(Class<T> type) {
        return this;
    }
}