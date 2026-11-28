package com.chua.starter.server.support.server.filter;

import com.chua.common.support.network.ProtocolType;
import com.chua.common.support.network.server.filter.ReactiveFilterChain;
import com.chua.common.support.network.server.filter.ReactiveServerFilter;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.filter.ServerFilterConfig;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
        if (delegate instanceof ReactiveServerFilter reactive) {
            // 底层逻辑全在响应式 doFilter（如 FileStorageServerFilter，其阻塞版 doFilter 为纯透传）。
            // 双接口过滤器经 addFilter 注册后，响应式链按「底层实例」去重时保留的是本类的同步包装面；
            // 若这里仍直接透传，插件会被彻底架空（所有业务请求落到默认 404 处理器）。
            // 与下方 reactive→sync 桥接互为镜像：把同步链下游包成响应式链，同步等待完成后返回。
            CompletionStage<Void> stage = reactive.doFilter(request, response, new ReactiveFilterChain() {
                @Override
                public CompletionStage<Void> doFilter(ServerRequest nextRequest, ServerResponse nextResponse) {
                    try {
                        chain.doFilter(nextRequest, nextResponse);
                        return CompletableFuture.completedFuture(null);
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                }
            });
            try {
                stage.toCompletableFuture().join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
            return;
        }
        delegate.doFilter(request, response, chain);
    }

    @Override
    public CompletionStage<Void> doFilter(ServerRequest request, ServerResponse response, ReactiveFilterChain chain) {
        if (delegate instanceof ReactiveServerFilter reactive) {
            return reactive.doFilter(request, response, chain);
        }
        // 底层仅实现同步链时也必须执行，不能直接放行；否则反应式引擎上显式声明的
        // OAuth 等同步过滤器会被静默跳过，私有桶鉴权形同虚设。
        CompletionStage<Void>[] next = new CompletionStage[]{CompletableFuture.completedFuture(null)};
        try {
            delegate.doFilter(request, response, new ServerFilterChain() {
                @Override
                public void doFilter(ServerRequest nextRequest, ServerResponse nextResponse) throws Exception {
                    next[0] = chain.doFilter(nextRequest, nextResponse);
                }
            });
            return next[0];
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
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
