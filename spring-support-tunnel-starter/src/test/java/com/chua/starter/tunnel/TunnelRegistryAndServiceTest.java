package com.chua.starter.tunnel;

import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.common.support.tunnel.TunnelView;
import com.chua.starter.tunnel.properties.TunnelProperties;
import com.chua.starter.tunnel.spi.TunnelProvider;
import com.chua.starter.tunnel.spi.TunnelProviderRegistry;
import com.chua.starter.tunnel.support.TestTunnelProvider;
import com.chua.starter.tunnel.support.TunnelService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提供方注册表与统一编排服务（纯 JUnit，不启动 Spring 容器）。
 *
 * <p>借助 {@link TestTunnelProvider} 存根覆盖「注册表裁剪 / 增删改查 / 启停恢复
 * / 持久化目录结构」，不参与厂商 CLI 调用。</p>
 * @author CH
 * @since 4.0.0.51
 */
class TunnelRegistryAndServiceTest {

    private static final Path STORE_DIR = Path.of(System.getProperty("user.dir")).resolve("target/tunnel-store");

    /** 测试用隧道本地端口（按测试场景编号） */
    private static final int PORT_CREATE = 8080;
    private static final int PORT_START_STOP = 8081;
    private static final int PORT_OUT_OF_RANGE = 70000;
    private static final int PORT_INVALID_MODE = 8083;
    private static final int PORT_UNKNOWN_PROVIDER = 8084;
    private static final int PORT_UPDATE = 8085;
    private static final int PORT_AUTO_START = 8086;
    private static final int PORT_NOT_AUTO_START = 8087;

