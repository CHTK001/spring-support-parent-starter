package com.chua.starter.account.spi;

import java.util.Set;

/**
 * 渠道描述层 SPI —— 最薄的一层，只回答「这是什么渠道、支持哪些账号类型」。
 *
 * <p>能力由子接口按账号类型补充：</p>
 * <ul>
 *   <li>{@link ChatChannelProvider}：免登录对话能力（复用 utils 的 {@code ChatClient} SPI）</li>
 *   <li>{@link ImageChannelProvider}：文生图能力（{@code ChatClient#generateImage}）</li>
 *   <li>{@link VideoChannelProvider}：文生视频能力（{@code ChatClient#generateVideo}）</li>
 *   <li>{@link Model3DChannelProvider}：图生 3D 能力（{@code ChatClient#generate3D}）</li>
 * </ul>
 *
 * <p>一个渠道实现按需实现其中一个或两个能力接口，{@link #accountTypes()} 必须与所实现的能力一致。</p>
 *
 * <p>AI 登录态渠道（OAuth 绑定 / 刷新 / 签到）属于 {@code spring-support-ai-starter}
 * 自己的 {@code AccountChannelAuthManager} SPI，不在本模块登记，本模块也不反向依赖 AI。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ChannelProvider {

    /**
     * 渠道标识（稳定，持久化 / 路由用），与 {@code @Spi} 别名一致。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    String displayName();

    /**
     * 渠道种类。
     *
     * <p>默认 {@link ChannelKind#CHAT}：本模块登记的都是免登录能力渠道，
     * 具体实现应显式声明（短信 / 邮件 / 文生图各有自己的种类）。</p>
     *
     * @return 渠道种类
     */
    default ChannelKind kind() {
        return ChannelKind.CHAT;
    }

    /**
     * 本渠道支持的账号类型。
     *
     * <p>默认仅 {@link AccountType#FIXED}；临时接码 / 收信渠道覆盖为
     * {@link AccountType#TEMPORARY}。</p>
     *
     * @return 支持的账号类型集合
     */
    default Set<AccountType> accountTypes() {
        return Set.of(AccountType.FIXED);
    }

    /**
     * 绑定 / 接入方式，供前端渲染对应的绑定 UI。
     *
     * <p>复用既有词表：{@code device-code / browser-device / browser-oauth / qr-code / sms / api-key / none}。
     * 无绑定流程的渠道（如接码 / 收信）取 {@code none}。</p>
     *
     * @return 接入方式
     */
    default String authMethod() {
        return "none";
    }

    /**
     * 渠道优先级（数值越大越优先）。
     *
     * <p>同一 {@link #kind()} 下存在多个实现时，编排层（临时账号创建、默认收信渠道选择）
     * 取优先级最高者作为默认数据源；优先级相同则按 SPI 注册顺序取第一个。
     * 接码 / 收信渠道可据此实现「进入优先」：先尝试稳定的数据源，失败再降级到备用源。</p>
     *
     * @return 优先级，默认 {@code 0}
     */
    default int priority() {
        return 0;
    }
}
