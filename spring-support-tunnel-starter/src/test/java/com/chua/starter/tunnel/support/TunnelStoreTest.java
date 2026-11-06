package com.chua.starter.tunnel.support;

import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.tunnel.properties.TunnelProperties;
import com.chua.starter.tunnel.spi.TunnelProvider;
import com.chua.starter.tunnel.spi.TunnelProviderRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 多提供方映射存储器 {@link TunnelStore} 的存储目录布局与 IO 行为。
 *
 * <p>核心约定：文件路径为 {@code <storeDir>/<provider>/<id>.json}，跨提供
 * 方按 id 检索。下面验证每个 provider 是否独立成目录。</p>
 * @author CH
 * @since 4.0.0.51
 */
class TunnelStoreTest {

    private static final Path STORE_ROOT = Path.of(System.getProperty("user.dir")).resolve("target/tunnel-store2");

    /** 存储测试用隧道本地端口（按场景编号） */
    private static final int PORT_STORE_CREATE = 9001;
    private static final int PORT_SPLIT_1 = 9002;
    private static final int PORT_SPLIT_2 = 9003;
    private static final int PORT_FIND = 9004;
    private static final int PORT_DELETE = 9005;
    private static final int PORT_NO_PROVIDER = 9006;

    private TunnelProperties properties;

    /**
     * 每次测试前清空存储目录。
     */
    @BeforeEach
    void cleanStoreDir() throws Exception {
        if (Files.isDirectory(STORE_ROOT)) {
            try (var walk = Files.walk(STORE_ROOT)) {
                walk.sorted(Comparator.comparingLong(Path::getNameCount).reversed())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
        Files.createDirectories(STORE_ROOT);
    }

    /**
     * 需要 storeDir 指向测试目录。
     */
    private TunnelProperties writeProperties() {
        TunnelProperties props = new TunnelProperties();
        props.setStoreDir(STORE_ROOT.toString());
        return props;
    }

    /**
     * 注册两个可以提供变体的提供方：一个在 storeDir 上覆盖。
     */
    private TunnelProviderRegistry registry() {
        return new TunnelProviderRegistry(List.of(
                new TestTunnelProvider("tunnel-test", true),
                new TestTunnelProvider("tunnel-other", true)));
    }

    private TunnelRecord map(int port, String provider) {
        TunnelRecord r = new TunnelRecord();
        r.setLocalPort(port);
        r.setMode("http");
        r.setProvider(provider);
        r.setName("store-map-" + port);
        return r;
    }

    @Test
    void shouldStoreInProviderSubdir() throws Exception {
        TunnelStore store = new TunnelStore(writeProperties(), registry());
        TunnelRecord saved = store.save(map(PORT_STORE_CREATE, "tunnel-test"));

        Path dir = STORE_ROOT.resolve("tunnel-test");
        assertThat(dir).isDirectory();
        assertThat(dir.resolve(saved.getId() + ".json")).exists();

        // 按 provider 取：只从这个目录读。
        List<TunnelRecord> found = store.findAll("tunnel-test");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(saved.getId());
        assertThat(store.findAll(null)).hasSize(1);
    }

    @Test
    void shouldSplitProvidersByDir() {
        TunnelStore store = new TunnelStore(writeProperties(), registry());
        store.save(map(PORT_SPLIT_1, "tunnel-test"));
        store.save(map(PORT_SPLIT_2, "tunnel-other"));

        assertThat(STORE_ROOT.resolve("tunnel-test")).isDirectory();
        assertThat(STORE_ROOT.resolve("tunnel-other")).isDirectory();
        assertThat(store.findAll(null)).hasSize(2);
        assertThat(store.findAll("tunnel-test")).hasSize(1);
        assertThat(store.findAll("tunnel-other")).hasSize(1);
    }

    @Test
    void shouldFindRecordAcrossProviders() {
        TunnelStore store = new TunnelStore(writeProperties(), registry());
        TunnelRecord saved = store.save(map(PORT_FIND, "tunnel-other"));

        assertThat(store.find(saved.getId())).isPresent().get().isEqualTo(saved);
        assertThat(store.require(saved.getId())).isEqualTo(saved);
        assertThatThrownBy(() -> store.require("not-there"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("映射不存在");
        assertThat(store.find(null)).isEmpty();
    }

    @Test
    void shouldDeleteRecordFromProviderDir() {
        TunnelStore store = new TunnelStore(writeProperties(), registry());
        TunnelRecord saved = store.save(map(PORT_DELETE, "tunnel-test"));

        assertThat(store.delete(saved.getId())).isPresent();
        assertThat(store.find(saved.getId())).isEmpty();
        assertThat(store.findAll(null)).isEmpty();
        assertThat(store.delete(saved.getId())).isEmpty();
    }

    @Test
    void shouldFailOnSaveWithoutProvider() {
        TunnelStore store = new TunnelStore(writeProperties(), registry());
        TunnelRecord noProvider = map(PORT_NO_PROVIDER, null);
        noProvider.setProvider("");

        assertThatThrownBy(() -> store.save(noProvider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("映射缺少提供方（provider）");
        assertThat(STORE_ROOT.toFile().listFiles()).isEmpty();
    }
}
