package com.chua.starter.monitor.support.server.filter;

import com.chua.common.support.network.server.filter.ReactiveFilterChain;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.impl.HttpServerRequest;
import com.chua.common.support.network.server.impl.HttpServerResponse;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务管理描述委托过滤器反应式桥接门禁。
 *
 * <p>回归缺陷：底层过滤器只实现同步 {@link ServerFilter} 时，{@link
 * DelegatingServerFilter} 曾在反应式链中直接放行，导致显式声明的 OAuth
 * 等同步过滤器被静默跳过。此处锁住“同步过滤器必须执行”，无论其是否调用后续链。</p>
 */
@DisplayName("DelegatingServerFilter: 同步过滤器反应式桥接")
class DelegatingServerFilterReactiveBridgeTest {

    @Test
    @DisplayName("同步过滤器拒绝请求时不得继续后续链")
    void syncRejectStopsReactiveChain() {
        AtomicBoolean delegateCalled = new AtomicBoolean(false);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        ServerFilter rejecting = new ServerFilter() {
            @Override
            public void doFilter(ServerRequest request, ServerResponse response,
                                 ServerFilterChain chain) {
                delegateCalled.set(true);
            }
        };
        DelegatingServerFilter delegating =
                new DelegatingServerFilter(rejecting, "sync-reject", 0, true);

        CompletionStage<Void> stage = delegating.doFilter(
                request(), response(), new ReactiveFilterChain() {
                    @Override
                    public CompletionStage<Void> doFilter(ServerRequest ignored,
                                                          ServerResponse ignoredResponse) {
                        chainCalled.set(true);
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                });

        stage.toCompletableFuture().join();
        assertTrue(delegateCalled.get(), "同步过滤器必须在反应式链上执行");
        assertFalse(chainCalled.get(), "同步过滤器拒绝后不得继续后续链");
    }

    @Test
    @DisplayName("同步过滤器放行时继续后续反应式链")
    void syncPassThroughContinuesReactiveChain() {
        AtomicBoolean delegateCalled = new AtomicBoolean(false);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        ServerFilter passing = new ServerFilter() {
            @Override
            public void doFilter(ServerRequest request, ServerResponse response,
                                 ServerFilterChain chain) throws Exception {
                delegateCalled.set(true);
                chain.doFilter(request, response);
            }
        };
        DelegatingServerFilter delegating =
                new DelegatingServerFilter(passing, "sync-pass", 0, true);

        CompletionStage<Void> stage = delegating.doFilter(
                request(), response(), new ReactiveFilterChain() {
                    @Override
                    public CompletionStage<Void> doFilter(ServerRequest ignored,
                                                          ServerResponse ignoredResponse) {
                        chainCalled.set(true);
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                });

        stage.toCompletableFuture().join();
        assertTrue(delegateCalled.get(), "同步过滤器必须在反应式链上执行");
        assertTrue(chainCalled.get(), "同步过滤器放行后必须继续后续反应式链");
    }

    private static ServerRequest request() {
        // 测试只验证链桥接，不读取协议内容，空 exchange 足够
        return new HttpServerRequest(null, 1024, "UTF-8");
    }

    private static ServerResponse response() {
        // 测试只验证链桥接，不写响应，空 exchange 足够
        return new HttpServerResponse(null);
    }
}