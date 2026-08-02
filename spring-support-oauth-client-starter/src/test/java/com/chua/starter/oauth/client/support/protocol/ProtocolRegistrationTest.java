package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.oauth.client.support.execute.ProtocolExecutor;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 协议 SPI 注册测试
 * <p>验证所有协议名（static/http/http-lite/armeria/oauth2/rsocket）
 * 与协议执行器（static/default）均已通过 SPI 注册且可实例化。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class ProtocolRegistrationTest {

    /**
     * 全部协议可通过 SPI 加载
     */
    @Test
    void allProtocolsLoadable() {
        for (String name : List.of("static", "http", "http-lite", "armeria", "oauth2", "rsocket")) {
            assertNotNull(ServiceProvider.of(Protocol.class).getNewExtension(name, new AuthClientProperties()),
                    "协议未注册或不可实例化: " + name);
        }
    }

    /**
     * 协议执行器可通过 SPI 加载
     */
    @Test
    void executorsLoadable() {
        assertNotNull(ServiceProvider.of(ProtocolExecutor.class).getExtension("static"), "static 执行器未注册");
        assertNotNull(ServiceProvider.of(ProtocolExecutor.class).getExtension("default"), "default 执行器未注册");
    }
}
