package com.chua.starter.job.support.glue;

/**
 * 片段合成器：把「只写方法体」的片段包成可以被 {@code ScriptProvider} 直接执行的完整脚本。
 *
 * <p>为什么需要合成：{@code java} 引擎最终交给 {@code JdkCompiler} 的是**完整类源码**，
 * 而用户在片段里只写 {@code @Job} 方法内部那几行。合成这一步把用户的注意力锚定在业务本身，
 * 类名、包名、入口方法签名、预置 import 都由后端决定，避免用户写错类名导致编译失败。</p>
 *
 * <p>各引擎的合成规则：</p>
 * <ul>
 *   <li>java：包一层 public 类 + {@code @Job} 入口方法，方法体即用户片段；</li>
 *   <li>groovy 及其它脚本引擎：语言本身允许裸语句，合成即**透传**，不做包装。</li>
 * </ul>
 *
 * <p>合成只发生在片段类型（{@link GlueTypes#isFragment(String)}）上；脚本类型是完整文件，
 * 一律原样下发。</p>
 *
 * @since 4.0.0.43
 * @author CH
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
     * 片段正文缩进层级（类体内 + 方法体内）
     */
    private static final String INDENT = "    ";

    /**
     * 合成入口方法的异常声明。
     *
     * <p>片段里做 IO、调外部服务必然要抛受检异常，方法不声明 {@code throws} 时
     * 这些代码一行都编译不过——那等于把可用性缺口留给用户。这里统一声明
     * {@code throws Exception}，由执行器捕获并记成任务失败。</p>
     */
    private static final String ENTRY_THROWS = " throws Exception";

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
        if (!GlueTypes.isFragment(glueType)) {
            return source;
        }
        if (GlueTypes.isJavaEngine(glueType)) {
            return synthesizeJava(source, jobKey);
        }
        // Groovy 等脚本语言允许裸语句，方法体本身就是可执行脚本，不需要包装
        return source;
    }

    /**
     * 合成 Java 类源码。
     *
     * @param fragment 用户写的方法体
     * @param jobKey   任务标识
     * @return 完整类源码
     */
    private static String synthesizeJava(String fragment, String jobKey) {
        String body = fragment == null ? "" : fragment;
        String className = syntheticClassName(jobKey, body);
        return "package " + SYNTHETIC_PACKAGE + ";\n"
                + "\n"
                + "import com.chua.starter.job.support.annotation.Job;\n"
                + "import com.chua.starter.job.support.glue.JobObjectContext;\n"
                + "import org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.core.env.Environment;\n"
                + "\n"
                + "/**\n"
                + " * 由 GlueScriptSynthesizer 合成，请勿手改：类名/入口方法由后端生成，\n"
                + " * 用户只提供 " + ENTRY_METHOD + " 的方法体。\n"
                + " */\n"
                + "public class " + className + " {\n"
                + "\n"
                + INDENT + "/** 宿主环境，验证字段自动装配可用 */\n"
                + INDENT + "@Autowired\n"
                + INDENT + "private Environment environment;\n"
                + "\n"
                + INDENT + "@Job(\"" + escape(jobKey) + "\")\n"
                + INDENT + "public void " + ENTRY_METHOD + "(JobObjectContext objectContext)" + ENTRY_THROWS + " {\n"
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
        return SYNTHETIC_CLASS_PREFIX + Integer.toHexString(seed.hashCode());
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
