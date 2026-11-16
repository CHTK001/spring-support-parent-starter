package com.chua.starter.ai.support.manage.limit;

/**
 * 提供商限制 SPI。
 *
 * <p>与余额查询同构：一种「机制」一个实现，不逐家服务商写死。内置实现按
 * 「手动配置 → 官方文档预设 → 上游响应头动态观测」顺序串起来，由
 * {@link ProviderLimits} 统一调度；新增机制只需实现本接口并登记到
 * {@link ProviderLimits#providers()}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface ProviderLimitProvider {

    /**
     * 机制标识（manual / doc / header）
     *
     * @return 机制标识
     */
    String kind();

    /**
     * 查询限制；拿不到时返回 {@code null}（不要返回「全空但存在」的对象）
     *
     * @param context 探测上下文
     * @return 限制快照或 null
     */
    ProviderLimit query(ProviderLimitContext context);
}
