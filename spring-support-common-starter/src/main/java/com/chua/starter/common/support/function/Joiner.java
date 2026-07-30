package com.chua.starter.common.support.function;

/**
 * 连接器工具。
 */
public final class Joiner {

    private final String separator;
    private final boolean omitEmpty;
    private final boolean trimResults;

    private Joiner(String separator, boolean omitEmpty, boolean trimResults) {
        this.separator = separator == null ? "" : separator;
        this.omitEmpty = omitEmpty;
        this.trimResults = trimResults;
    }

    private Joiner() {
        this("", false, false);
    }

    public static Joiner on(String separator) {
        return new Joiner(separator, false, false);
    }

    public Joiner omitEmptyStrings() {
        return new Joiner(this.separator, true, this.trimResults);
    }

    public Joiner trimResults() {
        return new Joiner(this.separator, this.omitEmpty, true);
    }

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

    public String join(Object[] parts) {
        if (parts == null) {
            return "";
        }
        return join(java.util.Arrays.asList(parts));
    }
}
