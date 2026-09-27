package com.chua.starter.test;

import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.osgi.OsgiBundle;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.deploy.BundleDeployWatcher;
import com.chua.starter.test.osgi.GreetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实测试：插件目录热部署全链路（Spring Boot + Felix + ObjectContext）。
 *
 * <p>覆盖 {@link BundleDeployWatcher} 的完整链路，全程不重启 Spring 上下文：</p>
 * <pre>
 * 投放 v1 jar 到监听目录 → 扫描 → 自动安装 → 容器可取到 v1 实现
 * 覆盖为 v2 jar（同符号名、新版本）→ 扫描 → 原地升级 → 容器切到 v2 实现
 * 移除 jar → 扫描 → 自动卸载 → 容器下线
 * </pre>
 *
 * <p>与 {@code BundleManagerRealTest}（走 Maven 坐标）互补：本用例验证的是
 * 「文件系统投放」这条部署通道，覆盖 watcher → 框架 → 桥接 → 容器四层。</p>
 *
 * <p>为消除轮询时序带来的不确定性，直接调用 {@link BundleDeployWatcher#scan()}
 * （该方法为 public 且 synchronized），不依赖后台调度线程。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-deploy-watcher",
        "plugin.osgi.storage-clean=onInit",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.framework-properties[org.osgi.framework.system.packages.extra]=com.chua.starter.test.osgi"
})
class BundleDeployWatcherRealTest {

    /**
     * 投放 bundle 的符号名，同时也是投放目录下的 jar 文件名。
     * <p>
     * {@link BundleDeployWatcher} 的原地升级按 {@code 符号名 + ".jar"} 定位文件，
     * 故文件名必须与符号名一致。
     * </p>
     */
    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.test.deploy.watcher";

    /**
     * 生成的 bundle 实现类所在包。
     */
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.deploy";

    @Autowired
    private FelixOsgiLauncher osgiLauncher;

    @Autowired
    private ObjectContext objectContext;

    /**
     * 本次用例使用的投放目录。
     */
    private Path deployDir;

    /**
     * 本次用例使用的热部署监听器（未启动调度线程，仅手动 scan）。
     */
    private BundleDeployWatcher watcher;

    /**
     * 每个用例准备独立的空投放目录与监听器，避免用例间残留。
     *
     * @throws Exception 目录准备失败时抛出
     */
    @BeforeEach
    void setUp() throws Exception {
        deployDir = Paths.get("target", "osgi-deploy-test").toAbsolutePath();
        deleteRecursively(deployDir);
        Files.createDirectories(deployDir);
        watcher = new BundleDeployWatcher(osgiLauncher, deployDir.toString(),
                60000L, true, true);
    }

    /**
     * 清理本次用例安装的 bundle 与投放目录。
     */
    @AfterEach
    void tearDown() {
        try {
            osgiLauncher.uninstallBundle(BUNDLE_SYMBOLIC_NAME);
        } catch (Exception ignored) {
            // 已卸载或不存在时忽略
        }
        try {
            watcher.stop();
        } catch (Exception ignored) {
            // 未启动调度时停止无副作用
        }
        try {
            deleteRecursively(deployDir);
        } catch (Exception ignored) {
            // 清理失败不影响断言结论
        }
    }

