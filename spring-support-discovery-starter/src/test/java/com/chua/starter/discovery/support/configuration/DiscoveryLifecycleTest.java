// [P3C A20 豁免] A20/A30/A31: 测试夹具与断言含魔法值场景，豁免。
package com.chua.starter.discovery.support.configuration;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.starter.discovery.support.service.DiscoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 服务发现的注册生命周期与多实例解析回归。
 *
 * <p>两个护栏，都是真实故障面：</p>
 * <ol>
 *   <li><b>下线必须先反注册</b>：直接 close 会让实例在注册中心残留到 TTL，
 *       滚动重启期间流量仍会打到已停进程。</li>
 *   <li><b>同协议第二个实例必须可解析</b>：重名时 Bean 名会变成
 *       {@code serviceDiscovery#<protocol>#0}，而解析逻辑只认以 {@code #<protocol>} 结尾的名字，
 *       导致第二个实现永远落到默认实现上。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.52
 */
class DiscoveryLifecycleTest {

    /**
     * 记录调用顺序的桩实现。
     */
    private static final class StubDiscovery implements ServiceDiscovery {

        /**
         * 事件序列。
         */
        private final List<String> events = new ArrayList<>();

        /**
         * 该实现返回的主机名，用于区分不同实例。
         */
        private String identity = "stub";

        @Override
        public void start() {
            events.add("start");
        }

        @Override
        public ServiceDiscovery registerService(String path, Discovery discovery) {
            events.add("register");
            return this;
        }

        @Override
        public ServiceDiscovery unregisterService(String path, Discovery discovery) {
            events.add("unregister");
            return this;
        }

        @Override
        public ServiceDiscovery unregisterService(String path, String serverId) {
            events.add("unregisterById");
            return this;
        }

        @Override
        public ServiceDiscovery updateService(String path, Discovery discovery) {
            events.add("update");
            return this;
        }

        @Override
        public Discovery getService(String path, String scatterId, String balance, String protocol) {
            return Discovery.builder().host(identity).port(1).build();
        }

        @Override
        public Set<Discovery> getServiceAll(String path) {
            return Collections.emptySet();
        }

        @Override
        public void close() {
            events.add("close");
        }
    }

    /**
     * 关闭时必须先反注册再断连。
     */
    @Test
    @DisplayName("下线：先反注册再 close")
    void unregistersBeforeClose() throws Exception {
        StubDiscovery stub = new StubDiscovery();
        Discovery discovery = Discovery.builder()
                .uriSpec("demo-namespace")
                .host("127.0.0.1")
                .port(8080)
                .build();
        DiscoveryConfiguration.ServiceDiscovertyFactoryBean factory =
                new DiscoveryConfiguration.ServiceDiscovertyFactoryBean(stub, List.of(discovery));

        factory.start();
        assertEquals(List.of("start", "register"), stub.events,
                "启动应只做 start + register");

        factory.close();
        assertEquals(List.of("start", "register", "unregister", "close"), stub.events,
                "关闭必须先 unregister 再 close，否则实例会在注册中心残留到 TTL");
    }

    /**
     * 同协议的重名实例（Bean 名带 #0 后缀）必须能被解析到。
     */
    @Test
    @DisplayName("解析：同协议多实例的 #0 后缀名可命中")
    void resolvesDeduplicatedBeanName() {
        StubDiscovery dedup = new StubDiscovery();
        dedup.identity = "nacos-0";
        StubDiscovery fallback = new StubDiscovery();
        fallback.identity = "default";

        Map<String, ServiceDiscovery> impls = new LinkedHashMap<>();
        impls.put("serviceDiscovery#nacos#0", dedup);
        impls.put("serviceDiscovery#default", fallback);

        DiscoveryService service = new DiscoveryService(fallback, impls);
        Discovery resolved = service.getDiscovery("nacos", "demo-service");

        assertEquals("nacos-0", resolved.getHost(),
                "serviceDiscovery#nacos#0 未被命中，说明重名实例回退到了默认实现");
    }
}