    /**
     * 置空存储目录，避免测试之间相互影响（先删文件、再删空目录，按深度倒序排列）。
     */
    @BeforeEach
    void cleanStoreDir() throws Exception {
        if (Files.isDirectory(STORE_DIR)) {
            try (var walk = Files.walk(STORE_DIR)) {
                walk.sorted(Comparator.comparingLong(Path::getNameCount).reversed())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
        Files.createDirectories(STORE_DIR);
    }

    /**
     * 满足公共字段的记录。
     */
    private static TunnelRecord record(int port) {
        TunnelRecord r = new TunnelRecord();
        r.setLocalPort(port);
        r.setName("test-map-" + port);
        r.setMode("http");
        r.setEnabled(true);
        return r;
    }

    private TunnelProvider testProvider(String name, boolean available) {
        return new TestTunnelProvider(name, available);
    }

    /**
     * 注入 storeDir 指向测试目录。
     */
    private TunnelProperties writeProperties() {
        TunnelProperties props = new TunnelProperties();
        props.setStoreDir(STORE_DIR.toString());
        return props;
    }

    @Test
    void shouldDescribeStubProvider() {
        TunnelProvider provider = testProvider("tunnel-test", true);
        assertThat(provider.displayName()).isEqualTo("tunnel-test Test");
        assertThat(provider.supportedModes()).containsExactly("http", "tcp");
        assertThat(provider.defaultMode()).isEqualTo("http");
        assertThat(provider.capabilities()).containsEntry("stub", "test");
    }

    @Test
    void shouldRegisterOneProvider() {
        TunnelProvider provider = testProvider("tunnel-test", true);
        TunnelProviderRegistry registry = new TunnelProviderRegistry(List.of(provider));

        assertThat(registry.names()).containsExactly("tunnel-test");
        assertThat(registry.all()).containsExactly(provider);
        assertThat(registry.find("tunnel-test")).isPresent().get().isEqualTo(provider);
        assertThat(registry.find("TUNNEL-TEST")).isPresent().get().isEqualTo(provider);
        assertThat(registry.find("nope")).isEmpty();
        assertThat(registry.isEmpty()).isFalse();
    }

    @Test
    void shouldFailOnDuplicateName() {
        assertThatThrownBy(() -> new TunnelProviderRegistry(List.of(testProvider("dup", true),
                testProvider("dup", false))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TunnelProvider 名称重复");
    }

    @Test
    void shouldFilterProvidersByWhitelist() {
        TunnelProvider one = testProvider("tunnel-test", true);
        TunnelProvider two = testProvider("tunnel-blocked", false);
        TunnelProviderRegistry registry = new TunnelProviderRegistry(whitelist(one, two));

        assertThat(registry.names()).containsExactly("tunnel-test");
        assertThat(registry.require("tunnel-test")).isSameAs(one);
        assertThatThrownBy(() -> registry.require("tunnel-blocked"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前已注册");
    }

    private List<TunnelProvider> whitelist(TunnelProvider one, TunnelProvider two) {
        List<TunnelProvider> all = new ArrayList<>();
        all.add(one);
        all.add(two);
        // 模拟 TunnelAutoConfiguration 的白名单裁剪：只留下 "tunnel-test"。
        return all.stream().filter(p -> "tunnel-test".equals(p.name())).toList();
    }

    @Test
    void shouldCreateListGetDeleteMap() throws Exception {
        TunnelProperties props = writeProperties();
        TunnelProviderRegistry registry = new TunnelProviderRegistry(
                List.of(testProvider("tunnel-test", true)));
        TunnelService service = new TunnelService(props, registry);

        // 创建（接口侧由 resolveCreateProvider 自动选唯一提供方）。
        TunnelRecord created = service.create(record(PORT_CREATE));
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getProvider()).isEqualTo("tunnel-test");
        assertThat(created.getName()).isEqualTo("test-map-8080");

        // 查询。
        List<TunnelView> all = service.list(null);
        assertThat(all).hasSize(1);
        assertThat(service.get(created.getId()).getName()).isEqualTo("test-map-8080");

        // 删除。
        assertThat(service.delete(created.getId())).isTrue();
        assertThat(service.list(null)).isEmpty();
        assertThatThrownBy(() -> service.get(created.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("映射不存在");
    }

    @Test
    void shouldStartStopMap() {
        TunnelProperties props = writeProperties();
        TunnelProviderRegistry registry = new TunnelProviderRegistry(
                List.of(testProvider("tunnel-test", true)));
        TunnelService service = new TunnelService(props, registry);

        TunnelRecord created = service.create(record(PORT_START_STOP));
        assertThat(service.get(created.getId()).isRunning()).isFalse();

        service.start(created.getId());
        assertThat(service.get(created.getId()).isRunning()).isTrue();
        assertThat(service.get(created.getId()).getPublicUrls())
                .containsExactly("http://public.tunnel-test.tunnel");
        assertThat(service.get(created.getId()).getStartedAt()).isNotNull();
        assertThat(service.get(created.getId()).getError()).isNull();

        service.stop(created.getId());
        assertThat(service.get(created.getId()).isRunning()).isFalse();
        assertThat(service.get(created.getId()).getError()).isNull();
    }

    @Test
    void shouldRejectInvalidInputs() {
        TunnelProperties props = writeProperties();
        TunnelProviderRegistry registry = new TunnelProviderRegistry(
                List.of(testProvider("tunnel-test", true)));
        TunnelService service = new TunnelService(props, registry);

        // 空配置
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("映射配置不能为空");

        // 非法端口
        TunnelRecord ported = record(PORT_OUT_OF_RANGE);
        assertThatThrownBy(() -> service.create(ported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本地端口必须在 1 - 65535 之间");

        // 非法模式
        TunnelRecord mode = record(PORT_INVALID_MODE);
        mode.setMode("udp");
        assertThatThrownBy(() -> service.create(mode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的 tunnel-test 模式");

        // 未知的提供方
        TunnelRecord unknown = record(PORT_UNKNOWN_PROVIDER);
        unknown.setProvider("nope");
        assertThatThrownBy(() -> service.create(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知的内网穿透提供方");
    }

    @Test
    void shouldUpdateMap() throws Exception {
        TunnelProperties props = writeProperties();
        TunnelProviderRegistry registry = new TunnelProviderRegistry(
                List.of(testProvider("tunnel-test", true)));
        TunnelService service = new TunnelService(props, registry);

        TunnelRecord created = service.create(record(PORT_UPDATE));
        TunnelRecord update = new TunnelRecord();
        update.setLocalPort(created.getLocalPort());
        update.setName("update-name");
        update.setRemark("comment");
        update.setEnabled(true);

        TunnelView updated = service.update(created.getId(), update);
        assertThat(updated.getName()).isEqualTo("update-name");
        assertThat(updated.getRemark()).isEqualTo("comment");
        assertThat(updated.getProvider()).isEqualTo("tunnel-test");
    }

    @Test
    void shouldRestoreAutoStartOfMaps() {
        TunnelProperties props = writeProperties();
        props.setRestoreOnStartup(true);

        TunnelProviderRegistry registry = new TunnelProviderRegistry(
                List.of(testProvider("tunnel-test", true)));
        TunnelService service = new TunnelService(props, registry);

        // 模拟已持久化且 autoStart 记录的映射（不通过接口创建，避免 bind 覆盖）。
        TunnelRecord autoStart = record(PORT_AUTO_START);
        autoStart.setAutoStart(true);
        TunnelRecord persisted = service.create(autoStart);

        service.restoreAutoStart();
        assertThat(service.list(null)).isNotEmpty();
        assertThat(service.get(persisted.getId()).isRunning()).isTrue();
    }

    @Test
    void shouldKeepNotAutoStartRestored() {
        TunnelProperties props = writeProperties();
        props.setRestoreOnStartup(true);

        TunnelProviderRegistry registry = new TunnelProviderRegistry(
                List.of(testProvider("tunnel-test", true)));
        TunnelService service = new TunnelService(props, registry);

        TunnelRecord hand = record(PORT_NOT_AUTO_START);
        hand.setAutoStart(false);
        hand.setEnabled(false);
        TunnelRecord persisted = service.create(hand);

        service.restoreAutoStart();
        assertThat(service.list(null)).hasSize(1);
        assertThat(service.get(persisted.getId()).isRunning()).isFalse();
    }
}
