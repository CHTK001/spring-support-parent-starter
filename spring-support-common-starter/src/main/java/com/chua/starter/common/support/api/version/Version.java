package com.chua.starter.common.support.api.version;

import com.chua.common.support.utils.StringUtils;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义化版本号
 * <p>
 * 支持标准语义化版本格式：major.minor.patch[-prerelease][+build]
 * </p>
 *
 * <h3>版本格式</h3>
 * <ul>
 *   <li>1 - 主版本号</li>
 *   <li>1.0 - 主版本号.次版本号</li>
 *   <li>1.0.0 - 主版本号.次版本号.修订号</li>
 *   <li>1.0.0-alpha - 带预发布标识</li>
 *   <li>1.0.0-beta.1 - 带预发布版本号</li>
 *   <li>1.0.0-rc.1 - 发布候选版本</li>
 *   <li>1.0.0-release - 正式发布版本</li>
 *   <li>1.0.0+build.123 - 带构建元数据</li>
 *   <li>latest - 特殊标识，表示最新版本</li>
 * </ul>
 *
 * <h3>比较规则</h3>
 * <ol>
 *   <li>先比较主版本号</li>
 *   <li>再比较次版本号</li>
 *   <li>再比较修订号</li>
 *   <li>最后比较预发布标识（release > rc > beta > alpha > snapshot）</li>
 *   <li>构建元数据不参与比较</li>
 * </ol>
 *
 * @author CH
 * @since 2024/12/18
 * @version 1.0.0
 */
public class Version implements Comparable<Version> {

    /**
     * 最新版本标识
     */
    public static final String LATEST = "latest";

    /**
     * 版本号正则表达式
     * 支持格式：1, 1.0, 1.0.0, 1.0.0-alpha, 1.0.0-alpha.1, 1.0.0+build
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z0-9]+)*))?(?:\\+([a-zA-Z0-9.]+))?$"
    );

    /**
     * 预发布版本优先级（数值越大优先级越高）
     */
    private static final java.util.Map<String, Integer> PRERELEASE_PRIORITY = java.util.Map.of(
            "snapshot", 1,
            "alpha", 2,
            "beta", 3,
            "rc", 4,
            "release", 5,
            "final", 5,
            "ga", 5
    );

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
     * 预发布标识（如 alpha, beta, rc, release）
     */
    private final String prerelease;

    /**
     * 构建元数据
     */
    private final String buildMetadata;

    /**
     * 是否为 latest 特殊版本
     */
    private final boolean latest;

    /**
     * 原始版本字符串
     */
    private final String original;

