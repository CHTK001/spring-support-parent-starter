package com.chua.starter.job.support.glue;

import com.chua.starter.job.support.dispatch.JobSpec;

/**
 * 任务执行类型（胶水类型）常量与归一规则。
 *
 * <p>{@code sys_job_glue_type} 是一个自由字符串列，本类把它的值域收敛成三类：</p>
 * <ul>
 *   <li>{@link #BEAN}：对象——从已注册执行器对象（{@code @Job} 注解方法）里挑一个执行，正文为空；</li>
 *   <li>裸引擎名（如 {@code java} / {@code groovy}）：脚本——用户写**完整文件**，
 *       保存时由引擎预编译校验，执行时复用同一份编译产物，正文直接交给
 *       {@code ScriptProvider} SPI；</li>
 *   <li>{@link #FRAGMENT_PREFIX} 开头（如 {@link #JAVA} / {@link #GROOVY}）：片段——用户只写
 *       {@code @Job} 里的**方法体部分**，由 {@link GlueScriptSynthesizer} 合成为完整脚本后再走
 *       同一条 SPI 与同一份缓存。</li>
 * </ul>
 *
 * <p>「含 {@code GLUE_} 前缀就是片段」是本类的核心判据：前缀既是历史命名，也恰好承担
 * 「需要合成」的语义，因此不需要再加一列。</p>
 *
 * <p>两类都走仓库公共 SPI {@code com.chua.common.support.task.script.ScriptProvider}，
 * 引擎实现在 utils 层（groovy 在 {@code utils-support-groovy-starter}，java 在
 * {@code utils-support-common-starter}），本类不再持有任何编译器。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public final class GlueTypes {

    /**
     * 对象：从已注册执行器对象（Bean 处理器）里选一个执行，任务键即处理器名
     *
     * <p>与 {@link JobSpec#GLUE_BEAN} 同值，此处引用而不重定义，避免两处常量漂移。</p>
     */
    public static final String BEAN = JobSpec.GLUE_BEAN;

    /**
     * 片段类型前缀：带此前缀表示用户只写方法体，需要后端合成完整脚本
     */
    public static final String FRAGMENT_PREFIX = "GLUE_";

    /**
     * Java 片段：用户只写方法体，后端合成为完整类源码
     */
    public static final String JAVA = "GLUE_JAVA";

    /**
     * Groovy 片段：用户只写脚本正文，Groovy 本身允许裸语句，合成即透传
     */
    public static final String GROOVY = "GLUE_GROOVY";

    /**
     * Java 引擎的 SPI 注册名（{@code ScriptProvider#engineName()}）
     */
    public static final String ENGINE_JAVA = "java";

    /**
     * Groovy 引擎的 SPI 注册名（{@code ScriptProvider#engineName()}）
     */
    public static final String ENGINE_GROOVY = "groovy";

    /**
     * 创建 胶水类型 常量集合 实例
     */
    private GlueTypes() {
    }

    /**
     * 归一后的执行类型。
     *
     * @param glueType 原始执行类型，允许为 null
     * @return 去空白后的值；空白与 null 统一按 {@link #BEAN} 处理
     */
    public static String normalize(String glueType) {
        String value = glueType == null ? "" : glueType.trim();
        return value.isEmpty() ? BEAN : value;
    }

    /**
     * 是否对象（Bean 处理器）类型。
     *
     * @param glueType 原始执行类型，允许为 null
     * @return 是对象类型返回 true
     */
    public static boolean isBean(String glueType) {
        return BEAN.equalsIgnoreCase(normalize(glueType));
    }

    /**
     * 是否片段类型（值以 {@code GLUE_} 开头，需要合成完整脚本）。
     *
     * @param glueType 原始执行类型，允许为 null
     * @return 是片段返回 true
     */
    public static boolean isFragment(String glueType) {
        return normalize(glueType).toUpperCase().startsWith(FRAGMENT_PREFIX);
    }

    /**
     * 是否脚本类型（完整文件，非对象且非片段）。
     *
     * @param glueType 原始执行类型，允许为 null
     * @return 是脚本返回 true
     */
    public static boolean isScriptFile(String glueType) {
        String value = normalize(glueType);
        return !isBean(value) && !isFragment(value);
    }

    /**
     * 是否代码片段任务（脚本或片段），即非对象类型。
     *
     * <p>保留旧名以兼容既有调用点：语义仍是「需要正文的任务」。</p>
     *
     * @param glueType 原始执行类型，允许为 null
     * @return 需要正文的任务返回 true
     */
    public static boolean isGlue(String glueType) {
        return !isBean(glueType);
    }

    /**
     * 是否 Java 引擎（{@code java} 脚本或 {@link #JAVA} 片段）。
     *
     * @param glueType 原始执行类型，允许为 null
     * @return 归一到 java 引擎返回 true
     */
    public static boolean isJavaEngine(String glueType) {
        return ENGINE_JAVA.equalsIgnoreCase(engineOf(glueType));
    }

    /**
     * 取脚本引擎的 SPI 注册名。
     *
     * <p>{@link #JAVA} / {@link #GROOVY} 去掉 {@code GLUE_} 前缀，裸引擎名原样返回；
     * 对象类型不是脚本引擎，返回空串。</p>
     *
     * @param glueType 原始执行类型，允许为 null
     * @return SPI 引擎名；对象类型返回空串
     */
    public static String engineOf(String glueType) {
        String value = normalize(glueType);
        if (isBean(value)) {
            return "";
        }
        String upper = value.toUpperCase();
        String engine = upper.startsWith(FRAGMENT_PREFIX)
                ? value.substring(FRAGMENT_PREFIX.length())
                : value;
        return engine.isBlank() ? "" : engine.toLowerCase();
    }
}
