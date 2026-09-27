package com.chua.starter.job.support.glue.compile;

import java.util.regex.Pattern;

/**
 * 胶水片段源码的包名与主类名。
 *
 * <p>胶水片段是一段<b>完整</b> Java 类源码（可带 {@code package} 声明），
 * 平台需要从正文里反推主类全名才能加载编译产物。正则只做粗定位：
 * 误判会直接体现在 {@code javac} 的报错里，不会静默跑错类。</p>
 *
 * <p>刻意不要求源码以 {@code package} 开头，也不要求 {@code public class}——
 * 片段作者写什么就编译什么，类名从源码里抓。</p>
 *
 * @param packageName 包名，未声明时为空串
 * @param simpleName  类名（不含包）
 * @author CH
 * @since 4.0.0.43
 */
public record SourceCoordinates(String packageName, String simpleName) {

    /**
     * 包声明
     */
    private static final Pattern PACKAGE =
            Pattern.compile("(?m)^[\\t ]*package[\\t ]+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)[\\t ]*;");

    /**
     * 顶层类声明：允许前置注解与 {@code public/final/abstract/sealed/strictfp} 修饰符
     */
    private static final Pattern TYPE =
            Pattern.compile("(?m)^[\\t ]*(?:@[\\w$.]+[\\t ]*(?:\\([^\\n]*\\))?[\\t ]*)*"
                    + "(?:(?:public|final|abstract|sealed|non-sealed|strictfp)[\\t ]+)*"
                    + "class[\\t ]+([A-Za-z_$][\\w$]*)");

    /**
     * 从源码正文解析包名与主类名。
     *
     * @param source 源码正文
     * @return 包名与类名
     * @throws IllegalStateException 解析不到类声明
     */
    public static SourceCoordinates of(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("Java 片段源码为空");
        }
        var packageName = "";
        var packageMatcher = PACKAGE.matcher(source);
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1);
        }
        var typeMatcher = TYPE.matcher(source);
        if (!typeMatcher.find()) {
            throw new IllegalStateException("Java 片段必须是一段完整的类源码，"
                    + "未找到 class 声明（接口/枚举/record 请改为 class）");
        }
        return new SourceCoordinates(packageName, typeMatcher.group(1));
    }

    /**
     * 主类二进制全名（点分）。
     *
     * @return 二进制全名
     */
    public String binaryName() {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }
}
