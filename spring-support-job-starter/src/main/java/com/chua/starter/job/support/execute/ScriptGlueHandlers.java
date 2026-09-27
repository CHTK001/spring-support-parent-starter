package com.chua.starter.job.support.execute;

import com.chua.common.support.task.script.ScriptFlow;
import com.chua.starter.job.support.glue.GlueTypes;
import com.chua.starter.job.support.glue.JobObjectContext;
import com.chua.starter.job.support.handler.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脚本任务的执行器侧处理器工厂。
 *
 * <p>脚本引擎走仓库公共 SPI：{@code com.chua.common.support.task.script.ScriptProvider}
 * （{@link ScriptFlow} 封装），当前仓库提供 {@code groovy} 实现
 * （utils-support-groovy-starter）。脚本正文随派发命令下发（Base64 单行），
 * 执行前落成本地临时文件（{@link ScriptFlow} 的 API 是文件路径式），
 * 按 任务名 + 版本时刻 缓存：glueAt 变化才重写文件，避免每拍落盘。</p>
 *
 * <p>引擎未安装（classpath 缺 ScriptProvider 实现）时抛出带引导语的可读异常，
 * 由 {@link JobExecutor} 记成 FAILURE，而不是静默跳过。</p>
 *
 * <p>脚本正文能拿 Spring bean：绑定上下文里带一个 {@link JobObjectContext}
 * （见 {@link #contextOf}），Groovy 引擎会把它绑成同名变量。
 * Java 代码片段类型（{@code GLUE_JAVA}）不在本类，由
 * {@link com.chua.starter.job.support.glue.JavaGlueHandlers} 承载。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class ScriptGlueHandlers {

    /**
     * 缓存：任务名 → 已落盘的脚本文件
     */
    private static final Map<String, CachedScript> CACHE = new ConcurrentHashMap<>();

    /**
     * 脚本文件目录（进程临时目录下），进程退出即清理
     */
    private static final Path SCRIPT_DIR =
            Path.of(System.getProperty("java.io.tmpdir"), "job-glue");

    /**
     * 创建 脚本胶水处理器 实例
     */
    private ScriptGlueHandlers() {
    }

    /**
     * 解析脚本任务的处理器。
     *
     * @param jobName             任务名（兼作脚本标识）
     * @param engine              脚本引擎名（SPI key，如 groovy）
     * @param source              脚本正文
     * @param glueAtMs            版本时刻（毫秒），null/0 时按正文哈希
     * @param executeParam        台账执行参数（脚本内以 {@code executeParam} 变量读取）
     * @param applicationContext  宿主容器，允许为 null（此时脚本不可取 bean）
     * @return 可执行处理器
     * @throws IllegalStateException 引擎未安装或脚本文件写入失败
     */
    public static JobHandler resolve(String jobName, String engine, String source, Long glueAtMs,
                                     String executeParam, ApplicationContext applicationContext) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalStateException("脚本任务缺少任务键");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("脚本任务缺少脚本正文: " + jobName);
        }
        long version = glueAtMs == null || glueAtMs <= 0 ? source.hashCode() : glueAtMs;

        // 提前探一次引擎：SPI 缺实现时给出可读引导，而不是等脚本文件落盘后才报
        ScriptFlow flow;
        try {
            flow = ScriptFlow.of(engineNameOf(engine));
        } catch (Throwable ex) {
            throw new IllegalStateException("脚本引擎 '" + engineNameOf(engine) + "' 不可用，请引入对应引擎依赖"
                    + "（如 utils-support-groovy-starter）: " + ex.getMessage(), ex);
        }

        Path scriptFile = CACHE.compute(jobName, (key, cached) -> {
            if (cached != null && cached.version() == version && Files.exists(cached.file())) {
                return cached;
            }
            try {
                Files.createDirectories(SCRIPT_DIR);
                Path file = SCRIPT_DIR.resolve(safeFileName(jobName) + "-" + version
                        + suffixOf(engineNameOf(engine)));
                Files.writeString(file, source, StandardCharsets.UTF_8);
                if (cached != null) {
                    Files.deleteIfExists(cached.file());
                }
                log.info("[job] 脚本已落盘 job={} engine={} version={} file={}",
                        jobName, engineNameOf(engine), version, file);
                return new CachedScript(version, file);
            } catch (IOException ex) {
                throw new IllegalStateException("脚本文件写入失败: " + ex.getMessage(), ex);
            }
        }).file();

        return handlerOf(jobName, flow, scriptFile, executeParam, applicationContext);
    }

    /**
     * 缓存脚本正文（调度中心 job-glue 主题推送落地方使用）。
     *
     * @param jobName             任务名
     * @param engine              脚本引擎名
     * @param source              脚本正文
     * @param glueAtMs            版本时刻
     * @param applicationContext  宿主容器，允许为 null
     */
    public static void store(String jobName, String engine, String source, Long glueAtMs,
                             ApplicationContext applicationContext) {
        resolve(jobName, engine, source, glueAtMs, null, applicationContext);
    }

    /**
     * 从本地缓存解析脚本处理器（派发命令只带版本号时使用）。
     *
     * <p>正文未同步（执行器刚重启、调度中心未推送成功）或版本不一致时给出可读失败，
     * 由执行器记成 FAILURE，而不是静默跳过。</p>
     *
     * @param jobName             任务名
     * @param engine              脚本引擎名
     * @param glueAtMs            期望版本时刻
     * @param executeParam        台账执行参数（脚本内以 {@code executeParam} 变量读取）
     * @param applicationContext  宿主容器，允许为 null
     * @return 可执行处理器
     * @throws IllegalStateException 正文未同步或引擎未安装
     */
    public static JobHandler resolveCached(String jobName, String engine, Long glueAtMs,
                                           String executeParam, ApplicationContext applicationContext) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalStateException("脚本任务缺少任务键");
        }
        var cached = CACHE.get(jobName);
        if (cached == null || !Files.exists(cached.file())) {
            throw new IllegalStateException("脚本正文未同步: " + jobName
                    + "，请在调度中心重新保存该任务，或等待执行器重连后自动同步");
        }
        long expected = glueAtMs == null ? 0L : glueAtMs;
        if (expected > 0 && cached.version() != expected) {
            throw new IllegalStateException("脚本版本未同步: " + jobName
                    + " 期望 glueAt=" + expected + "，本地=" + cached.version());
        }
        ScriptFlow flow;
        try {
            flow = ScriptFlow.of(engineNameOf(engine));
        } catch (Throwable ex) {
            throw new IllegalStateException("脚本引擎 '" + engineNameOf(engine) + "' 不可用，请引入对应引擎依赖"
                    + "（如 utils-support-groovy-starter）: " + ex.getMessage(), ex);
        }
        return handlerOf(jobName, flow, cached.file(), executeParam, applicationContext);
    }

    /**
     * 构造脚本处理器。
     *
     * @param jobName             任务名
     * @param flow                已解析的脚本引擎
     * @param file                脚本文件
     * @param executeParam        执行参数（脚本内以 {@code executeParam} 变量读取）
     * @param applicationContext  宿主容器，允许为 null
     * @return 处理器
     */
    private static JobHandler handlerOf(String jobName, ScriptFlow flow, Path file, String executeParam,
                                        ApplicationContext applicationContext) {
        String param = executeParam == null ? "" : executeParam;
        return new JobHandler() {
            @Override
            public String name() {
                return jobName;
            }

            @Override
            public void execute() {
                flow.execute(file, contextOf(jobName, param, applicationContext));
            }
        };
    }

    /**
     * 组装脚本执行上下文。
     *
     * <p>{@code GroovyScriptProvider} 会把这里的每个条目逐个绑成 Groovy 变量，
     * 所以 {@code objectContext} 在脚本里就是可直接调用的对象，无需 import。</p>
     *
     * @param jobName             任务名
     * @param executeParam        执行参数
     * @param applicationContext  宿主容器，允许为 null
     * @return 绑定上下文
     */
    private static Map<String, Object> contextOf(String jobName, String executeParam,
                                                 ApplicationContext applicationContext) {
        var context = new java.util.LinkedHashMap<String, Object>(4);
        context.put("jobName", jobName);
        context.put("executeParam", executeParam);
        context.put("objectContext",
                new JobObjectContext(applicationContext, jobName, jobName, executeParam));
        return context;
    }

    /**
     * 归一脚本引擎名：{@code GLUE_GROOVY} 与裸值 {@code groovy} 等价，
     * 空白/空值按 {@code groovy} 处理（与本类历史行为一致）。
     *
     * @param engine 原始执行类型，允许为 null
     * @return SPI 引擎名
     */
    private static String engineNameOf(String engine) {
        var name = GlueTypes.engineOf(engine);
        return name.isEmpty() ? GlueTypes.ENGINE_GROOVY : name;
    }

    /**
     * 文件名安全化：非字母数字点线替换为下划线。
     *
     * @param value 原始名
     * @return 安全文件名
     */
    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * 按引擎名推断脚本文件后缀。
     *
     * @param engine 引擎名
     * @return 后缀（含点）
     */
    private static String suffixOf(String engine) {
        return switch (engine.toLowerCase()) {
            case "groovy" -> ".groovy";
            case "js", "javascript" -> ".js";
            case "python", "py" -> ".py";
            case "shell", "sh" -> ".sh";
            default -> ".script";
        };
    }

    /**
     * 已落盘脚本缓存项。
     *
     * @param version 版本时刻
     * @param file    脚本文件
     */
    private record CachedScript(long version, Path file) {
    }
}
