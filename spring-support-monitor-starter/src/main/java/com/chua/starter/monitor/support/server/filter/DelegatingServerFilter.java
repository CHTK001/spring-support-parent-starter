package com.chua.starter.monitor.support.server.filter;

import com.chua.common.support.network.ProtocolType;
import com.chua.common.support.network.server.filter.ReactiveFilterChain;
import com.chua.common.support.network.server.filter.ReactiveServerFilter;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.filter.ServerFilterConfig;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
* 服务管理 —— 描述委托过滤器。
*
* <p>用 {@code ServerFilterDescriptor} 中声明的 name/order/enabled 归一化底层过滤器实例，
* 同时桥接同步与响应式两种过滤链接口：底层实现哪种链路就透传哪种。</p>
*
* @author CH
* @since 4.0.0.42
 */
public class DelegatingServerFilter implements ServerFilter, ReactiveServerFilter {

    /**
    * 底层过滤器实例
     */
    private final ServerFilter delegate;
    /**
    * 描述名
     */
    private final String filterName;
    /**
    * 覆盖顺序(空 则沿用底层实现)
     */
    private final Integer order;
    /**
    * 描述级启用开关(支持热切换)
     */
    private volatile boolean enabled;

    /**
    * 创建委托过滤器
    *
    * @param delegate   底层过滤器
    * @param filterName 描述名
    * @param order      覆盖顺序(空 则沿用底层实现)
    * @param enabled    描述级启用开关
     */
    public DelegatingServerFilter(ServerFilter delegate, String filterName, Integer order, boolean enabled) {
        this.delegate = delegate;
        this.filterName = filterName;
        this.order = order;
        this.enabled = enabled;
    }

    /**
    * 热切换启用状态(不重建过滤器实例)。
    *
    * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
    * 获取底层过滤器实例
    *
    * @return 底层过滤器
     */
    public ServerFilter getDelegate() {
        return delegate;
    }

    @Override
    public void doFilter(ServerRequest request, ServerResponse response, ServerFilterChain chain) throws Exception {
        delegate.doFilter(request, response, chain);
    }

    @Override
    public CompletionStage<Void> doFilter(ServerRequest request, ServerResponse response, ReactiveFilterChain chain) {
        if (delegate instanceof ReactiveServerFilter reactive) {
            return reactive.doFilter(request, response, chain);
        }
        // 底层仅实现同步链：直接放行交由后续链路处理(同步过滤器在同步引擎路径上生效)
        return chain.doFilter(request, response);
    }

    @Override
    public int getOrder() {
        return order != null ? order : delegate.getOrder();
    }

    @Override
    public boolean isEnabled() {
        return enabled && delegate.isEnabled();
    }

    @Override
    public String getFilterId() {
        return filterName != null ? filterName : delegate.getFilterId();
    }

    @Override
    public void init(ServerFilterConfig config) throws Exception {
        delegate.init(config);
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    @Override
    public void updateConfig(java.util.Map<String, Object> config) {
        delegate.updateConfig(config);
    }

    @Override
    public ProtocolType[] supportProtocols() {
        return delegate.supportProtocols();
    }

    @Override
    public String supportPath() {
        return delegate.supportPath();
    }
}
