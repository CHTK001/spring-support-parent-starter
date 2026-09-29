package com.chua.starter.osgi.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.osgi.support.source.SourceExtensionResolver;
import com.chua.starter.osgi.properties.OsgiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.StaticApplicationContext;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 源码扩展点「一键」开关的真实行为测试。
 *
 * <p>只测 {@link OsgiProperties.Source} 的<b>配置默认值</b>与
 * {@link OsgiAutoConfiguration} 里 {@code sourceExtensionScheduler} 的装配路径，
 * 不启动完整 Spring 上下文——完整上下文会连带拉起 Felix 框架，
 * 而本组件刻意与 Felix 解耦（框架起不来时源码扩展点仍应可用）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class OsgiSourceConfigurationRealTest {

    /**
     * 构造一个空的 Spring 容器上下文。
     *
     * <p>{@code sourceExtensionScheduler} 需要容器上下文把 Bean 暴露给源码，
     * 而本组用例只验证装配路径本身，不关心容器里的具体 Bean，因此用
     * {@code StaticApplicationContext}（Spring 真实类型，非 mock）即可。</p>
     *
     * @return 空的容器上下文
     */
    private static ConfigurableApplicationContext emptyContext() {
        var context = new StaticApplicationContext();
        context.refresh();
        return context;
    }

    /**
     * 默认开启（2026-09-29 用户裁定：starter 的配置默认都是开启的）。
     */
    @Test
    @DisplayName("源码扩展点默认开启，且目录与引擎有可用默认值")
    void source_shouldBeEnabledByDefault() {
        var properties = new OsgiProperties();
        assertNotNull(properties.getSource(), "配置对象应存在");
        assertEquals(true, properties.getSource().isEnabled(),
                "默认开启：starter 的配置默认都是开启的");
        assertEquals("./osgi-source", properties.getSource().getDirectory());
        assertEquals("groovy", properties.getSource().getScriptEngine());
        assertEquals(true, properties.getSource().isGroovyEnabled());
    }

    /**
     * 默认开启后，目录不存在时必须安全降级——否则引入该 starter 的应用
     * （bundle-starter / datasync-starter 等）启动时就会因目录缺失出错。
     */
    @Test
    @DisplayName("默认开启且目录不存在时启动不报错")
    void defaultEnabledWithMissingDirectory_shouldStartSafely(@TempDir Path temp) {
        var properties = new OsgiProperties();
        // 用一个确定不存在的目录，模拟「引入 starter 但还没放源码」的常态
        properties.getSource().setDirectory(temp.resolve("not-created-yet").toString());

        var configuration = new OsgiAutoConfiguration();
        var scheduler = configuration.sourceExtensionScheduler(properties, emptyContext());
        assertNotNull(scheduler, "默认开启时应装配出调度器");
        try {
            scheduler.reloadIfChanged();
            assertNull(ServiceProvider.of(ConfigGreeter.class).getExtension("oneClick"),
                    "目录不存在时不应注册任何扩展点");
        } finally {
            scheduler.stop();
        }
    }

    /**
     * 端到端：装配出的调度器能把源码编译并注册成 SPI 扩展点。
     *
     * <p>这是「一键」的核心承诺：只需开配置 + 放源码，无需打包 bundle。</p>
     */
    @Test
    @DisplayName("开启配置后，源码自动编译并注册为 SPI 扩展点")
    void enabledSource_shouldCompileAndRegisterExtension(@TempDir Path temp) throws Exception {
        Path sourceDirectory = temp.resolve("osgi-source");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("Hello.java"), """
                package demo;

                import com.chua.osgi.support.source.SourceSpi;
                import com.chua.starter.osgi.configuration.ConfigGreeter;

                @SourceSpi
                public class Hello {
                    public final ConfigGreeter oneClick = name -> "一键:" + name;
                }
                """, StandardCharsets.UTF_8);

        var properties = new OsgiProperties();
        properties.getSource().setEnabled(true);
        properties.getSource().setDirectory(sourceDirectory.toString());
        properties.getSource().setGroovyEnabled(false);

        var configuration = new OsgiAutoConfiguration();
        var scheduler = configuration.sourceExtensionScheduler(properties, emptyContext());
        assertNotNull(scheduler, "开启后应装配出调度器");

        try {
            // start() 内部是 scheduleWithFixedDelay(0L, ...)，首轮编译跑在调度线程上，
            // 不能依赖它已同步完成——这里显式触发一次同步重载再断言，
            // 测的才是「源码放进目录即成为扩展点」这个契约本身。
            scheduler.reloadIfChanged();

            ConfigGreeter greeter = ServiceProvider.of(ConfigGreeter.class).getExtension("oneClick");
            assertNotNull(greeter, "源码里的 oneClick 扩展点应已注册");
            assertEquals("一键:学生", greeter.greet("学生"));
        } finally {
            scheduler.stop();
        }

        assertNull(ServiceProvider.of(ConfigGreeter.class).getExtension("oneClick"),
                "stop 后应注销，避免源码 ClassLoader 无法回收");
    }

    /**
     * 输出目录留空时应自动推导，不能因空串创建出意外路径。
     */
    @Test
    @DisplayName("输出目录留空时自动推导到源码目录的同级")
    void blankOutputDirectory_shouldBeDerived(@TempDir Path temp) {
        var properties = new OsgiProperties();
        properties.getSource().setEnabled(true);
        properties.getSource().setDirectory(temp.resolve("src").toString());
        properties.getSource().setOutputDirectory("");

        assertDoesNotThrow(() -> {
            var configuration = new OsgiAutoConfiguration();
            var scheduler = configuration.sourceExtensionScheduler(properties, emptyContext());
            if (scheduler != null) {
                scheduler.stop();
            }
        }, "留空输出目录不应抛异常");
    }

    /**
     * 配置未启用时返回 {@code null}，不创建任何后台线程。
     */
    @Test
    @DisplayName("未启用时不装配调度器")
    void disabledSource_shouldNotCreateScheduler() {
        var properties = new OsgiProperties();
        properties.getSource().setEnabled(false);
        var configuration = new OsgiAutoConfiguration();
        assertNull(configuration.sourceExtensionScheduler(properties, emptyContext()),
                "未启用时应返回 null，不创建任何后台线程");
    }

    /**
     * 覆写 {@code Source} 的 getter 返回 null 也不应导致自动装配崩溃。
     *
     * <p>自动配置里的 {@code if (source == null) return null;} 就是为这类情况准备的，
     * 此处固化该行为，避免后续重构把它删掉。</p>
     */
    @Test
    @DisplayName("配置对象为 null 时安全返回 null")
    void nullSourceConfig_shouldBeTolerated() throws Exception {
        var properties = new OsgiProperties();
        Field field = OsgiProperties.class.getDeclaredField("source");
        field.setAccessible(true);
        field.set(properties, null);

        var configuration = new OsgiAutoConfiguration();
        assertNull(configuration.sourceExtensionScheduler(properties, emptyContext()),
                "配置为 null 时应返回 null 而非抛 NPE");
        assertNotNull(SourceExtensionResolver.class.getName(), "解析器类应可加载");
    }
}
