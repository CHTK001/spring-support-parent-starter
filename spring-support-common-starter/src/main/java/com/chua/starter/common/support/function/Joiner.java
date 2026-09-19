package com.chua.starter.common.support.function;

/**
 * 连接器工具。
 * @author CH
 * @since 4.0.0
 */
public final class Joiner {

    /** Separator */
    private final String separator;
    /** Omit是否为空 */
    private final boolean omitEmpty;
    /** Trimresults */
    private final boolean trimResults;

    /**
     * 创建 Joiner 实例
     * @param separator separator
     * @param omitEmpty 布尔值
     * @param omitEmpty 布尔值
     * @param omitEmpty omit空
     * @param trimResults 修剪结果
     */
    private Joiner(String separator, boolean omitEmpty, boolean trimResults) {
        this.separator = separator == null ? "" : separator;
        this.omitEmpty = omitEmpty;
        this.trimResults = trimResults;
    }

    /** 创建 Joiner 实例 */
    private Joiner() {
        /**
         * this。
         * @param "" ""
         * @param false false
         * @param false false
         */
        this("", false, false);
    }

    /**
     * On
     *
     * @param separator separator
     * @return on的结果
     */
    public static Joiner on(String separator) {
        /**
         * 连接。
         * @param separator separator
         * @param false false
         * @param false false
         */
        return new Joiner(separator, false, false);
    }

    /**
     * omit空字符串
     *
     * @return omit空字符串的结果
     */
    public Joiner omitEmptyStrings() {
        /**
         * 连接。
         * @param this.separator this.separator
         * @param true true
         * @param this.trimResults this.修剪结果
         */
        return new Joiner(this.separator, true, this.trimResults);
    }

    /**
     * 去空格结果
     *
     * @return 修剪结果的结果
     */
    public Joiner trimResults() {
        /**
         * 连接。
         * @param this.separator this.separator
         * @param this.omitEmpty this.omit空
         * @param true true
         */
        return new Joiner(this.separator, this.omitEmpty, true);
    }

    /**
     * 合并
     *
     * @param parts parts
     * @return 连接的结果
     */
    public String join(Iterable<?> parts) {
        if (parts == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object part : parts) {
            String partStr = part == null ? "" : part.toString();
            if (trimResults) {
                partStr = partStr.trim();
            }
            if (omitEmpty && partStr.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(separator);
            }
            sb.append(partStr);
            first = false;
        }
        return sb.toString();
    }

    /**
     * 合并
     *
     * @param parts parts
     * @return 连接的结果
     */
    public String join(Object[] parts) {
        if (parts == null) {
            return "";
        }
        return join(java.util.Arrays.asList(parts));
    }
}

