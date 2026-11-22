package com.chua.starter.osgi.controller;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.osgi.OsgiServiceDescriptor;
import com.chua.starter.logger.event.OsgiLogEvent;
import com.chua.starter.osgi.properties.OsgiProperties;
import com.chua.starter.osgi.support.realtime.OsgiLogPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OsgiMonitorController} 生命周期与批量操作单元测试。
 *
 * <p>不依赖 Mockito / spring-test：启动器与 bundle 用手写 Fake 实现，
 * 上传文件用手写 Stub 实现；私有助手（doBatch / runLifecycleWithTimeout /
 * requireUploadSizeOk / indexByLocation / doUninstall）经反射调用。</p>
 *
 * <p>覆盖点（对应本轮加固）：
 * <ul>
 *   <li>批量端点：空列表 / 超 100 条 / 混合成败 / 不存在 bundle / 根因解包</li>
 *   <li>生命周期限时：成功 / 异常重抛 / Error 重抛 / 超时报错 / &le;0 直通</li>
 *   <li>上传限额：null / 未超 / 超限（错误信息含 max-upload-bytes）/ &le;0 不限</li>
 *   <li>jars 索引：按 location 建索引、重复取首个、null location 跳过</li>
 *   <li>卸载：成功 / bundle 不存在 / 启动器返回 false</li>
 *   <li>manage-enabled 开关关闭时拒绝变更</li>
 * </ul></p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class OsgiMonitorControllerTest {

    private FakeLauncher launcher;
    private OsgiProperties properties;
    private RecordingPublisher publisher;
    private OsgiMonitorController controller;

    @BeforeEach
    void setUp() {
        launcher = new FakeLauncher();
        properties = new OsgiProperties();
        publisher = new RecordingPublisher();
        controller = new OsgiMonitorController(launcher, null, properties, publisher);
    }

    // ------------------------------------------------------------------
    // 批量启动 / 批量卸载（公共端点 → requireManageEnabled + 锁 + doBatch）
    // ------------------------------------------------------------------

    @Test
    void batchStartMixedResults() {
        FakeBundle started = launcher.addBundle("plugin.a", "RESOLVED");
        FakeBundle active = launcher.addBundle("plugin.b", "ACTIVE");
        FakeBundle broken = launcher.addBundle("plugin.bad", "RESOLVED");
        broken.startError = new IllegalStateException("启动炸了",
                new IllegalArgumentException("底层根因"));

        Map<String, Object> result = controller.batchStart(List.of("plugin.a", "missing", "plugin.b", "plugin.bad"));

        assertEquals(4, result.get("total"));
        assertEquals(2, result.get("successCount"));
        assertEquals(2, result.get("failureCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals("plugin.a", items.get(0).get("symbolicName"));
        assertEquals(Boolean.TRUE, items.get(0).get("success"));
        assertEquals("missing", items.get(1).get("symbolicName"));
        assertEquals(Boolean.FALSE, items.get(1).get("success"));
        assertEquals("bundle 不存在: missing", items.get(1).get("error"));
        assertEquals(Boolean.TRUE, items.get(2).get("success"));
        assertEquals(Boolean.FALSE, items.get(3).get("success"));
        // 根因解包：报错给出最底层原因，而不是包装层的「启动炸了」
        assertEquals("底层根因", items.get(3).get("error"));

        // RESOLVED 的被启动；已 ACTIVE 的跳过启动
        assertEquals(1, started.startCount);
        assertEquals(0, active.startCount);
        // 批量日志：一条 start + 一条 success 汇总
        assertTrue(publisher.hasStatus("batch-start", "start"));
        assertTrue(publisher.hasStatus("batch-start", "success"));
    }

    @Test
    void batchStartRejectsEmptyList() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.batchStart(List.of()));
        assertEquals("批量操作目标不能为空", ex.getMessage());
    }

    @Test
    void batchStartRejectsNullList() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.batchStart(null));
        assertEquals("批量操作目标不能为空", ex.getMessage());
    }

    @Test
    void batchStartRejectsOverLimit() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            names.add("plugin." + i);
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.batchStart(names));
        assertEquals("批量操作一次最多 100 个 bundle: 101", ex.getMessage());
    }

    @Test
    void batchOperationsRejectedWhenManageDisabled() {
        properties.getEndpoint().setManageEnabled(false);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> controller.batchStart(List.of("plugin.a")));
        assertTrue(ex.getMessage().contains("manage-enabled=true"));
        assertThrows(IllegalStateException.class, () -> controller.batchStop(List.of("plugin.a")));
        assertThrows(IllegalStateException.class, () -> controller.batchUninstall(List.of("plugin.a")));
    }

    @Test
    void batchUninstallReportsPerItemFailure() {
        launcher.addBundle("plugin.a", "ACTIVE");
        launcher.addBundle("plugin.b", "ACTIVE");
        launcher.uninstallFailures.add("plugin.b");

        Map<String, Object> result = controller.batchUninstall(List.of("plugin.a", "plugin.b"));

        assertEquals(2, result.get("total"));
        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("failureCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(Boolean.TRUE, items.get(0).get("success"));
        assertEquals(Boolean.FALSE, items.get(1).get("success"));
        assertEquals("bundle 卸载失败: plugin.b", items.get(1).get("error"));
        assertTrue(launcher.uninstalled.contains("plugin.a"));
        assertFalse(launcher.uninstalled.contains("plugin.b"));
    }

    // ------------------------------------------------------------------
    // doUninstall（私有，反射调用）
    // ------------------------------------------------------------------

    @Test
    void doUninstallSuccess() {
        launcher.addBundle("plugin.a", "ACTIVE");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) invokePrivate("doUninstall",
                new Class<?>[]{String.class}, "plugin.a");
        assertEquals("plugin.a", result.get("symbolicName"));
        assertEquals(Boolean.TRUE, result.get("success"));
        assertTrue(launcher.uninstalled.contains("plugin.a"));
        assertTrue(publisher.hasStatus("uninstall", "success"));
    }

    @Test
    void doUninstallMissingBundleRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> invokePrivate("doUninstall", new Class<?>[]{String.class}, "nope"));
        assertEquals("bundle 不存在: nope", ex.getMessage());
    }

    @Test
    void doUninstallLauncherFailurePropagates() {
        launcher.addBundle("plugin.a", "ACTIVE");
        launcher.uninstallResult = false;
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokePrivate("doUninstall", new Class<?>[]{String.class}, "plugin.a"));
        assertEquals("bundle 卸载失败: plugin.a", ex.getMessage());
        assertTrue(publisher.hasStatus("uninstall", "failure"));
        // 卸载失败不允许动框架状态
        assertFalse(launcher.uninstalled.contains("plugin.a"));
    }

    // ------------------------------------------------------------------
    // runLifecycleWithTimeout（私有，反射调用）
    // ------------------------------------------------------------------

    @Test
    void lifecycleTimeoutSuccess() {
        FakeBundle bundle = launcher.addBundle("plugin.a", "RESOLVED");
        invokePrivate("runLifecycleWithTimeout",
                new Class<?>[]{String.class, String.class, Runnable.class},
                "start", "plugin.a", (Runnable) bundle::start);
        assertEquals(1, bundle.startCount);
        assertEquals("ACTIVE", bundle.state);
    }

    @Test
    void lifecycleTimeoutRethrowsRuntimeException() {
        RuntimeException boom = new IllegalStateException("boom");
        RuntimeException ex = assertThrows(IllegalStateException.class,
                () -> invokePrivate("runLifecycleWithTimeout",
                        new Class<?>[]{String.class, String.class, Runnable.class},
                        "start", "plugin.a", (Runnable) () -> {
                            throw boom;
                        }));
        assertSame(boom, ex);
    }

    @Test
    void lifecycleTimeoutRethrowsError() {
        assertThrows(AssertionError.class,
                () -> invokePrivate("runLifecycleWithTimeout",
                        new Class<?>[]{String.class, String.class, Runnable.class},
                        "start", "plugin.a", (Runnable) () -> {
                            throw new AssertionError("fatal");
                        }));
    }

    @Test
    void lifecycleTimeoutGivesUpWaiting() {
        properties.getEndpoint().setLifecycleTimeoutSeconds(1);
        long begin = System.currentTimeMillis();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokePrivate("runLifecycleWithTimeout",
                        new Class<?>[]{String.class, String.class, Runnable.class},
                        "start", "plugin.slow", (Runnable) () -> {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }));
        long cost = System.currentTimeMillis() - begin;
        assertTrue(ex.getMessage().contains("超过 1s 未完成"));
        assertTrue(ex.getMessage().contains("plugin.slow"));
        // 限时生效：约 1 秒放弃等待，而不是等满 3 秒
        assertTrue(cost < 2800, "应在超时后尽快返回，实际 " + cost + "ms");
    }

    @Test
    void lifecycleTimeoutNonPositiveRunsInline() {
        properties.getEndpoint().setLifecycleTimeoutSeconds(0);
        AtomicReference<Thread> runner = new AtomicReference<>();
        invokePrivate("runLifecycleWithTimeout",
                new Class<?>[]{String.class, String.class, Runnable.class},
                "start", "plugin.a", (Runnable) () -> runner.set(Thread.currentThread()));
        // ≤0 不起工作线程，动作在调用线程上直接执行
        assertEquals(Thread.currentThread(), runner.get());
    }

    // ------------------------------------------------------------------
    // requireUploadSizeOk（私有，反射调用）
    // ------------------------------------------------------------------

    @Test
    void uploadSizeNullFileOk() {
        invokePrivate("requireUploadSizeOk", new Class<?>[]{MultipartFile.class}, (Object) null);
    }

    @Test
    void uploadSizeUnderLimitOk() {
        properties.getEndpoint().setMaxUploadBytes(1000);
        invokePrivate("requireUploadSizeOk", new Class<?>[]{MultipartFile.class},
                new StubMultipartFile("a.jar", new byte[500]));
    }

    @Test
    void uploadSizeOverLimitRejected() {
        properties.getEndpoint().setMaxUploadBytes(1000);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> invokePrivate("requireUploadSizeOk", new Class<?>[]{MultipartFile.class},
                        new StubMultipartFile("a.jar", new byte[2000])));
        assertTrue(ex.getMessage().contains("2000 > 1000"));
        // 错误信息必须带出配置项，便于使用方自行调整
        assertTrue(ex.getMessage().contains("max-upload-bytes"));
    }

    @Test
    void uploadSizeNonPositiveLimitMeansUnlimited() {
        properties.getEndpoint().setMaxUploadBytes(0);
        invokePrivate("requireUploadSizeOk", new Class<?>[]{MultipartFile.class},
                new StubMultipartFile("a.jar", new byte[10_000_000]));
        properties.getEndpoint().setMaxUploadBytes(-1);
        invokePrivate("requireUploadSizeOk", new Class<?>[]{MultipartFile.class},
                new StubMultipartFile("a.jar", new byte[10_000_000]));
    }

    // ------------------------------------------------------------------
    // indexByLocation（私有，反射调用）
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void indexByLocationFirstWinsAndSkipsNull() {
        FakeBundle first = launcher.addBundle("plugin.a", "ACTIVE");
        first.location = "file:/opt/plugins/a.jar";
        FakeBundle second = launcher.addBundle("plugin.b", "RESOLVED");
        second.location = "file:/opt/plugins/b.jar";
        FakeBundle duplicate = launcher.addBundle("plugin.c", "RESOLVED");
        duplicate.location = "file:/opt/plugins/a.jar";
        FakeBundle anonymous = launcher.addBundle("plugin.d", "RESOLVED");
        anonymous.location = null;

        Map<String, OsgiBundle> index = (Map<String, OsgiBundle>) invokePrivate("indexByLocation", new Class<?>[]{});

        assertEquals(2, index.size());
        assertSame(first, index.get("file:/opt/plugins/a.jar"));
        assertSame(second, index.get("file:/opt/plugins/b.jar"));
        assertNull(index.get("file:/opt/plugins/c.jar"));
    }

    // ------------------------------------------------------------------
    // install：start=false 只落框架不启动（回归：launcher 自动启动架空参数语义）
    // ------------------------------------------------------------------

    @Test
    void installStartFalseLeavesBundleInactive(@TempDir Path tempDir) throws IOException {
        properties.getDeploy().setDirectory(tempDir.toString());

        Map<String, Object> result = controller.install(
                new StubMultipartFile("probe-fake.jar", osgiJar("probe.fake")), false);

        assertFalse(launcher.lastAutoStart);
        assertEquals("probe.fake", launcher.lastInstalled.getSymbolicName());
        assertTrue(launcher.lastInstalledUrl.endsWith("probe-fake.jar"));
        assertEquals("INSTALLED", result.get("state"));
        assertFalse(launcher.bundles.get("probe.fake").isActive());
        // jar 已落盘到部署目录
        assertTrue(Files.exists(tempDir.resolve("probe-fake.jar")));
        assertTrue(publisher.hasStatus("install", "success"));
    }

    @Test
    void installStartTrueActivatesBundleWithoutDoubleStart(@TempDir Path tempDir) throws IOException {
        properties.getDeploy().setDirectory(tempDir.toString());

        Map<String, Object> result = controller.install(
                new StubMultipartFile("probe-fake.jar", osgiJar("probe.fake")), true);

        assertTrue(launcher.lastAutoStart);
        assertEquals("ACTIVE", result.get("state"));
        // launcher 安装时已启动（state=ACTIVE），controller 不得再重复 start
        FakeBundle installed = (FakeBundle) launcher.bundles.get("probe.fake");
        assertEquals(0, installed.startCount);
    }

    /**
     * 构造最小可用 OSGi bundle jar：仅含带 Bundle-SymbolicName 的 MANIFEST。
     */
    private byte[] osgiJar(String symbolicName) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Bundle-SymbolicName", symbolicName);
        manifest.getMainAttributes().putValue("Bundle-Version", "1.0.0");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out, manifest)) {
            jar.putNextEntry(new JarEntry("marker.txt"));
            jar.write("ok".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // 测试替身
    // ------------------------------------------------------------------

    /**
     * 手写启动器替身：维护符号名 → bundle 的内存表，支持注入卸载结果。
     */
    static class FakeLauncher implements OsgiLauncher {

        final Map<String, OsgiBundle> bundles = new LinkedHashMap<>();
        final List<String> uninstalled = new ArrayList<>();
        final Set<String> uninstallFailures = new HashSet<>();
        boolean uninstallResult = true;
        boolean active = true;

        FakeBundle addBundle(String symbolicName, String state) {
            FakeBundle bundle = new FakeBundle(bundles.size() + 1, symbolicName,
                    "file:/opt/plugins/" + symbolicName + ".jar", state);
            bundles.put(symbolicName, bundle);
            return bundle;
        }

        @Override
        public OsgiBundle getBundle(String symbolicName) {
            return bundles.get(symbolicName);
        }

        @Override
        public OsgiBundle getBundle(long bundleId) {
            for (OsgiBundle bundle : bundles.values()) {
                if (bundle.getBundleId() == bundleId) {
                    return bundle;
                }
            }
            return null;
        }

        @Override
        public List<OsgiBundle> getBundlesByLocation(String location) {
            List<OsgiBundle> result = new ArrayList<>();
            for (OsgiBundle bundle : bundles.values()) {
                if (bundle.getLocation() != null && bundle.getLocation().equals(location)) {
                    result.add(bundle);
                }
            }
            return result;
        }

        @Override
        public List<OsgiBundle> getBundlesByState(String state) {
            List<OsgiBundle> result = new ArrayList<>();
            for (OsgiBundle bundle : bundles.values()) {
                if (bundle.getState().equals(state)) {
                    result.add(bundle);
                }
            }
            return result;
        }

        @Override
        public List<OsgiBundle> getActiveBundles() {
            return getBundlesByState("ACTIVE");
        }

        @Override
        public long getBundleCount() {
            return bundles.size();
        }

        @Override
        public Map<String, Object> getFrameworkStats() {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("bundleCount", bundles.size());
            stats.put("active", active);
            return stats;
        }

        @Override
        public boolean uninstallBundle(String bundleSymbolicName) {
            if (!bundles.containsKey(bundleSymbolicName) || !uninstallResult
                    || uninstallFailures.contains(bundleSymbolicName)) {
                return false;
            }
            bundles.remove(bundleSymbolicName);
            uninstalled.add(bundleSymbolicName);
            return true;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void start(Map<String, String> config) {
            active = true;
        }

        @Override
        public void stop() {
            active = false;
        }

        @Override
        public <T> List<T> getServices(Class<T> type) {
            return new ArrayList<>();
        }

        @Override
        public <T> T getService(Class<T> type) {
            return null;
        }

        @Override
        public void releaseServices(Class<?> type) {
        }

        @Override
        public void releaseAllServices() {
        }

        @Override
        public List<OsgiBundle> getBundles() {
            return new ArrayList<>(bundles.values());
        }

        @Override
        public List<String> getRegisteredServiceTypes() {
            return List.of();
        }

        @Override
        public List<OsgiServiceDescriptor> getServiceDescriptors() {
            return List.of();
        }

        OsgiBundle lastInstalled;
        String lastInstalledUrl;
        boolean lastAutoStart = true;

        @Override
        public OsgiBundle installBundle(String url) {
            throw new UnsupportedOperationException("单测未覆盖");
        }

        /**
         * 双参安装：尊重 autoStart 语义——false 时 bundle 停在 INSTALLED，
         * 用于回归「install(start=false) 被 launcher 自动启动架空」的缺陷。
         */
        @Override
        public OsgiBundle installBundle(String url, boolean autoStart) {
            this.lastInstalledUrl = url;
            this.lastAutoStart = autoStart;
            FakeBundle bundle = new FakeBundle(bundles.size() + 1, "probe.fake", url,
                    autoStart ? "ACTIVE" : "INSTALLED");
            bundles.put("probe.fake", bundle);
            this.lastInstalled = bundle;
            return bundle;
        }

        @Override
        public OsgiBundle updateBundle(String bundleSymbolicName, String url) {
            throw new UnsupportedOperationException("单测未覆盖");
        }

        @Override
        public boolean isAutoStartInstalledBundles() {
            return false;
        }

        @Override
        public void setAutoStartInstalledBundles(boolean autoStart) {
        }
    }

    /**
     * 手写 bundle 替身：状态迁移可注入延迟与异常，用于超时与失败路径。
     */
    static class FakeBundle implements OsgiBundle {

        private final long bundleId;
        private final String symbolicName;
        private final String version = "1.0.0";
        String location;
        String state;
        long startDelayMillis;
        RuntimeException startError;
        RuntimeException stopError;
        int startCount;
        int stopCount;

        FakeBundle(long bundleId, String symbolicName, String location, String state) {
            this.bundleId = bundleId;
            this.symbolicName = symbolicName;
            this.location = location;
            this.state = state;
        }

        @Override
        public long getBundleId() {
            return bundleId;
        }

        @Override
        public String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getLocation() {
            return location;
        }

        @Override
        public Map<String, String> getHeaders() {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Bundle-SymbolicName", symbolicName);
            headers.put("Bundle-Version", version);
            return headers;
        }

        @Override
        public String getState() {
            return state;
        }

        @Override
        public boolean isActive() {
            return "ACTIVE".equals(state);
        }

        @Override
        public void start() {
            startCount++;
            if (startDelayMillis > 0) {
                try {
                    Thread.sleep(startDelayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("start interrupted", ex);
                }
            }
            if (startError != null) {
                throw startError;
            }
            state = "ACTIVE";
        }

        @Override
        public void stop() {
            stopCount++;
            if (stopError != null) {
                throw stopError;
            }
            state = "RESOLVED";
        }

        @Override
        public void uninstall() {
            state = "UNINSTALLED";
        }

        @Override
        public void update(String url) {
            throw new UnsupportedOperationException("单测未覆盖");
        }

        @Override
        public <T> void registerService(Class<T> type, T service) {
            throw new UnsupportedOperationException("单测未覆盖");
        }

        @Override
        public <T> void registerService(Class<T> type, T service, Map<String, Object> properties) {
            throw new UnsupportedOperationException("单测未覆盖");
        }

        @Override
        public <T> boolean unregisterService(Class<T> type, T service) {
            return false;
        }

        @Override
        public <T> List<T> getServices(Class<T> type) {
            return List.of();
        }

        @Override
        public List<String> getRegisteredServiceTypes() {
            return List.of();
        }
    }

    /**
     * 记录型日志发布器：捕获全部事件供断言，不影响业务结果。
     */
    static class RecordingPublisher implements OsgiLogPublisher {

        final List<OsgiLogEvent> events = new ArrayList<>();

        @Override
        public void publish(OsgiLogEvent event) {
            if (event != null) {
                events.add(event);
            }
        }

        boolean hasStatus(String action, String status) {
            for (OsgiLogEvent event : events) {
                if (action.equals(event.action()) && status.equals(event.status())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 手写上传文件替身：仅提供控制器读取大小与流所需的字节数据。
     */
    static class StubMultipartFile implements MultipartFile {

        private final String filename;
        private final byte[] content;

        StubMultipartFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }

        public String getName() {
            return "file";
        }

        public String getOriginalFilename() {
            return filename;
        }

        public String getContentType() {
            return "application/java-archive";
        }

        public boolean isEmpty() {
            return content.length == 0;
        }

        public long getSize() {
            return content.length;
        }

        public byte[] getBytes() {
            return content;
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }

        public void transferTo(Path dest) throws IOException {
            Files.write(dest, content);
        }
    }

    // ------------------------------------------------------------------
    // 反射工具
    // ------------------------------------------------------------------

    /**
     * 反射调用私有助手；目标方法抛出的异常解包后原样重抛，
     * 保证 assertThrows 断言的是业务异常而不是 InvocationTargetException。
     */
    private Object invokePrivate(String name, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = OsgiMonitorController.class.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method.invoke(controller, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("反射调用失败: " + name, ex);
        }
    }
}
