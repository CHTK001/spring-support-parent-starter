package com.chua.starter.job.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.job.support.dispatch.InMemoryJobRepository;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.glue.GlueSynthesizer;
import com.chua.starter.job.support.glue.GlueSynthesizers;
import com.chua.starter.job.support.glue.GlueTypes;
import com.chua.starter.job.support.glue.JavaGlueSynthesizer;
import com.chua.starter.job.support.glue.PassThroughGlueSynthesizer;
import com.chua.starter.job.support.log.FileJobLogSink;
import com.chua.starter.job.support.log.JobLogRepository;
import com.chua.starter.job.support.log.JobLogSink;
import com.chua.starter.job.support.log.NoopJobLogRepository;
import com.chua.starter.job.support.log.StdoutJobLogSink;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 扩展点注册探针：验证 {@code META-INF/extensions} 里的键都能取到实现。
 *
 * <p>与业务无关，只回答一个问题：<b>「换实现只改注册文件」这个承诺是否成立</b>。
 * 键写错 / 类名写错 / 构造器签名对不上时 SPI 内部会把异常吞掉并返回 {@code null}，
 * 上层再兜底 {@code new}，功能看起来仍然可用，但接入方自定义的实现被静默忽略 —— 只有这里能看见。</p>
 *
 * <p>运行方式（纯 JDK，无 JUnit）：</p>
 * <pre>
 * java -cp "target/classes;target/test-classes" com.chua.starter.job.spi.SpiRegistrationProbe
 * </pre>
 *
 * @since 4.0.0.51
 * @author CH
 */
public class SpiRegistrationProbe {

    private static int passed;
    private static int failed;

    /**
     * 探针入口。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        say("1. JobRepository：无参实现必须能从 SPI 取到");
        caseJobRepository();

        say("2. JobLogSink：无参实现与带参实现都必须能取到");
        caseJobLogSink();

        say("3. JobLogRepository：空实现必须能从 SPI 取到");
        caseJobLogRepository();

        say("4. GlueSynthesizer：引擎键必须命中对应合成器，未知名回落透传");
        caseGlueSynthesizer();

        System.out.println();
        System.out.println("==== 结果: passed=" + passed + " failed=" + failed + " ====");
        if (failed > 0) {
            throw new IllegalStateException("扩展点注册探针失败 " + failed + " 条");
        }
    }

    /**
     * 1. {@link JobRepository}：{@code in-memory} / {@code memory} 两个别名都指向内存仓库。
     */
    private static void caseJobRepository() {
        Map<String, Class<JobRepository>> mapping = ServiceProvider.of(JobRepository.class).mapping();
        check("1.1 注册表含 in-memory", hasKey(mapping, "in-memory"), "keys=" + mapping.keySet());
        check("1.2 注册表含 memory 别名", hasKey(mapping, "memory"), "keys=" + mapping.keySet());
        JobRepository repo = extension(JobRepository.class, "in-memory");
        check("1.3 取到的是内存仓库", repo instanceof InMemoryJobRepository,
                "impl=" + className(repo));
    }

    /**
     * 2. {@link JobLogSink}：{@code stdout}/{@code console} 无参，{@code file} 需要 basePath。
     */
    private static void caseJobLogSink() {
        ServiceProvider<JobLogSink> provider = ServiceProvider.of(JobLogSink.class);
        JobLogSink stdout = extension(JobLogSink.class, "stdout");
        check("2.1 stdout 取到打印日志实现", stdout instanceof StdoutJobLogSink, "impl=" + className(stdout));
        JobLogSink console = extension(JobLogSink.class, "console");
        check("2.2 console 别名同样取到", console instanceof StdoutJobLogSink, "impl=" + className(console));

        Path dir = null;
        try {
            dir = Files.createTempDirectory("job-spi-probe-");
            JobLogSink file = provider.getNewExtension("file", dir.toString());
            check("2.3 file 带 basePath 取到文件实现", file instanceof FileJobLogSink, "impl=" + className(file));
        } catch (RuntimeException ex) {
            check("2.3 file 带 basePath 取到文件实现", false, "exception=" + ex.getMessage());
        } catch (java.io.IOException ex) {
            check("2.3 file 带 basePath 取到文件实现", false, "exception=" + ex.getMessage());
        } finally {
            if (dir != null) {
                try {
                    Files.deleteIfExists(dir);
                } catch (Exception ignored) {
                    // 探针目录删不掉不影响结论
                }
            }
        }

        JobLogSink fallback = provider.getDefault();
        check("2.4 未指定键时兜底为 @SpiDefault（打印日志，不因缺库失败）",
                fallback instanceof StdoutJobLogSink, "impl=" + className(fallback));
    }

