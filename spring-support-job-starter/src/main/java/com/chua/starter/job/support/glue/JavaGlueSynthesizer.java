package com.chua.starter.job.support.glue;

import com.chua.common.support.spi.annotations.Spi;

/**
 * Java 片段合成器：把方法体包成 {@code public class} + {@code @Job} 入口方法。
 *
 * <p>类名由「任务标识 + 正文」共同决定（{@code Glue<hex>}），正文一变类名就变，
 * 天然避免旧类残留与缓存串味。入口方法统一声明 {@code throws Exception}：
 * 片段里做 IO、调外部服务必然要抛受检异常，不声明的话这些代码一行都编译不过。</p>
 *
 * <p>合成的类刻意放在 {@link GlueScriptSynthesizer#SYNTHETIC_PACKAGE} 下，
 * 与业务包隔离，避免与用户类同名。注册名 {@code java}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Spi("java")
public class JavaGlueSynthesizer implements GlueSynthesizer {

    /**
     * 片段正文缩进层级（类体内 + 方法体内）
     */
    private static final String INDENT = "    ";

    /**
     * 合成入口方法的异常声明
     */
    private static final String ENTRY_THROWS = " throws Exception";

    @Override
    public String engine() {
        return GlueTypes.ENGINE_JAVA;
    }

    @Override
    public String synthesize(String source, String jobKey) {
        String body = source == null ? "" : source;
        String className = syntheticClassName(jobKey, body);
        return "package " + GlueScriptSynthesizer.SYNTHETIC_PACKAGE + ";\n"
                + "\n"
                + "import com.chua.starter.job.support.annotation.Job;\n"
                + "import com.chua.starter.job.support.glue.JobObjectContext;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.core.env.Environment;\n"
                + "\n"
                + "/**\n"
                + " * 由 GlueScriptSynthesizer 合成，请勿手改：类名/入口方法由后端生成，\n"
                + " * 用户只提供 " + GlueScriptSynthesizer.ENTRY_METHOD + " 的方法体。\n"
                + " */\n"
                + "public class " + className + " {\n"
                + "\n"
                + INDENT + "/** 宿主环境，验证字段自动装配可用 */\n"
                + INDENT + "@Autowired\n"
                + INDENT + "private Environment environment;\n"
                + "\n"
                + INDENT + "@Job(\"" + escape(jobKey) + "\")\n"
                + INDENT + "public void " + GlueScriptSynthesizer.ENTRY_METHOD
                + "(JobObjectContext objectContext)" + ENTRY_THROWS + " {\n"
                + indent(body)
                + INDENT + "}\n"
                + "}\n";
    }

    /**
     * 生成合成类名：任务标识 + 正文共同决定，正文一变类名就变，天然避免旧类残留。
     *
     * @param jobKey 任务标识
     * @param source 正文
     * @return 合成类名（不含包名）
     */
    private static String syntheticClassName(String jobKey, String source) {
        String seed = (jobKey == null ? "" : jobKey) + '|' + source;
        return GlueScriptSynthesizer.SYNTHETIC_CLASS_PREFIX + Integer.toHexString(seed.hashCode());
    }

    /**
     * 给多行正文统一加一级缩进，保持合成结果可读。
     *
     * @param body 正文
     * @return 缩进后的正文
     */
    private static String indent(String body) {
        StringBuilder builder = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            builder.append(INDENT).append(INDENT).append(line).append('\n');
        }
        return builder.toString();
    }

    /**
     * 转义 Java 字符串字面量里的反斜杠与引号。
     *
     * @param value 原值，允许为 null
     * @return 可安全放进双引号字面量的文本
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
