package com.chua.starter.oauth.client.support.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAuthServerFilter 执行顺序门禁。
 *
 * <p>回归缺陷：文件存储节点描述会通过 DelegatingServerFilter 把业务插件
 * order 覆盖为默认 0，原 order=50 在反应式链上排到文件存储网关之后，
 * 私有桶尚未鉴权就返回 200。安全鉴权必须排在所有业务插件之前。</p>
 */
@DisplayName("OAuthServerFilter: 鉴权执行顺序")
class OAuthServerFilterOrderTest {

    @Test
    @DisplayName("OAuth 必须早于文件存储节点默认的插件 order=0")
    void oauthRunsBeforeFileStorageDescriptor() {
        OAuthServerFilter filter = new OAuthServerFilter();

        assertTrue(filter.getOrder() < 0,
                () -> "OAuth order 必须小于文件存储节点默认 filter order 0，实际为 "
                        + filter.getOrder());
    }
}