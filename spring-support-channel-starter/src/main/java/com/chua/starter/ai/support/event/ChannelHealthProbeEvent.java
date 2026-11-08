package com.chua.starter.ai.support.event;

import org.springframework.context.ApplicationEvent;

import java.util.Set;

/**
 * 渠道健康探针完成事件。
 *
 * <p>由 {@link com.chua.starter.ai.support.service.impl.ChannelHealthServiceImpl} 在
 * 探针轮（列/单个）执行完毕后发布；消费方（{@code ModelClientResolver}} 收到后失效
 * RouteTarget 缓存，令下线的连接立即从路由中剔除（避免路由表持有已下线通道）。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public class ChannelHealthProbeEvent extends ApplicationEvent {

    /**
     * 探针轮识别的下线连接标识
     */
    private final Set<String> downConnections;

    /**
     * 探针覆盖的连接数
     */
    private final int probed;

    /**
     * 探针头（是否来自定时任务）
     */
    private final boolean scheduled;

    /**
     * 构造探针完成事件
     *
     * @param source 发布源
     * @param downConnections 上下线连接标识
     * @param probed 探针的连接数
     * @param scheduled 是否来自定时任务
     */
    public ChannelHealthProbeEvent(Object source, Set<String> downConnections, int probed, boolean scheduled) {
        super(source);
        this.downConnections = downConnections;
        this.probed = probed;
        this.scheduled = scheduled;
    }

    /**
     * 是否来自定时任务（与手动探针区分）
     *
     * @return true 表示来自定时任务
     */
    public boolean scheduled() {
        return this.scheduled;
    }

    /**
     * 下线的连接标识
     *
     * @return 连接标识集合
     */
    public Set<String> downConnections() {
        return this.downConnections;
    }

    /**
     * 探针完成的连接数
     *
     * @return 连接数
     */
    public int probed() {
        return this.probed;
    }
}
