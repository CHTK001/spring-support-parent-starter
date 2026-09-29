package com.chua.starter.job.support.execute;

import com.chua.common.support.task.script.ScriptFlow;
import com.chua.common.support.task.script.ScriptProviders;
import com.chua.starter.job.support.glue.GlueScriptSynthesizer;
import com.chua.starter.job.support.glue.GlueTypes;
import com.chua.starter.job.support.glue.JobObjectContext;
import com.chua.starter.job.support.glue.JobScriptHost;
import com.chua.starter.job.support.handler.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 片段任务的执行器侧处理器工厂（脚本与片段共用一条链路）。
 *
 * <p>链路只有一条：</p>
 * <ol>
 *   <li>片段类型（{@code GLUE_*}）先用 {@link GlueScriptSynthesizer} 合成完整脚本；
 *       脚本类型（裸引擎名）本身就是完整文件，原样使用；</li>
 *   <li>正文按 {@code 任务名 + 版本} 落成临时文件；</li>
 *   <li>{@link ScriptFlow#load(Path)} 做**预编译并缓存编译产物**——保存期校验走的就是这一步；</li>
 *   <li>编译成功后**立刻删掉那个临时文件**：两个引擎执行时都只读内存里的编译产物
 *       （{@code JavaScriptProvider} 的 {@code Compiled}、{@code GroovyScriptProvider} 的
 *       {@code classCache}），不会回读文件，落盘内容对执行已无用；</li>
 *   <li>{@link ScriptFlow#execute(Path, Object)} 以文件路径为键复用上一步的同一份编译产物执行，
 *       不再重复编译、不新建第二个引擎对象。</li>
 * </ol>
 *
 * <p>第 4 步是刻意的：状态一律以「引擎缓存里的编译产物」为准，不以落盘文件为准。
 * 保留文件会带来两个真问题——单节点下「保存期校验写了文件、任务最终没建成功」会留下无主孤儿；
 * 集群下执行器节点收到删除请求的概率本就低，文件更会永久残留。删掉之后
 * 「哪个节点负责回收脚本文件」这个问题就不存在了，跨节点删除通知、磁盘对账都不再需要。</p>
 *
 * <p>引擎一律经仓库公共 SPI {@code ScriptProvider} 解析（见 {@link ScriptFlow}），
 * 本类不再区分 Java 与脚本两条链路。引擎未安装时抛出带引导语的可读异常，
 * 由 {@link JobExecutor} 记成 FAILURE，而不是静默跳过。</p>
 *
 * <p>正文能拿 Spring bean：绑定上下文里带 {@link JobObjectContext}（Groovy 引擎绑成同名变量）
 * 与 {@link JobScriptHost}（Java 引擎靠它做字段装配）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class ScriptGlueHandlers {

    /**
     * 缓存：任务名 → 编译产物的键（文件路径）、版本与生效引擎
     *
     * <p>文件本身在编译成功后即被删除，路径只作为引擎缓存的键使用。</p>
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
     * 解析片段任务的处理器（命令带正文时调用）。
     *
     * @param jobName            任务名（兼作脚本标识）
     * @param glueType           执行类型（{@code GLUE_*} 片段或裸引擎名脚本）
     * @param source             正文
     * @param glueAtMs           版本时刻（毫秒），null/0 时按正文哈希
     * @param executeParam       台账执行参数（片段内以 {@code executeParam} 变量读取）
     * @param applicationContext 宿主容器，允许为 null（此时片段不可取 bean）
     * @return 可执行处理器
     * @throws IllegalStateException 正文缺失、引擎未安装、编译失败或文件写入失败
     */
    public static JobHandler resolve(String jobName, String glueType, String source, Long glueAtMs,
                                     String executeParam, ApplicationContext applicationContext) {
        Prepared prepared = prepare(jobName, glueType, source, glueAtMs);
        return handlerOf(jobName, prepared, executeParam, applicationContext);
    }

    /**
     * 从本地缓存解析处理器（派发命令只带版本号时使用）。
     *
     * <p>正文未同步（执行器刚重启、调度中心未推送成功）或版本不一致时给出可读失败，
     * 由执行器记成 FAILURE，而不是静默跳过。</p>
     *
     * @param jobName            任务名
     * @param glueType           执行类型
     * @param glueAtMs           期望版本时刻
     * @param executeParam       台账执行参数
     * @param applicationContext 宿主容器，允许为 null
     * @return 可执行处理器
     * @throws IllegalStateException 正文未同步或引擎未安装
     */
    public static JobHandler resolveCached(String jobName, String glueType, Long glueAtMs,
                                           String executeParam, ApplicationContext applicationContext) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalStateException("片段任务缺少任务键");
        }
        var cached = CACHE.get(jobName);
        // 判据是「本地有没有这份编译产物」，不是「落盘文件在不在」——文件在编译成功后
        // 就被删掉了，它不再代表任何状态。缓存条目在，才说明调度中心的正文同步到过本节点。
        if (cached == null) {
            throw new IllegalStateException("片段正文未同步: " + jobName
                    + "（本节点没有该任务的编译产物），请在调度中心重新保存该任务，或等待执行器重连后自动同步");
        }
        long expected = glueAtMs == null ? 0L : glueAtMs;
        if (expected > 0 && cached.version() != expected) {
            throw new IllegalStateException("片段版本未同步: " + jobName
                    + " 期望 glueAt=" + expected + "，本地=" + cached.version());
        }
        Prepared prepared = new Prepared(engineNameOf(cached.glueType()), cached.glueType(), cached.file());
        return handlerOf(jobName, prepared, executeParam, applicationContext);
    }

    /**
     * 缓存正文（调度中心 job-glue 主题推送落地方使用）。
     *
     * @param jobName            任务名
     * @param glueType           执行类型
     * @param source             正文
     * @param glueAtMs           版本时刻
     * @param applicationContext 宿主容器，允许为 null
     */
    public static void store(String jobName, String glueType, String source, Long glueAtMs,
                             ApplicationContext applicationContext) {
        prepare(jobName, glueType, source, glueAtMs);
    }

    /**
     * 保存期校验：合成 + 落盘 + 预编译，编译失败抛可读异常。
     *
     * <p>刻意复用 {@link #prepare} 的落盘路径：校验与执行命中**同一个文件**，
     * 因而命中同一份编译产物缓存，不会为了校验而多编译一次。</p>
     *
     * <p>引擎不在本进程时跳过而不是报错：调度中心不一定装了所有脚本引擎，
     * 装不装是部署问题，不该让保存失败；真正编译不过才是要挡住的问题。</p>
     *
     * @param jobName  任务名
     * @param glueType 执行类型
     * @param source   正文
     * @param glueAtMs 版本时刻（毫秒），允许为 null
     * @throws IllegalStateException 正文缺失或编译失败
     */
    public static void validate(String jobName, String glueType, String source, Long glueAtMs) {
        String engine = engineNameOf(glueType);
        if (ScriptProviders.resolve(engine) == null) {
            log.warn("[job] 保存校验跳过：本进程未安装脚本引擎 job={} engine={}", jobName, engine);
            return;
        }
        prepare(jobName, glueType, source, glueAtMs);
    }

    /**
     * 合成、落盘、预编译，返回可直接执行的准备结果。
     *
     * @param jobName  任务名
     * @param glueType 执行类型
     * @param source   正文
     * @param glueAtMs 版本时刻（毫秒），允许为 null
     * @return 准备结果
     * @throws IllegalStateException 正文缺失、引擎未安装、编译失败或文件写入失败
     */
    private static Prepared prepare(String jobName, String glueType, String source, Long glueAtMs) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalStateException("片段任务缺少任务键");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("片段任务缺少正文: " + jobName);
        }
        String engine = engineNameOf(glueType);
        String synthesized = GlueScriptSynthesizer.synthesize(glueType, source, jobName);
        long version = glueAtMs == null || glueAtMs <= 0 ? synthesized.hashCode() : glueAtMs;

        ScriptFlow flow = flowOf(engine);
        Path scriptFile = CACHE.compute(jobName, (key, cached) -> {
            // 版本一致即命中。落盘文件在编译成功后就被删掉（见下方），拿"文件是否存在"
            // 当判据会让每次执行都重写 + 重编译。
            if (cached != null && cached.version() == version) {
                return cached;
            }
            try {
                Files.createDirectories(SCRIPT_DIR);
                Path file = SCRIPT_DIR.resolve(safeFileName(jobName) + "-" + version
                        + suffixOf(engine));
                Files.writeString(file, synthesized, StandardCharsets.UTF_8);
                if (cached != null) {
                    // 换版本了：先把旧文件的编译产物从引擎里卸载掉，再删文件。
                    // 不卸载的话 ScriptProvider 的缓存会一直持有旧版本的 Class 及其类加载器，
                    // Metaspace 只涨不落（脚本反复保存必然踩到）。
                    unloadQuietly(cached.glueType(), cached.file());
                    Files.deleteIfExists(cached.file());
                }
                log.info("[job] 片段已落盘 job={} engine={} fragment={} version={} file={}",
                        jobName, engine, GlueTypes.isFragment(glueType), version, file);
                return new CachedScript(version, glueType, file);
            } catch (IOException ex) {
                throw new IllegalStateException("片段文件写入失败: " + ex.getMessage(), ex);
            }
        }).file();

        // 预编译并缓存编译产物；执行时复用同一份，不重复编译
        try {
            flow.load(scriptFile);
        } catch (RuntimeException ex) {
            // 编译没过，保存必然失败。这份文件与缓存条目对谁都没用，而且任务没建成功，
            // 永远等不到删除来触发 release，必须当场丢弃，否则按任务数永久残留。
            discardQuietly(jobName, glueType, scriptFile);
            throw ex;
        }
        // 编译完成后落盘文件即无用：两个引擎执行时都只读内存里的编译产物
        // （JavaScriptProvider 的 Compiled / GroovyScriptProvider 的 classCache），
        // 不会回读文件。立刻删掉，落盘残留与「哪个节点负责回收」这个问题一并消失——
        // 集群下执行器节点曾因删除通知发不到而永久残留脚本文件。
        discardScriptFile(jobName, scriptFile);
        return new Prepared(engine, glueType, scriptFile);
    }

    /**
     * 编译成功后清理落盘脚本文件（只删文件，编译产物与缓存条目保留给后续执行复用）。
     *
     * <p>删不掉只告警：文件已经是可有可无的残留物，为此让整次准备失败不划算；
     * 残留文件会在下次同任务重新编译时被覆盖，或由 {@link #release(String)} 清掉。</p>
     *
     * @param jobName 任务名（仅用于日志）
     * @param file    编译所用的落盘文件
     */
    private static void discardScriptFile(String jobName, Path file) {
        try {
            if (Files.deleteIfExists(file)) {
                log.info("[job] 片段已编译并清理落盘文件 job={} file={}", jobName, file);
            }
        } catch (IOException ex) {
            log.warn("[job] 片段落盘文件清理失败 job={} file={} cause={}", jobName, file, ex.getMessage());
        }
    }

    /**
     * 丢弃一次准备失败的片段资源：只清当次这一份，之前能跑的那版不动。
     *
     * <p>保存期校验是先 prepare 再决定能不能存，编译不过时任务根本没进台账，
     * 不会有删除动作来释放它。这里按文件路径做条件删除，避免把上一版
     * 已经验证可用的片段一起清掉。</p>
     *
     * @param jobName  任务名
     * @param glueType 执行类型（用于还原引擎名）
     * @param file     当次准备出的文件
     */
    private static void discardQuietly(String jobName, String glueType, Path file) {
        try {
            CACHE.computeIfPresent(jobName, (key, cur) -> cur.file().equals(file) ? null : cur);
        } catch (RuntimeException ex) {
            log.warn("[job] 片段缓存清理失败 job={} file={} cause={}", jobName, file, ex.getMessage());
        }
        unloadQuietly(glueType, file);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("[job] 片段文件删除失败 file={} cause={}", file, ex.getMessage());
        }
        log.info("[job] 片段准备失败已丢弃 job={} file={}", jobName, file);
    }

    /**
     * 释放某个任务占用的脚本资源：引擎缓存里的编译产物与类加载器。
     *
     * <p>任务删除时必须调用。{@code ScriptProvider} 的缓存以「脚本文件路径」为键，
     * 而每次保存都会生成新版本路径；不主动 unload，旧版本的 Class 与其类加载器
     * 就会一直挂在引擎缓存里，Metaspace 只涨不落。</p>
     *
     * <p>落盘文件在编译成功后已被删除，这里再删一次是幂等的，顺带清掉升级前
     * 或历史异常留下的旧文件。</p>
     *
     * @param jobName 任务名，允许为 null
     */
    public static void release(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return;
        }
        var cached = CACHE.remove(jobName.trim());
        if (cached == null) {
            return;
        }
        unloadQuietly(cached.glueType(), cached.file());
        try {
            Files.deleteIfExists(cached.file());
        } catch (IOException ex) {
            log.warn("[job] 片段文件删除失败 job={} file={} cause={}", jobName, cached.file(), ex.getMessage());
        }
        log.info("[job] 片段资源已释放 job={} version={} file={}", jobName, cached.version(), cached.file());
    }

    /**
     * 从引擎卸载某个已落盘脚本，释放其编译产物与类加载器。
     *
     * <p>卸载失败只告警不抛：释放资源是「尽力而为」，不该把任务流程带崩。</p>
     *
     * @param glueType 执行类型（用于还原引擎名）
     * @param file     已落盘脚本文件
     */
    private static void unloadQuietly(String glueType, Path file) {
        try {
            flowOf(engineNameOf(glueType)).unload(file);
        } catch (Throwable ex) {
            log.warn("[job] 片段卸载失败 file={} cause={}", file, ex.getMessage());
        }
    }

    /**
     * 解析脚本引擎，缺实现时给出可读引导。
     *
     * @param engine 引擎名
     * @return 脚本引擎流程对象
     * @throws IllegalStateException 引擎未安装
     */
    private static ScriptFlow flowOf(String engine) {
        try {
            return ScriptFlow.of(engine);
        } catch (Throwable ex) {
            throw new IllegalStateException("脚本引擎 '" + engine + "' 不可用，请引入对应引擎依赖"
                    + "（groovy 用 utils-support-groovy-starter，java 已随 utils-support-common-starter 提供）"
                    + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * 构造处理器：执行时复用已缓存的编译产物。
     *
     * @param jobName            任务名
     * @param prepared           准备结果
     * @param executeParam       执行参数（片段内以 {@code executeParam} 变量读取）
     * @param applicationContext 宿主容器，允许为 null
     * @return 处理器
     */
    private static JobHandler handlerOf(String jobName, Prepared prepared, String executeParam,
                                        ApplicationContext applicationContext) {
        String param = executeParam == null ? "" : executeParam;
        ScriptFlow flow = flowOf(prepared.engine());
        // 编译产物在 prepare 时已进入引擎缓存，这里命中即无操作。命中不了（引擎缓存被清、
        // 而落盘文件已随编译删除）只能明确失败：原来还能靠回读文件静默恢复，现在不行了，
        // 所以把失败翻译成可读原因，别让引擎的 FileNotFound 冒到上层。
        try {
            flow.load(prepared.file());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("片段编译产物已失效: " + jobName
                    + "（本节点的引擎缓存里没有它），请在调度中心重新保存该任务以重新同步", ex);
        }
        return new JobHandler() {
            @Override
            public String name() {
                return jobName;
            }

            @Override
            public void execute() {
                flow.execute(prepared.file(), contextOf(jobName, param, applicationContext));
            }
        };
    }

    /**
     * 组装片段执行上下文。
     *
     * <p>Groovy 引擎会把这里的每个条目逐个绑成变量，所以 {@code objectContext} 在脚本里
     * 可直接调用；Java 引擎按形参类型/名绑定，并从 {@code scriptHost} 取装配能力。</p>
     *
     * @param jobName            任务名
     * @param executeParam       执行参数
     * @param applicationContext 宿主容器，允许为 null
     * @return 绑定上下文
     */
    private static Map<String, Object> contextOf(String jobName, String executeParam,
                                                 ApplicationContext applicationContext) {
        var context = new LinkedHashMap<String, Object>(4);
        context.put("jobName", jobName);
        context.put("executeParam", executeParam);
        context.put("objectContext",
                new JobObjectContext(applicationContext, jobName, jobName, executeParam));
        context.put("scriptHost", new JobScriptHost(applicationContext));
        return context;
    }

    /**
     * 归一脚本引擎名：{@code GLUE_GROOVY} 与裸值 {@code groovy} 都归一成 {@code groovy}。
     *
     * @param glueType 原始执行类型，允许为 null
     * @return SPI 引擎名
     */
    private static String engineNameOf(String glueType) {
        var name = GlueTypes.engineOf(glueType);
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
            case "java" -> ".java";
            case "groovy" -> ".groovy";
            case "js", "javascript" -> ".js";
            case "python", "py" -> ".py";
            case "shell", "sh" -> ".sh";
            default -> ".script";
        };
    }

    /**
     * 已落盘片段缓存项。
     *
     * @param version  版本时刻
     * @param glueType 执行类型（用于缓存命中时还原引擎）
     * @param file     片段文件
     */
    private record CachedScript(long version, String glueType, Path file) {
    }

    /**
     * 准备结果：引擎名、执行类型与已落盘文件。
     *
     * @param engine   引擎名
     * @param glueType 执行类型
     * @param file     已落盘并预编译的片段文件
     */
    private record Prepared(String engine, String glueType, Path file) {
    }
}
