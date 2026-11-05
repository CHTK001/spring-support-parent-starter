// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.support;

import com.chua.datasource.support.middleware.MiddlewareClientProvider;
import com.chua.starter.datasourcehub.support.middleware.MiddlewareClientRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「单 jar 中间件客户端」上传通道的验收。
 *
 * <p><b>为什么不用桩对象</b>：这里要验的正是「一个只依赖 SPI 接口的单 jar，
 * 能不能被独立类加载器装进来、能不能被注册表认出来、删掉后能不能干净退出」。
 * 用桩就绕过了类加载器，等于什么都没验。所以现场用 {@link JavaCompiler}
 * 编一个实现类，打成 jar，走与生产完全相同的入口。</p>
 *
 * <p><b>覆盖的失败面</b>：入口类不存在、入口类不是 SPI 实现，两种都必须在
 * 上传阶段就抛出来，且不能污染已装配列表 —— 否则用户会得到「传上去了但用不了」
 * 这种最难排查的状态。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
class MiddlewareDriverSupportTest {

    /**
     * 现场编译的中间件实现源码。
     *
     * <p>刻意放在一个应用 classpath 上不存在的包里：如果加载器退化成父加载器，
     * 这里会直接 {@code ClassNotFoundException}，测试就失败 —— 正是我们想守住的边界。</p>
     */
    private static final String PROVIDER_SOURCE = """
            package demo.uploaded;

            import com.chua.datasource.support.middleware.MiddlewareCapability;
            import com.chua.datasource.support.middleware.MiddlewareClientProvider;
            import com.chua.datasource.support.middleware.MiddlewareConnectRequest;
            import com.chua.datasource.support.middleware.MiddlewareDocument;
            import com.chua.datasource.support.middleware.MiddlewareQueryResult;
            import com.chua.datasource.support.middleware.MiddlewareTestResult;
            import com.chua.datasource.support.middleware.MiddlewareTreeNode;

            import java.util.List;

            public class DemoProvider implements MiddlewareClientProvider {

                @Override
                public String name() {
                    return "demomw";
                }

                @Override
                public String displayName() {
                    return "演示中间件";
                }

                @Override
                public int defaultPort() {
                    return 12345;
                }

                @Override
                public MiddlewareCapability capability() {
                    return null;
                }

                @Override
                public MiddlewareTestResult test(MiddlewareConnectRequest request) {
                    return null;
                }

                @Override
                public List<MiddlewareTreeNode> listTopNodes(MiddlewareConnectRequest request) {
                    return List.of();
                }

                @Override
                public List<MiddlewareTreeNode> listChildren(MiddlewareConnectRequest request, String parentKey) {
                    return List.of();
                }

                @Override
                public MiddlewareQueryResult query(MiddlewareConnectRequest request, String expression, int limit) {
                    return null;
                }

                @Override
                public MiddlewareDocument document(MiddlewareConnectRequest request, String scope, String key) {
                    return null;
                }
            }
            """;

    /**
     * 每个用例结束后清缓存，避免用例之间通过静态状态互相影响。
     */
    @AfterEach
    void clear() {
        MiddlewareDriverSupport.clearCache();
    }

    /**
     * 主链路：单 jar → 独立类加载器 → 注册表可见 → 删除后消失。
     *
     * @param temp 临时目录
     * @throws Exception 编译或打包失败
     */
    @Test
    @DisplayName("上传的单 jar 能被独立加载器装进来，并入注册表，删除后回退")
    void loadsUploadedSingleJarIntoRegistry(@TempDir Path temp) throws Exception {
        Path jar = buildProviderJar(temp);

        MiddlewareClientProvider provider =
                MiddlewareDriverSupport.providerOf(jar.toFile(), "demo.uploaded.DemoProvider");

        assertEquals("demomw", provider.name());
        assertEquals("演示中间件", provider.displayName());
        assertEquals(12345, provider.defaultPort());

        // 类加载器隔离：实现不是应用类加载器装的，否则「单 jar 不进产物」这件事就不成立
        assertNotSame(getClass().getClassLoader(), provider.getClass().getClassLoader(),
                "实现类必须来自该 jar 自己的类加载器");

        // 注册表（生产里 /ms/monitor/middleware/types 就是它）必须立刻看得到
        MiddlewareClientRegistry registry = new MiddlewareClientRegistry();
        assertTrue(registry.isSupported("demomw"), "注册表应认出刚上传的类型");
        assertTrue(registry.isSupported("DemoMW"), "类型判断应大小写不敏感");
        assertEquals("演示中间件", registry.displayNames().get("demomw"));
        assertNotNull(registry.require("demomw"));
        assertTrue(MiddlewareDriverSupport.uploaded().containsKey("demomw"));

        // 删除 jar：缓存与注册表都要干净退出，不能留下「删了还能用」的幽灵
        MiddlewareDriverSupport.evict(jar.toFile());
        assertFalse(MiddlewareDriverSupport.uploaded().containsKey("demomw"));
        assertFalse(new MiddlewareClientRegistry().isSupported("demomw"), "删除后注册表不应再认这个类型");
    }