    /**
     * 3. {@link JobLogRepository}：{@code none}/{@code noop} 空实现，默认同样是空实现。
     */
    private static void caseJobLogRepository() {
        JobLogRepository none = extension(JobLogRepository.class, "none");
        check("3.1 none 取到空实现", none instanceof NoopJobLogRepository, "impl=" + className(none));
        check("3.2 空实现查询不抛异常", none != null && none.count(null) == 0L, "count 非 0");
        JobLogRepository fallback = ServiceProvider.of(JobLogRepository.class).getDefault();
        check("3.3 未指定键时兜底为空实现", fallback instanceof NoopJobLogRepository,
                "impl=" + className(fallback));
    }

    /**
     * 4. {@link GlueSynthesizer}：{@code java} 命中 Java 合成器，其余引擎回落透传。
     */
    private static void caseGlueSynthesizer() {
        GlueSynthesizer java = GlueSynthesizers.resolve(GlueTypes.ENGINE_JAVA);
        check("4.1 引擎 java 命中 Java 合成器", java instanceof JavaGlueSynthesizer, "impl=" + className(java));

        GlueSynthesizer groovy = GlueSynthesizers.resolve(GlueTypes.ENGINE_GROOVY);
        check("4.2 引擎 groovy 回落透传（本模块不做 Groovy 合成）",
                groovy instanceof PassThroughGlueSynthesizer, "impl=" + className(groovy));

        GlueSynthesizer unknown = GlueSynthesizers.resolve("lua");
        check("4.3 未知引擎回落透传而不是抛异常",
                unknown instanceof PassThroughGlueSynthesizer, "impl=" + className(unknown));

        String body = "System.out.println(\"hi\");";
        String synthesized = GlueSynthesizers.synthesize(GlueTypes.JAVA, body, "probe-job");
        check("4.4 片段被包成可编译的类（含 package 与 @Job 入口）",
                synthesized.startsWith("package ") && synthesized.contains("@Job(\"probe-job\")")
                        && synthesized.contains("void execute("),
                "synthesized 首行=" + firstLine(synthesized));
        check("4.5 非片段类型原样返回（不合成）", body.equals(
                GlueSynthesizers.synthesize(GlueTypes.GROOVY, body, "probe-job")),
                "Groovy 脚本被合成器改写了");
    }

    // ------------------------------------------------------------------ 基础设施

    /**
     * 按名称取扩展实现。
     *
     * @param type 扩展点类型
     * @param key  SPI 键
     * @param <T>  扩展点类型
     * @return 实例，取不到返回 null
     */
    private static <T> T extension(Class<T> type, String key) {
        try {
            return ServiceProvider.of(type).getExtension(key);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 取实例的简单类名（用于失败时定位）。
     *
     * @param instance 实例
     * @return 类名，null 时返回字符串 "null"
     */
    private static String className(Object instance) {
        return instance == null ? "null" : instance.getClass().getSimpleName();
    }

    /**
     * 注册表是否含某个键（SPI 内部把键统一大写，比较时大小写不敏感）。
     *
     * @param mapping 注册表
     * @param key     SPI 键
     * @return 含该键返回 true
     */
    private static boolean hasKey(Map<String, ?> mapping, String key) {
        for (String registered : mapping.keySet()) {
            if (registered != null && registered.trim().equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取文本首行。
     *
     * @param text 文本
     * @return 首行
     */
    private static String firstLine(String text) {
        if (text == null) {
            return "null";
        }
        int index = text.indexOf('\n');
        return index < 0 ? text : text.substring(0, index);
    }

    /**
     * 记录一条分节标题。
     *
     * @param text 标题
     */
    private static void say(String text) {
        System.out.println();
        System.out.println("--- " + text);
    }

    /**
     * 断言并计数。
     *
     * @param desc      断言描述
     * @param condition 条件
     * @param detail    细节（失败时打印）
     */
    private static void check(String desc, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + desc);
        } else {
            failed++;
            System.out.println("  [FAIL] " + desc + "  细节：" + detail);
        }
    }
}
