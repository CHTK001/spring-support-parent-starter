package com.chua.starter.common.support.function;

/**
 * 连接器工具。
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
     * @param boolean boolean
     * @param boolean boolean
     */
    private Joiner(String separator, boolean omitEmpty, boolean trimResults) {
        this.separator = separator == null ? "" : separator;
        this.omitEmpty = omitEmpty;
        this.trimResults = trimResults;
    }

    /** 创建 Joiner 实例 */
    private Joiner() {
        this("", false, false);
    }

    /** On */
    public static Joiner on(String separator) {
        return new Joiner(separator, false, false);
    }

    /** OmitEmptyStrings */
    public Joiner omitEmptyStrings() {
        return new Joiner(this.separator, true, this.trimResults);
    }

    /** 去空格Results */
    public Joiner trimResults() {
        return new Joiner(this.separator, this.omitEmpty, true);
    }

    /** 合并 */
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

    /** 合并 */
    public String join(Object[] parts) {
        if (parts == null) {
            return "";
        }
        return join(java.util.Arrays.asList(parts));
    }
}