    /**
     * 入口类不是 SPI 实现时，必须在加载阶段就失败。
     *
     * @param temp 临时目录
     * @throws Exception 编译或打包失败
     */
    @Test
    @DisplayName("入口类没实现 MiddlewareClientProvider 时当场失败，且不污染注册表")
    void rejectsClassNotImplementingSpi(@TempDir Path temp) throws Exception {
        Path jar = buildProviderJar(temp);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MiddlewareDriverSupport.providerOf(jar.toFile(), "demo.uploaded.NotAProvider"));

        assertTrue(error.getMessage().contains("MiddlewareClientProvider"),
                "报错要讲清「不是这个接口的实现」，不能只抛类名：实际=" + error.getMessage());
        assertTrue(MiddlewareDriverSupport.uploaded().isEmpty(), "失败的加载不能留下装配记录");
    }

    /**
     * 入口类不在 jar 里时，报错要能指到类名与文件名，否则排障只能靠猜。
     *
     * @param temp 临时目录
     * @throws Exception 打包失败
     */
    @Test
    @DisplayName("入口类不存在时报错带类名与 jar 名")
    void reportsMissingEntryClass(@TempDir Path temp) throws Exception {
        Path jar = buildProviderJar(temp);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MiddlewareDriverSupport.providerOf(jar.toFile(), "demo.uploaded.Missing"));

        assertTrue(error.getMessage().contains("demo.uploaded.Missing"), error.getMessage());
        assertTrue(error.getMessage().contains(jar.getFileName().toString()), error.getMessage());
        assertTrue(MiddlewareDriverSupport.uploaded().isEmpty());
    }

    /**
     * 现场编译并打包一个「只依赖 SPI 接口、不带任何第三方依赖」的单 jar。
     *
     * @param temp 临时目录
     * @return jar 路径
     * @throws IOException 读写失败
     */
    private Path buildProviderJar(Path temp) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // JRE 下没有编译器；这属于环境缺失，不是功能失败
        Assumptions.assumeTrue(compiler != null, "需要 JDK 才能现场编译探针实现");

        Path srcRoot = Files.createDirectories(temp.resolve("src"));
        Path packageDir = Files.createDirectories(srcRoot.resolve("demo/uploaded"));
        Path source = Files.writeString(packageDir.resolve("DemoProvider.java"), PROVIDER_SOURCE,
                StandardCharsets.UTF_8);
        // 「不是实现类」的探针：同一个 jar 里再放一个无关类
        Path notAProvider = Files.writeString(packageDir.resolve("NotAProvider.java"),
                "package demo.uploaded;\n\npublic class NotAProvider {\n}\n", StandardCharsets.UTF_8);

        Path classes = Files.createDirectories(temp.resolve("classes"));
        ByteArrayOutputStream compilerOut = new ByteArrayOutputStream();
        int exit = compiler.run(null, null, compilerOut,
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(),
                source.toString(), notAProvider.toString());
        assertEquals(0, exit, "探针实现编译失败: " + compilerOut.toString(StandardCharsets.UTF_8));

        Path jar = temp.resolve("demo-middleware-1.0.jar");
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jarOut = new JarOutputStream(out);
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String entryName = classes.relativize(file).toString().replace('\\', '/');
                jarOut.putNextEntry(new JarEntry(entryName));
                jarOut.write(Files.readAllBytes(file));
                jarOut.closeEntry();
            }
        }
        return jar;
    }
}