    /**
     * 真实测试：投放 → 自动安装 → 热升级 → 移除自动卸载 的全链路。
     *
     * @throws Exception 构建 jar 或文件操作失败时抛出
     */
    @Test
    void deployUpgradeRemoveFullChain() throws Exception {
        // 1) 投放 v1 → 扫描 → 自动安装 → 容器可取到 v1
        writeDeployJar("DeployGreetV1", "deploy-v1", "1.0.0");
        watcher.scan();

        OsgiBundle installed = osgiLauncher.getBundle(BUNDLE_SYMBOLIC_NAME);
        assertNotNull(installed, "投放 v1 jar 后应被自动安装");
        assertEquals("1.0.0", installed.getVersion(), "首次投放应安装 v1");

        GreetService contextV1 = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(contextV1, "自动安装后 ObjectContext 应能查到插件服务");
        assertEquals("deploy-v1:world", contextV1.greet("world"), "容器应取到 v1 实现");

        // 2) 覆盖为 v2（同符号名、新版本）→ 扫描 → 原地升级 → 容器切换到 v2
        writeDeployJar("DeployGreetV2", "deploy-v2", "2.0.0");
        watcher.scan();

        OsgiBundle upgraded = osgiLauncher.getBundle(BUNDLE_SYMBOLIC_NAME);
        assertNotNull(upgraded, "覆盖 jar 后 bundle 应仍然存在");
        assertEquals("2.0.0", upgraded.getVersion(), "覆盖 jar 后应原地升级到 v2");

        GreetService contextV2 = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(contextV2, "热升级后 ObjectContext 应仍能查到插件服务");
        assertEquals("deploy-v2:world", contextV2.greet("world"),
                "热升级后容器应自动切换到 v2 实现（无需重启 Spring 上下文）");

        // 3) 移除 jar → 扫描 → 自动卸载 → 容器下线
        Files.deleteIfExists(deployDir.resolve(BUNDLE_SYMBOLIC_NAME + ".jar"));
        watcher.scan();

        assertNull(osgiLauncher.getBundle(BUNDLE_SYMBOLIC_NAME), "移除 jar 后应被自动卸载");
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class),
                "自动卸载后 ObjectContext 应查不到插件服务");
    }

    /**
     * 生成 bundle 实现 jar 并投放到监听目录。
     *
     * @param className 实现类名
     * @param prefix    greet 返回前缀，用于区分版本
     * @param version   bundle 版本
     * @throws Exception 编译或打包失败时抛出
     */
    private void writeDeployJar(String className, String prefix, String version) throws Exception {
        Path srcFile = Paths.get("target", "osgi-deploy-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, prefix), StandardCharsets.UTF_8);

        Path classOut = Paths.get("target", "osgi-deploy-classes", className);
        deleteRecursively(classOut);
        Files.createDirectories(classOut);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                "-d", classOut.toString(),
                "-classpath", System.getProperty("java.class.path"),
                srcFile.toString());
        if (result != 0) {
            throw new IllegalStateException("bundle 实现类编译失败: " + className);
        }

        Path jarFile = deployDir.resolve(BUNDLE_SYMBOLIC_NAME + ".jar");
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Bundle-ManifestVersion", "2");
        attrs.putValue("Bundle-SymbolicName", BUNDLE_SYMBOLIC_NAME);
        attrs.putValue("Bundle-Version", version);
        attrs.putValue("Bundle-Activator", BUNDLE_BASE_PACKAGE + "." + className);
        attrs.putValue("Import-Package", "org.osgi.framework, com.chua.starter.test.osgi");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()), manifest)) {
            try (var walk = Files.walk(classOut)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    if (p.toString().endsWith(".class")) {
                        String entry = classOut.relativize(p).toString().replace('\\', '/');
                        jos.putNextEntry(new JarEntry(entry));
                        jos.write(Files.readAllBytes(p));
                        jos.closeEntry();
                    }
                }
            }
        }
        // 指纹 = 最后修改时间 + 长度，覆盖后必须变化才能被识别为「已更新」
        jarFile.toFile().setLastModified(System.currentTimeMillis());
        assertTrue(Files.size(jarFile) > 0, "生成的插件 jar 不应为空");
    }

    /**
     * 生成 bundle 实现类源码：实现应用侧接口并在 activator 中注册服务。
     *
     * @param className 实现类名
     * @param prefix    greet 返回前缀
     * @return 源码文本
     */
    private static String bundleSource(String className, String prefix) {
        return "package " + BUNDLE_BASE_PACKAGE + ";\n"
                + "import com.chua.starter.test.osgi.GreetService;\n"
                + "import org.osgi.framework.BundleActivator;\n"
                + "import org.osgi.framework.BundleContext;\n"
                + "public class " + className + " implements GreetService, BundleActivator {\n"
                + "    @Override\n"
                + "    public String greet(String name) {\n"
                + "        return \"" + prefix + ":\" + name;\n"
                + "    }\n"
                + "    @Override\n"
                + "    public void start(BundleContext context) throws Exception {\n"
                + "        context.registerService(GreetService.class.getName(), this, null);\n"
                + "    }\n"
                + "    @Override\n"
                + "    public void stop(BundleContext context) throws Exception {\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 递归删除目录或文件。
     *
     * @param path 目标路径
     * @throws Exception 删除失败时抛出（目录不存在则直接返回）
     */
    private static void deleteRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (java.io.IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
