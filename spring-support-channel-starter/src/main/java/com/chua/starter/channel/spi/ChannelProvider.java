package com.chua.starter.channel.spi;

import java.util.Set;

/**
 * 渠道描述层 SPI —— 最薄的一层，只回答「这是什么渠道、支持哪些账号类型」。
 *
 * <p>能力由子接口按账号类型补充：</p>
 * <ul>
 *   <li>{@link FixedAccountProvider}：固定账号能力（绑定 / 授权拿长期凭据）</li>
 *   <li>{@link TemporaryAccountProvider}：临时账号能力（领取虚拟身份 + 收消息）</li>
 * </ul>
 *
 * <p>一个渠道实现按需实现其中一个或两个能力接口，{@link #accountTypes()} 必须与所实现的能力一致。</p>
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
     * <p>默认 {@link ChannelKind#AI}：现有全部 AI 渠道实现无需显式声明即可保持原语义。</p>
     *
     * @return 渠道种类
     */
    default ChannelKind kind() {
        return ChannelKind.AI;
    }

    /**
     * 本渠道支持的账号类型。
     *
     * <p>默认仅 {@link AccountType#FIXED}：现有 AI 渠道实现无需显式声明即可保持原语义。</p>
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