    /**
     * 私有构造函数
     */
    private Version(int major, int minor, int patch, String prerelease, String buildMetadata, 
                    boolean latest, String original) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
        this.buildMetadata = buildMetadata;
        this.latest = latest;
        this.original = original;
    }

    /**
     * 解析版本号字符串
     *
     * @param version 版本号字符串
     * @return Version 对象
     * @throws IllegalArgumentException 如果版本格式无效
     */
    public static Version parse(String version) {
        if (StringUtils.isBlank(version)) {
            return new Version(1, 0, 0, null, null, false, "1.0.0");
        }

        String trimmed = version.trim();

        // 处理 latest 特殊标识
        if (LATEST.equalsIgnoreCase(trimmed)) {
            return new Version(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 
                    null, null, true, LATEST);
        }

        Matcher matcher = VERSION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            // 尝试解析为简单数字格式（向后兼容）
            try {
                double d = Double.parseDouble(trimmed);
                int major = (int) d;
                int minor = (int) ((d - major) * 10);
                return new Version(major, minor, 0, null, null, false, trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid version format: " + version);
            }
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        String prerelease = matcher.group(4);
        String buildMetadata = matcher.group(5);

        return new Version(major, minor, patch, prerelease, buildMetadata, false, trimmed);
    }

    /**
     * 安全解析版本号（不抛异常）
     *
     * @param version 版本号字符串
     * @return Version 对象，解析失败返回默认版本 1.0.0
     */
    public static Version parseOrDefault(String version) {
        try {
            return parse(version);
        } catch (Exception e) {
            return new Version(1, 0, 0, null, null, false, "1.0.0");
        }
    }

    /**
     * 创建版本号
     *
     * @param major 主版本号
     * @param minor 次版本号
     * @param patch 修订号
     * @return Version 对象
     */
    public static Version of(int major, int minor, int patch) {
        return new Version(major, minor, patch, null, null, false, 
                major + "." + minor + "." + patch);
    }

    /**
     * 创建带预发布标识的版本号
     *
     * @param major 主版本号
     * @param minor 次版本号
     * @param patch 修订号
     * @param prerelease 预发布标识
     * @return Version 对象
     */
    public static Version of(int major, int minor, int patch, String prerelease) {
        String original = major + "." + minor + "." + patch;
        if (StringUtils.isNotBlank(prerelease)) {
            original += "-" + prerelease;
        }
        return new Version(major, minor, patch, prerelease, null, false, original);
    }

    /**
     * 获取最新版本对象
     *
     * @return 表示最新版本的 Version 对象
     */
    public static Version latest() {
        return new Version(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 
                null, null, true, LATEST);
    }

    /**
     * 比较版本号
     *
     * @param other 另一个版本
     * @return 比较结果
     */
    @Override
    public int compareTo(Version other) {
        if (other == null) {
            return 1;
        }

        // latest 版本始终最大
        if (this.latest && other.latest) {
            return 0;
        }
        if (this.latest) {
            return 1;
        }
        if (other.latest) {
            return -1;
        }

        // 比较主版本号
        int result = Integer.compare(this.major, other.major);
        if (result != 0) {
            return result;
        }

        // 比较次版本号
        result = Integer.compare(this.minor, other.minor);
        if (result != 0) {
            return result;
        }

        // 比较修订号
        result = Integer.compare(this.patch, other.patch);
        if (result != 0) {
            return result;
        }

        // 比较预发布标识
        return comparePrerelease(this.prerelease, other.prerelease);
    }

    /**
     * 比较预发布标识
     */
    private int comparePrerelease(String pre1, String pre2) {
        // 没有预发布标识的版本比有预发布标识的版本高
        // 例如：1.0.0 > 1.0.0-rc.1 > 1.0.0-beta > 1.0.0-alpha
        boolean hasPre1 = StringUtils.isNotBlank(pre1);
        boolean hasPre2 = StringUtils.isNotBlank(pre2);

        if (!hasPre1 && !hasPre2) {
            return 0;
        }
        if (!hasPre1) {
            return 1; // 没有预发布标识的版本更高
        }
        if (!hasPre2) {
            return -1;
        }

        // 解析预发布标识
        String[] parts1 = pre1.toLowerCase().split("\\.");
        String[] parts2 = pre2.toLowerCase().split("\\.");

        // 获取预发布类型优先级
        int priority1 = getPrereleasePriority(parts1[0]);
        int priority2 = getPrereleasePriority(parts2[0]);

        if (priority1 != priority2) {
            return Integer.compare(priority1, priority2);
        }

        // 如果类型相同，比较版本号（如 alpha.1 vs alpha.2）
        if (parts1.length > 1 && parts2.length > 1) {
            try {
                int num1 = Integer.parseInt(parts1[1]);
                int num2 = Integer.parseInt(parts2[1]);
                return Integer.compare(num1, num2);
            } catch (NumberFormatException e) {
                // 非数字，按字符串比较
                return parts1[1].compareTo(parts2[1]);
            }
        }

        // 有版本号的比没有版本号的高
        return Integer.compare(parts1.length, parts2.length);
    }

    /**
     * 获取预发布类型的优先级
     */
    private int getPrereleasePriority(String type) {
        return PRERELEASE_PRIORITY.getOrDefault(type.toLowerCase(), 0);
    }

    /**
     * 判断当前版本是否大于等于指定版本
     *
     * @param other 另一个版本
     * @return 是否大于等于
     */
    public boolean isGreaterThanOrEqual(Version other) {
        return compareTo(other) >= 0;
    }

    /**
     * 判断当前版本是否大于指定版本
     *
     * @param other 另一个版本
     * @return 是否大于
     */
    public boolean isGreaterThan(Version other) {
        return compareTo(other) > 0;
    }

    /**
     * 判断当前版本是否小于等于指定版本
     *
     * @param other 另一个版本
     * @return 是否小于等于
     */
    public boolean isLessThanOrEqual(Version other) {
        return compareTo(other) <= 0;
    }

    /**
     * 判断当前版本是否小于指定版本
     *
     * @param other 另一个版本
     * @return 是否小于
     */
    public boolean isLessThan(Version other) {
        return compareTo(other) < 0;
    }

    /**
     * 判断当前版本是否匹配指定版本（精确匹配）
     *
     * @param other 另一个版本
     * @return 是否匹配
     */
    public boolean matches(Version other) {
        return compareTo(other) == 0;
    }

    /**
     * 判断当前版本是否在指定范围内
     *
     * @param min 最小版本（包含）
     * @param max 最大版本（包含）
     * @return 是否在范围内
     */
    public boolean isInRange(Version min, Version max) {
        return isGreaterThanOrEqual(min) && isLessThanOrEqual(max);
    }

    /**
     * 获取标准化版本字符串
     *
     * @return 标准化版本字符串
     */
    public String toStandardString() {
        if (latest) {
            return LATEST;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (StringUtils.isNotBlank(prerelease)) {
            sb.append('-').append(prerelease);
        }
        if (StringUtils.isNotBlank(buildMetadata)) {
            sb.append('+').append(buildMetadata);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Version version = (Version) o;
        return compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, prerelease);
    }

    @Override
    public String toString() {
        return original;
    }
    /**
     * 获取 major
     *
     * @return major
     */
    public int getMajor() {
        return major;
    }

    /**
     * 获取 minor
     *
     * @return minor
     */
    public int getMinor() {
        return minor;
    }

    /**
     * 获取 patch
     *
     * @return patch
     */
    public int getPatch() {
        return patch;
    }

    /**
     * 获取 prerelease
     *
     * @return prerelease
     */
    public String getPrerelease() {
        return prerelease;
    }

    /**
     * 获取 buildMetadata
     *
     * @return buildMetadata
     */
    public String getBuildMetadata() {
        return buildMetadata;
    }

    /**
     * 获取 latest
     *
     * @return latest
     */
    public boolean getLatest() {
        return latest;
    }

    /**
     * 获取 original
     *
     * @return original
     */
    public String getOriginal() {
        return original;
    }

    /**
     * 获取 trimmed
     *
     * @return trimmed
     */
    public String getTrimmed() {
        return trimmed;
    }

    /**
     * 获取 matcher
     *
     * @return matcher
     */
    public Matcher getMatcher() {
        return matcher;
    }

    /**
     * 获取 d
     *
     * @return d
     */
    public double getD() {
        return d;
    }

    /**
     * 获取 major
     *
     * @return major
     */
    public int getMajor() {
        return major;
    }

    /**
     * 获取 minor
     *
     * @return minor
     */
    public int getMinor() {
        return minor;
    }

    /**
     * 获取 major
     *
     * @return major
     */
    public int getMajor() {
        return major;
    }

    /**
     * 获取 minor
     *
     * @return minor
     */
    public int getMinor() {
        return minor;
    }

    /**
     * 获取 patch
     *
     * @return patch
     */
    public int getPatch() {
        return patch;
    }

    /**
     * 获取 prerelease
     *
     * @return prerelease
     */
    public String getPrerelease() {
        return prerelease;
    }

    /**
     * 获取 buildMetadata
     *
     * @return buildMetadata
     */
    public String getBuildMetadata() {
        return buildMetadata;
    }

    /**
     * 获取 original
     *
     * @return original
     */
    public String getOriginal() {
        return original;
    }

                /**
     * 获取 result
     *
     * @return result
     */
    public int getResult() {
        return result;
    }

        /**
     * 获取 result
     *
     * @return result
     */
    public 比较次版本号 getResult() {
        return result;
    }

        /**
     * 获取 result
     *
     * @return result
     */
    public 比较修订号 getResult() {
        return result;
    }

        /**
     * 获取 hasPre1
     *
     * @return hasPre1
     */
    public boolean getHasPre1() {
        return hasPre1;
    }

    /**
     * 获取 hasPre2
     *
     * @return hasPre2
     */
    public boolean getHasPre2() {
        return hasPre2;
    }

            /**
     * 获取 parts1
     *
     * @return parts1
     */
    public String[] getParts1() {
        return parts1;
    }

    /**
     * 获取 parts2
     *
     * @return parts2
     */
    public String[] getParts2() {
        return parts2;
    }

    /**
     * 获取 priority1
     *
     * @return priority1
     */
    public int getPriority1() {
        return priority1;
    }

    /**
     * 获取 priority2
     *
     * @return priority2
     */
    public int getPriority2() {
        return priority2;
    }

    /**
     * 获取 num1
     *
     * @return num1
     */
    public int getNum1() {
        return num1;
    }

    /**
     * 获取 num2
     *
     * @return num2
     */
    public int getNum2() {
        return num2;
    }

    /**
     * 获取 sb
     *
     * @return sb
     */
    public StringBuilder getSb() {
        return sb;
    }

            /**
     * 获取 version
     *
     * @return version
     */
    public Version getVersion() {
        return version;
    }

    }
