package com.chua.common.support.lang.version;

/**
 * 版本比较类，支持语义化版本和简单版本字符串的比较
 *
 * @author CH
 */
public class Version implements Comparable<Version> {

    /** Segments */
    private final int[] segments;

    /**
     * 构造版本实例
     *
     * @param segments 版本段
     */
    public Version(int... segments) {
        this.segments = segments;
    }

    /**
     * 从字符串创建版本实例
     *
     * @param version 版本字符串
     * @return 版本实例
     */
    public static Version of(String version) {
        int[] parts = new int[10];
        int count = 0;
        if (version != null) {
            for (String part : version.split("[\\.-]")) {
                try {
                    parts[count++] = Integer.parseInt(part.replaceAll("[^\\d]", ""));
                } catch (NumberFormatException e) {
                    // 忽略非数字部分
                }
            }
        }
        return new Version(parts);
    }

    /**
     * 比较版本大小
     *
     * @param other 另一个版本
     * @return 正数表示更大，负数表示更小，0表示相等
     */
    @Override
    public int compareTo(Version other) {
        for (int i = 0; i < segments.length; i++) {
            int otherSegment = (i < other.segments.length) ? other.segments[i] : 0;
            int thisSegment = segments[i];
            if (thisSegment != otherSegment) {
                return Integer.compare(thisSegment, otherSegment);
            }
        }
        return 0;
    }

    /**
     * 判断是否小于另一个版本
     *
     * @param other 另一个版本
     * @return 小于时返回 true
     */
    public boolean lessThan(Version other) {
        return compareTo(other) < 0;
    }

    /**
     * 判断是否大于另一个版本
     *
     * @param other 另一个版本
     * @return 大于时返回 true
     */
    public boolean greaterThan(Version other) {
        return compareTo(other) > 0;
    }

    /**
     * 获取版本后缀
     *
     * @return 版本后缀字符串
     */
    public String getSuffix() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append(".");
            }
            sb.append(segments[i]);
        }
        return sb.toString();
    }

    /**
     * 判断是否高于另一个版本（与 greaterThan 相同）
     *
     * @param other 另一个版本
     * @return 高于时返回 true
     */
    public boolean isHigherThan(Version other) {
        return greaterThan(other);
    }
}
