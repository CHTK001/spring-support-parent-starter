package com.chua.starter.job.support.glue;

/**
 * 片段合成的兼容门面：保留原有静态常量与 {@link #synthesize(String, String, String)} 入口。
 *
 * <p>合成规则本身已下沉为 {@link GlueSynthesizer} SPI 扩展点（{@link JavaGlueSynthesizer} /
 * {@link PassThroughGlueSynthesizer}），本类只做委托，使既有调用点与常量引用无需改动。</p>
 *
 * @since 4.0.0.43
 * @author CH
 * @see GlueSynthesizers
 */
public final class GlueScriptSynthesizer {

    /**
     * 合成类所在包，刻意与业务包隔离，避免与用户类同名
     */
    public static final String SYNTHETIC_PACKAGE = "com.chua.job.dynamic.glue";

    /**
     * 合成类名前缀
     */
    public static final String SYNTHETIC_CLASS_PREFIX = "Glue";

    /**
     * 合成入口方法名（Java 引擎的三级回退里也认这个名）
     */
    public static final String ENTRY_METHOD = "execute";

    /**
     * 创建 片段合成器 实例
     */
    private GlueScriptSynthesizer() {
    }

    /**
     * 把片段合成为完整脚本。
     *
     * <p>非片段类型（脚本/对象）原样返回，调用方可以无条件调用本方法。</p>
     *
     * @param glueType 执行类型
     * @param source   用户写的正文
     * @param jobKey   任务标识，参与合成类名与入口注解
     * @return 可直接交给引擎执行的完整源码
     */
    public static String synthesize(String glueType, String source, String jobKey) {
        return GlueSynthesizers.synthesize(glueType, source, jobKey);
    }
}
