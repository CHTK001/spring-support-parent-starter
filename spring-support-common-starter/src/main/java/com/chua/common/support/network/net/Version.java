package com.chua.common.support.network.net;

/**
 * 版本号解析与比较类，支持语义化版本号的解析和排序
 *
 * @author CH
 * @since 4.0.0
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
     * 原始的版本号字符串
     */
    private final String original;

    /**
     * 私有构造函数，通过 {@link #parse(String)} 工厂方法创建实例
     *
     * @param major    主版本号
     * @param minor    次版本号
     * @param patch    修订号
     * @param original 原始版本号字符串
     */
    private Version(int major, int minor, int patch, String original) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.original = original;
    }

    /**
      * 解析版本号字符串为 版本 对象
     * <p>支持以 "v" 或 "V" 开头的版本号，自动过滤非数字后缀。
     * 格式为 "主版本号.次版本号.修订号"，例如 "v1.2.3"、"2.0.1-beta"。</p>
     *
     * @param version 版本号字符串，允许为 空 或空
     * @return Version 对象，无法解析时各分量默认为 0
     */
    public static Version parse(String version) {
        if (version == null || version.isEmpty()) {
            return new Version(0, 0, 0, "0");
        }
        String v = version;
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        String[] parts = v.split("[^0-9]", 2);
        String[] nums = parts[0].split("\\.");
        int major = 0, minor = 0, patch = 0;
        try {
            major = nums.length > 0 ? Integer.parseInt(nums[0]) : 0;
            minor = nums.length > 1 ? Integer.parseInt(nums[1]) : 0;
            patch = nums.length > 2 ? Integer.parseInt(nums[2]) : 0;
        } catch (NumberFormatException ignored) {
        }
        return new Version(major, minor, patch, version);
    }

    /**
     * 比较两个版本号的大小
     * <p>依次比较主版本号、次版本号、修订号，按字典序比较整数值。</p>
     *
     * @param o 要比较的另一个版本对象，为 空 时返回 1（当前版本较大）
     * @return 负数表示小于、零表示等于、正数表示大于
     */
    @Override
    public int compareTo(Version o) {
        if (o == null) {
            return 1;
        }
        int result = Integer.compare(this.major, o.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(this.minor, o.minor);
        if (result != 0) {
            return result;
        }
        return Integer.compare(this.patch, o.patch);
    }

    /**
     * 获取主版本号
     *
     * @return 主版本号
     */
    public int getMajor() { return major; }

    /**
     * 获取次版本号
     *
     * @return 次版本号
     */
    public int getMinor() { return minor; }

    /**
     * 获取修订号
     *
     * @return 修订号
     */
    public int getPatch() { return patch; }

    /**
     * 返回原始版本号字符串
     *
     * @return 原始版本号（如 "v1.2.3"）
     */
    @Override
    public String toString() { return original; }
}
