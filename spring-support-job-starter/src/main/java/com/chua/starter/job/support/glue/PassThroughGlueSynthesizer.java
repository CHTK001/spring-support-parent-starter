// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.job.support.glue;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;

/**
 * 透传合成器：语言本身允许裸语句时，合成即原样下发。
 *
 * <p>Groovy / JavaScript 等脚本语言的「方法体」本身就是可执行脚本，包一层类反而会把
 * 用户代码变成不可执行的内容。所以本实现不做任何包装，{@link #synthesize(String, String)}
 * 直接返回正文。</p>
 *
 * <p>它同时是 {@link GlueSynthesizer} 的默认实现：新增了一个本模块还不认识的脚本引擎时，
 * 「原样下发」比「抛异常」更接近用户预期（引擎自己会给出语法错误）。注册名
 * {@code default} / {@code plain} / {@code passthrough}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@SpiDefault
@Spi({"default", "plain", "passthrough"})
public class PassThroughGlueSynthesizer implements GlueSynthesizer {

    /**
     * 无状态单例。
     */
    public static final PassThroughGlueSynthesizer INSTANCE = new PassThroughGlueSynthesizer();

    /**
     * 创建透传合成器。
     */
    public PassThroughGlueSynthesizer() {
    }

    @Override
    public String engine() {
        return "default";
    }

    @Override
    public String synthesize(String source, String jobKey) {
        return source;
    }
}
