package com.chua.starter.job.support.glue;

import com.chua.starter.job.support.dispatch.JobSpec;

/**
 * 任务执行类型（胶水类型）常量与归一规则。
 *
 * <p>{@code sys_job_glue_type} 是一个自由字符串列，本类把它的值域收敛成三类，
 * 与 {@code sys_job_glue_source} 的正文形态一一对应：</p>
 * <ul>
 *   <li>{@link #BEAN}：从已注册执行器对象（{@code @Job} 注解方法）里挑一个执行，正文为空；</li>
 *   <li>{@link #JAVA}：完整 Java 类源码片段，编译成类后反射执行，字段上可 {@code @Autowired}；</li>
 *   <li>{@link #GROOVY}：Groovy 脚本片段，等价于历史的裸值 {@code groovy}，走
 *       {@code ScriptProvider} SPI 执行，正文以 {@code objectContext} 变量取 Spring bean。</li>
 * </ul>
 *
 * <p>裸值 {@code groovy} 继续可用（存量台账不迁移），{@link #engineOf} 会把它
 * 归一成 SPI 引擎名；{@link #JAVA} 不走 SPI——JDK 编译器不是 {@code ScriptProvider}，
 * 由 {@code com.chua.starter.job.support.glue.compile.JavaGlueCompiler} 单独承载。</p>
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
     * Java 代码片段：完整类源码，编译后反射调用入口方法
     */
    public static final String JAVA = "GLUE_JAVA";

    /**
     * Groovy 脚本片段：{@code GLUE_} 前缀形态，与裸值 {@code groovy} 等价
     */
    public static final String GROOVY = "GLUE_GROOVY";

    /**
     * Groovy 引擎的 SPI 注册名（{@code ScriptProvider#engineName()}）
     */
    public static final String ENGINE_GROOVY = "groovy";

    /**
     * 归一后的执行类型
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
     * 是否 Java 代码片段类型。
     *
     * @param glueType 原始执行类型，允许为 null
     * @return 是 Java 片段返回 true
     */
    public static boolean isJava(String glueType) {
        return JAVA.equalsIgnoreCase(normalize(glueType));
    }

    /**
     * 是否代码片段类型（脚本或 Java 片段），即非对象类型。
     *
     * @param glueType 原始执行类型，允许为 null
     * @return 是片段类型返回 true
     */
    public static boolean isGlue(String glueType) {
        return !isBean(glueType);
    }

    /**
     * 取脚本引擎的 SPI 注册名。
     *
     * <p>{@link #GROOVY} 与裸值 {@code groovy} 归一为 {@link #ENGINE_GROOVY}；
     * 对象与 Java 片段不是脚本引擎，返回空串。</p>
     *
     * @param glueType 原始执行类型，允许为 null
     * @return SPI 引擎名；非脚本类型返回空串
     */
    public static String engineOf(String glueType) {
        String value = normalize(glueType);
        if (isBean(value) || isJava(value)) {
            return "";
        }
        return GROOVY.equalsIgnoreCase(value) ? ENGINE_GROOVY : value;
    }

    /**
     * 创建 胶水类型 常量集合 实例
     */
    private GlueTypes() {
    }
}
