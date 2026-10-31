package com.chua.starter.monitor.support.server;

import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务管理 node OAuth 过滤器初始化门禁。
 *
 * <p>回归缺陷：{@link ServerNodeRegistry#createOAuthFilter()} 原先只反射实例化
 * OAuthServerFilter，没有调用 {@code init(null)}。而 OAuthServerFilter 只有
 * {@code init(null)} 会读取环境配置
 * {@code plugin.oauth.file-storage.private-buckets}，节点 config 未显式写
 * {@code privateBuckets} 时自动挂载的过滤器私有桶恒为空，文件服务实际按公共桶运行，
 * 无令牌请求仍能访问目录与文件。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@DisplayName("服务管理节点 OAuth 过滤器初始化")
class ServerNodeRegistryOAuthFilterInitTest {

    @Test
    @DisplayName("反射创建的 OAuth 过滤器必须执行 init(null)")
    void oauthFilterMustBeInitialized() throws Exception {
        AtomicBoolean initCalled = new AtomicBoolean(false);
        ServerFilter stub = new ServerFilter() {
            @Override
            public void doFilter(ServerRequest request, ServerResponse response,
                                 ServerFilterChain chain) {
                // 不在单测里走真实请求
            }

            @Override
            public void init(com.chua.common.support.network.server.filter.ServerFilterConfig config) {
                initCalled.set(true);
            }
        };

        ServerNodeRegistry.initOAuthFilter(stub);

        assertTrue(initCalled.get(), "节点自动挂载 OAuth 过滤器前必须 init(null)，否则环境私有桶回退失效");
    }

    @Test
    @DisplayName("过滤器 init 抛异常时向上传播，由 registry 按不可用降级处理")
    void initFailurePropagates() {
        ServerFilter broken = new ServerFilter() {
            @Override
            public void doFilter(ServerRequest request, ServerResponse response,
                                 ServerFilterChain chain) {
                // 不应走到
            }

            @Override
            public void init(com.chua.common.support.network.server.filter.ServerFilterConfig config) throws Exception {
                throw new IOException("simulated init failure");
            }
        };

        assertThrows(Exception.class, () -> ServerNodeRegistry.initOAuthFilter(broken),
                "初始化失败必须向上抛给 createOAuthFilter，由调用方决定是否降级为公共桶");
    }
}