package com.chua.starter.account.channel.event;

/**
 * 渠道目录变更事件（账号模块发布，AI 模块消费）。
 *
 * <p>渠道、渠道模型、渠道连接三项数据的任何写操作都会改变「可路由的模型集合」，
 * 但账号模块不持有路由缓存、也不做探活，因此不直接调用 AI 侧的缓存失效方法，
 * 而是发布本事件：</p>
 * <ul>
 *   <li>{@code connectionId} 非空表示某条连接的凭据/归属发生变化，消费方除了清
 *       路由缓存，还应复位该连接的健康状态（续期成功不该继续被当作 DOWN）；</li>
 *   <li>{@code connectionId} 为空表示只有目录结构变化（新增/删除渠道、开关模型），
 *       消费方清缓存即可。</li>
 * </ul>
 *
 * <p><b>为什么用事件而不是 SPI</b>：账号模块是最底层模块，不能反向依赖 AI；
 * 而事件发布只依赖 Spring 核心，宿主没装 AI 模块时事件自然无人监听，不影响启动。</p>
 *
 * @param connectionId 发生凭据/归属变化的连接标识，仅目录变化时为 {@code null}
 * @param reason       变更原因，便于日志追溯
 * @author CH
 * @since 4.0.0.58
 */
public record ChannelCatalogChangedEvent(String connectionId, String reason) {

    /**
     * 只有目录结构变化（清路由缓存即可）。
     *
     * @param reason 变更原因
     * @return 事件
     */
    public static ChannelCatalogChangedEvent catalog(String reason) {
        return new ChannelCatalogChangedEvent(null, reason);
    }

    /**
     * 某条连接的凭据发生变化（清路由缓存 + 复位健康状态）。
     *
     * @param connectionId 连接标识
     * @param reason       变更原因
     * @return 事件
     */
    public static ChannelCatalogChangedEvent connection(String connectionId, String reason) {
        return new ChannelCatalogChangedEvent(connectionId, reason);
    }
}
