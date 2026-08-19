package com.chua.common.support.lang;

/**
 * 版本号解析与比较
 * <p>
 * 支持语义化版本号（SemVer）的解析和比较，格式为 major.minor.patch。
 * </p>
 *
 * @author CH
 */
public class Version implements Comparable<Version> {

    /**
     * 主版本号
     */
    private final int major;

    /**
     * 次版本号
     */
    private final int minor;

    /**
     * 修订号
     */
    private final int patch;

    /**
     * 构造函数
     *
     * @param major 主版本号
     * @param minor 次版本号
     * @param patch 修订号
     */
    public Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * 解析版本号字符串
     *
     * @param version 版本号字符串，如 "1.2.3"
     * @return Version 对象
     * @throws IllegalArgumentException 格式不正确时抛出
     */
    public static Version parse(String version) {
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("版本号不能为空");
        }
        String[] parts = version.split("\\.");
        int major = 0;
        int minor = 0;
        int patch = 0;
        try {
            if (parts.length > 0) {
                major = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
            }
            if (parts.length > 1) {
                minor = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
            }
            if (parts.length > 2) {
                patch = Integer.parseInt(parts[2].replaceAll("[^0-9]", ""));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("版本号格式不正确: " + version, e);
        }
        return new Version(major, minor, patch);
    }

    /**
     * 获取主版本号
     *
     * @return major
     */
    public int getMajor() {
        return major;
    }

    /**
     * 获取次版本号
     *
     * @return minor
     */
    public int getMinor() {
        return minor;
    }

    /**
     * 获取修订号
     *
     * @return patch
     */
    public int getPatch() {
        return patch;
    }

    /**
     * 比较两个版本号的大小
     * <p>依次比较主版本号、次版本号、修订号，按字典序比较整数值。</p>
     *
     * @param other 要比较的另一个版本对象
     * @return 负数表示小于、零表示等于、正数表示大于
     */
    @Override
    public int compareTo(Version other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }

    /**
     * 返回格式化的版本号字符串，格式为 "主版本号.次版本号.修订号"
     *
     * @return 版本号字符串，例如 "1.2.3"
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    /**
     * 判断两个版本对象是否相等
     * <p>当且仅当两个版本的主版本号、次版本号、修订号全部相等时返回 true。</p>
     *
     * @param o 待比较的对象
     * @return 如果版本号完全相同则返回 true，否则返回 false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Version version = (Version) o;
        return major == version.major && minor == version.minor && patch == version.patch;
    }

    /**
     * 计算版本对象的哈希码，基于主版本号、次版本号和修订号
     *
     * @return 哈希码值
     */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(major, minor, patch);
    }
}
