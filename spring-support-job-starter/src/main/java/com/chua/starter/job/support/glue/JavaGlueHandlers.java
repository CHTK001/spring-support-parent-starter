package com.chua.starter.job.support.glue;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.glue.compile.JavaGlueCompiler;
import com.chua.starter.job.support.glue.compile.SourceCoordinates;
import com.chua.starter.job.support.handler.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java 胶水片段（{@code sys_job_glue_type = GLUE_JAVA}）的执行器侧处理器工厂。
 *
 * <p>与 {@link ScriptGlueHandlers} 的差别只在“怎么把正文变成对象”：脚本走
 * {@code ScriptProvider} SPI，Java 片段走 {@code javax.tools} 编译 + 类加载器反射。
 * 之后的生命周期完全一致——按「任务名 + 版本时刻」缓存编译产物，版本不变就不重编译。</p>
 *
 * <p>片段是一段完整的类源码，平台按下列顺序挑入口方法（与
 * {@link #ENTRY_EXECUTED} 常量一起构成约定）：</p>
 * <ol>
 *   <li>带 {@link Job} 注解的方法——这正是“把 {@code @Job} 那部分抄进片段”的形态；</li>
 *   <li>名为 {@code execute} 的方法；</li>
 *   <li>类里唯一一个 public 实例方法。</li>
 * </ol>
 *
 * <p>入口方法可以无参，也可以带一个 {@link JobObjectContext} 形参（形参类型
 * {@code Object} 或其父类同样接受）。实例化后走
 * {@link ApplicationContext#autowireBeanProperties}，所以字段上的
 * {@code @Autowired} 也能生效——与 {@link JobObjectContext#getBean} 两条路都在。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
public final class JavaGlueHandlers {

    /**
     * 入口方法名（与 {@link Job} 注解、{@code execute()} 构成三级回退）
     */
    public static final String ENTRY_EXECUTED = "execute";

    /**
     * 编译产物目录（进程临时目录下），进程退出即清理
     */
    private static final Path GLUE_DIR =
            Path.of(System.getProperty("java.io.tmpdir"), "job-glue-java");

    /**
     * 缓存：任务名 → 已编译的类
     */
    private static final Map<String, CachedClass> CACHE = new ConcurrentHashMap<>();

    /**
     * 解析 Java 片段任务的处理器。
     *
     * @param jobKey             任务键
     * @param source             片段源码（完整类）
     * @param glueAtMs           版本时刻（毫秒），null/0 时按源码哈希
     * @param executeParam       台账执行参数（经 {@link JobObjectContext#getExecuteParam()} 读取）
     * @param applicationContext 宿主容器，允许为 null（此时片段不可取 bean）
     * @return 可执行处理器
     * @throws IllegalStateException 源码非法或编译失败
     */
    public static JobHandler resolve(String jobKey, String source, Long glueAtMs,
                                     String executeParam, ApplicationContext applicationContext) {
        if (jobKey == null || jobKey.isBlank()) {
            throw new IllegalStateException("Java 片段任务缺少任务键");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("Java 片段任务缺少源码: " + jobKey);
        }
        long version = glueAtMs == null || glueAtMs <= 0 ? source.hashCode() : glueAtMs;
        CachedClass cached = CACHE.compute(jobKey, (key, previous) -> {
            if (previous != null && previous.version() == version && Files.isDirectory(previous.outputDir())) {
                return previous;
            }
            return build(key, source, version, previous);
        });
        log.info("[job] Java 片段处理器就绪 job={} class={} glueAt={}",
                jobKey, cached.binaryName(), version);
        return handlerOf(jobKey, cached, executeParam, applicationContext);
    }

    /**
     * 从本地缓存解析处理器（派发命令只带版本号时使用）。
     *
     * <p>源码未同步（执行器刚重启、调度中心未推送成功）或版本不一致时给出可读失败，
     * 由执行器记成 FAILURE，而不是静默跳过。</p>
     *
     * @param jobKey             任务键
     * @param glueAtMs           期望版本时刻
     * @param executeParam       台账执行参数
     * @param applicationContext 宿主容器，允许为 null
     * @return 可执行处理器
     * @throws IllegalStateException 源码未同步或版本不一致
     */
    public static JobHandler resolveCached(String jobKey, Long glueAtMs,
                                           String executeParam, ApplicationContext applicationContext) {
        if (jobKey == null || jobKey.isBlank()) {
            throw new IllegalStateException("Java 片段任务缺少任务键");
        }
        var cached = CACHE.get(jobKey);
        if (cached == null) {
            throw new IllegalStateException("Java 片段源码未同步: " + jobKey
                    + "，请在调度中心重新保存该任务，或等待执行器重连后自动同步");
        }
        long expected = glueAtMs == null ? 0L : glueAtMs;
        if (expected > 0 && cached.version() != expected) {
            throw new IllegalStateException("Java 片段版本未同步: " + jobKey
                    + " 期望 glueAt=" + expected + "，本地=" + cached.version());
        }
        return handlerOf(jobKey, cached, executeParam, applicationContext);
    }

    /**
     * 缓存片段源码（调度中心 job-glue 主题推送落地方使用）。
     *
     * @param jobKey             任务键
     * @param source             片段源码
     * @param glueAtMs           版本时刻
     * @param applicationContext 宿主容器，允许为 null
     */
    public static void store(String jobKey, String source, Long glueAtMs,
                             ApplicationContext applicationContext) {
        resolve(jobKey, source, glueAtMs, null, applicationContext);
    }

    /**
     * 编译源码并缓存，旧版本的产物目录随即删除。
     *
     * <p>旧类加载器不主动 {@code close()}：输出目录不是 jar，没有句柄占用；
     * 而关闭会让仍在执行中的上一拍加载不到尚未触发的类。</p>
     *
     * @param jobKey   任务键
     * @param source   片段源码
     * @param version  版本时刻
     * @param previous 旧缓存项，可为 null
     * @return 新缓存项
     */
    private static CachedClass build(String jobKey, String source, long version, CachedClass previous) {
        var coordinates = SourceCoordinates.of(source);
        var outputDir = GLUE_DIR.resolve(safeFileName(jobKey) + "-" + version);
        try {
            deleteQuietly(outputDir);
            Files.createDirectories(outputDir);
        } catch (IOException ex) {
            throw new UncheckedIOException("Java 片段产物目录不可写: " + outputDir, ex);
        }
        JavaGlueCompiler.compile(coordinates, source, outputDir);
        // 类加载器随缓存项一起持有，替换后由 GC 回收；刻意不 close()——
        // 关掉会让仍在执行中的上一拍加载不到尚未触发的类
        URLClassLoader loader;
        try {
            loader = new URLClassLoader(new URL[]{outputDir.toUri().toURL()},
                    Thread.currentThread().getContextClassLoader());
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Java 片段产物目录不可访问: " + outputDir, ex);
        }
        try {
            var type = Class.forName(coordinates.binaryName(), false, loader);
            return new CachedClass(version, outputDir, coordinates.binaryName(), loader, type,
                    EntryPoint.of(type));
        } catch (ClassNotFoundException | LinkageError ex) {
            closeQuietly(loader);
            throw new IllegalStateException("Java 片段类加载失败: " + coordinates.binaryName()
                    + " -> " + ex, ex);
        }
    }

    /**
     * 构造处理器：每次派发新建实例并做一次属性装配，实例状态不跨拍泄漏。
     *
     * @param jobKey             任务键
     * @param cached             已编译的类
     * @param executeParam       台账执行参数
     * @param applicationContext 宿主容器，允许为 null
     * @return 处理器
     */
    private static JobHandler handlerOf(String jobKey, CachedClass cached, String executeParam,
                                        ApplicationContext applicationContext) {
        return new JobHandler() {
            @Override
            public String name() {
                return jobKey;
            }

            @Override
            public void execute() throws Exception {
                var objectContext = new JobObjectContext(applicationContext, jobKey, jobKey, executeParam);
                var instance = newInstance(cached.type(), applicationContext);
                var args = cached.entry().takesContext()
                        ? new Object[]{objectContext}
                        : new Object[0];
                invoke(cached.entry().method(), instance, args);
            }
        };
    }

    /**
     * 实例化并做 {@code @Autowired} 属性装配。
     *
     * @param type                主类
     * @param applicationContext  宿主容器，允许为 null
     * @return 实例
     * @throws IllegalStateException 实例化失败
     */
    private static Object newInstance(Class<?> type, ApplicationContext applicationContext) {
        Object instance;
        try {
            instance = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Java 片段实例化失败（需要一个 public 无参构造器）: "
                    + type.getName(), ex);
        }
        if (applicationContext instanceof ConfigurableApplicationContext configurable) {
            // autowireBeanProperties 走 populateBean 分支，
            // AutowiredAnnotationBeanPostProcessor 在此处理 @Autowired 字段；
            // ApplicationContext 本身不继承 AutowireCapableBeanFactory，必须先取 beanFactory；
            // 第二参是 Spring 6.2 起的 int 版本，AUTOWIRE_BY_TYPE 才对得上 @Autowired 的按类型注入
            configurable.getBeanFactory().autowireBeanProperties(
                    instance, AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false);
        }
        return instance;
    }

    /**
     * 调用入口方法，把原始异常原样抛给 {@link com.chua.starter.job.support.execute.JobExecutor} 记 FAILURE。
     *
     * @param method   入口方法
     * @param instance 片段实例
     * @param args     实参
     * @throws Exception 片段抛出的异常
     */
    private static void invoke(Method method, Object instance, Object[] args) throws Exception {
        try {
            ReflectUtils.invokeProxy(instance, method, args);
        } catch (Throwable ex) {
            if (ex instanceof Exception exception) {
                throw exception;
            }
            if (ex instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Java 片段调用失败: " + ex, ex);
        }
    }

    /**
     * 关闭类加载器，只在加载失败这条冷路径上用到。
     *
     * @param loader 类加载器
     */
    private static void closeQuietly(URLClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ex) {
            log.debug("[job] 片段类加载器关闭失败 cause={}", ex.getMessage());
        }
    }

    /**
     * 递归删除目录，失败不阻断（残留只占临时目录空间，进程退出即清）。
     *
     * @param directory 目标目录
     */
    private static void deleteQuietly(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.debug("[job] 片段产物删除失败 path={} cause={}", path, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.debug("[job] 片段产物目录遍历失败 path={} cause={}", directory, ex.getMessage());
        }
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
     * 禁止实例化 Java 胶水处理器工厂
     */
    private JavaGlueHandlers() {
    }

    /**
     * 已编译的类及其入口方法。
     *
     * @param version    版本时刻
     * @param outputDir  class 输出目录
     * @param binaryName 主类二进制全名
     * @param loader     持有编译产物的类加载器
     * @param type       主类
     * @param entry      入口方法
     */
    private record CachedClass(long version, Path outputDir, String binaryName, URLClassLoader loader,
                               Class<?> type, EntryPoint entry) {
    }

    /**
     * 片段入口方法及其是否需要 {@link JobObjectContext} 形参。
     *
     * @param method        入口方法
     * @param takesContext  是否带上下文形参
     */
    private record EntryPoint(Method method, boolean takesContext) {

        /**
         * 按 {@code @Job} 注解 → {@code execute()} → 唯一 public 方法的顺序挑入口。
         *
         * <p>必须排除 {@code java.lang.Object} 自己声明的方法：{@code getMethods()} 会把
         * {@code wait/notify/equals/toString/...} 一并返回，片段作者写的入口通常无参，
         * 那样「唯一 public 方法」这条回退永远不成立（{@code wait()} 会先占一个坑），
         * 且报错信息会误导成「入口方法不唯一」。</p>
         *
         * @param type 主类
         * @return 入口
         * @throws IllegalStateException 候选不唯一或没有候选
         */
        static EntryPoint of(Class<?> type) {
            var candidates = Arrays.stream(type.getMethods())
                    .filter(method -> method.getDeclaringClass() != Object.class)
                    .filter(method -> !Modifier.isStatic(method.getModifiers()))
                    .filter(EntryPoint::isSupportedSignature)
                    .sorted(Comparator.comparing(Method::getName))
                    .toList();
            for (var method : candidates) {
                if (method.getAnnotation(Job.class) != null) {
                    return of(method);
                }
            }
            for (var method : candidates) {
                if (ENTRY_EXECUTED.equals(method.getName())) {
                    return of(method);
                }
            }
            if (candidates.size() == 1) {
                return of(candidates.getFirst());
            }
            if (candidates.isEmpty()) {
                throw new IllegalStateException("Java 片段没有可用入口方法: " + type.getName()
                        + " 需要一个 public 实例方法，可无参或带一个 JobObjectContext 形参");
            }
            throw new IllegalStateException("Java 片段入口方法不唯一: " + type.getName()
                    + " 请给其中一个方法加 @Job 注解，或把它命名为 execute()");
        }

        /**
         * 入口签名：无参，或单个 {@link JobObjectContext} 形参。
         *
         * @param method 候选方法
         * @return 签名受支持返回 true
         */
        private static boolean isSupportedSignature(Method method) {
            var parameters = method.getParameterTypes();
            if (parameters.length == 0) {
                return true;
            }
            return parameters.length == 1 && parameters[0].isAssignableFrom(JobObjectContext.class);
        }

        /**
         * 包装候选方法。
         *
         * @param method 候选方法
         * @return 入口点
         */
        private static EntryPoint of(Method method) {
            return new EntryPoint(method, method.getParameterCount() == 1);
        }
    }
}
