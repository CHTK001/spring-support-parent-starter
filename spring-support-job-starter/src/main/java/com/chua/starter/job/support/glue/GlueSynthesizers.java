// [P3C A20 豁免] A20/A30/A31: 仅含常量定义行，豁免。
package com.chua.starter.job.support.glue;

import java.util.Locale;

import com.chua.common.support.spi.ServiceProvider;

/**
 * 片段合成入口：按引擎名挑 {@link GlueSynthesizer}，并统一把关「只合成片段」。
 *
 * <p>调用方（{@code ScriptGlueHandlers}）无条件调本类即可，不需要自己判断片段 / 脚本：
 * 非片段类型原样返回；片段才按引擎选合成器。</p>
 *
 * <p>取不到对应引擎的实现时回落 {@link PassThroughGlueSynthesizer}（原样下发）而不是抛异常：
 * 本模块不认识的新脚本引擎，其语法错误由引擎自己报更准确；合成器缺失不应阻断执行。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class GlueSynthesizers {

    /**
     * SPI 键：透传
     */
    public static final String KEY_DEFAULT = "default";

    /**
     * 工具类，不实例化。
     */
    private GlueSynthesizers() {
    }

    /**
     * 把片段合成为完整脚本。
     *
     * <p>非片段类型（脚本 / 对象）原样返回，调用方可以无条件调用本方法。</p>
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
        String engine = GlueTypes.engineOf(glueType);
        if (engine == null || engine.isBlank()) {
            return PassThroughGlueSynthesizer.INSTANCE.synthesize(source, jobKey);
        }
        GlueSynthesizer synthesizer = resolve(engine.toLowerCase(Locale.ROOT));
        return synthesizer.synthesize(source, jobKey);
    }

    /**
     * 按引擎名取合成器。
     *
     * <p>SPI 取到的实现若声称支持另一个引擎（键写错 / 注册重复），不采用它：
     * 合成规则与引擎必须绑定，错配会产出无法编译的源码。</p>
     *
     * @param engine 引擎名（小写）
     * @return 合成器，永不为 null
     */
    public static GlueSynthesizer resolve(String engine) {
        GlueSynthesizer spi = null;
        try {
            spi = ServiceProvider.of(GlueSynthesizer.class).getExtension(engine);
        } catch (RuntimeException ex) {
            // 注册缺失 / 类加载失败一律走透传兜底，合成器是纯增强项
        }
        if (spi != null && spi.supports(engine)) {
            return spi;
        }
        return PassThroughGlueSynthesizer.INSTANCE;
    }
}
