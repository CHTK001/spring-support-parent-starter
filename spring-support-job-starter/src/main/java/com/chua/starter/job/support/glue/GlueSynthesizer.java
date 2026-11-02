package com.chua.starter.job.support.glue;

/**
 * 片段合成扩展点（SPI）：把「只写方法体」的片段包成引擎可直接执行的完整脚本。
 *
 * <p>为什么需要合成：{@code java} 引擎最终交给编译器的是<b>完整类源码</b>，而用户在片段里只写
 * {@code @Job} 方法内部那几行。合成这一步把用户的注意力锚定在业务本身，类名、包名、
 * 入口方法签名、预置 import 都由后端决定，避免用户写错类名导致编译失败。</p>
 *
 * <p>合成规则按引擎而异，所以它天然是扩展点：</p>
 * <ul>
 *   <li>{@link JavaGlueSynthesizer}（{@code java}）&mdash; 包一层 public 类 + {@code @Job} 入口方法；</li>
 *   <li>{@link PassThroughGlueSynthesizer}（{@code default} / {@code plain}）&mdash; 语言本身允许裸语句
 *       （Groovy 等），合成即<b>透传</b>。</li>
 * </ul>
 *
 * <p>注册文件：{@code META-INF/extensions/com.chua.starter.job.support.glue.GlueSynthesizer}。
 * 新增脚本引擎时，实现本接口并登记一个与引擎名同名的键即可；键取不到时由
 * {@link GlueSynthesizers} 回落到透传实现（合成失败不应阻断执行）。</p>
 *
 * <p>合成只发生在片段类型（{@link GlueTypes#isFragment(String)}）上；脚本类型是完整文件，
 * 一律原样下发 —— 这条判据在 {@link GlueSynthesizers#synthesize(String, String, String)}
 * 里统一把关，实现类只负责自己那一种引擎的包装规则。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface GlueSynthesizer {

    /**
     * 本实现对应的脚本引擎名（{@code GlueTypes#engineOf(String)} 的返回值）。
     *
     * @return SPI 键，如 {@code java} / {@code groovy}
     */
    String engine();

    /**
     * 把片段合成为完整脚本。
     *
     * @param source 用户写的正文（方法体或裸语句）
     * @param jobKey 任务标识，参与合成类名与入口注解
     * @return 可直接交给引擎执行的源码
     */
    String synthesize(String source, String jobKey);

    /**
     * 是否支持该引擎。
     *
     * @param engineName 引擎名（已归一为小写）
     * @return 支持返回 true
     */
    default boolean supports(String engineName) {
        return engine() != null && engine().equalsIgnoreCase(engineName);
    }
}
