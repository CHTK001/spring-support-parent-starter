package com.chua.starter.apm.support;

import com.chua.starter.apm.support.properties.ApmProperties;
import com.chua.starter.apm.support.server.ApmStandaloneServer;
import com.chua.starter.apm.support.web.ApmDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CountDownLatch;

/**
 * 独立模式的启动入口。
 * <p>
 * 需求 1.7 的「独立」分支：不依赖 Spring 容器，直接构造
 * {@link ApmRegistry} → {@link ApmDispatcher} → {@link ApmStandaloneServer}，
 * 用 utils 层的 {@code ServerBuilder} 单独监听一个 HTTP 端口。
 * </p>
 * <p>
 * 这样 APM 可以作为「可观测网关」独立部署在一台机器上，与业务进程完全解耦；
 * 嵌入模式（{@code plugin.apm.mode=embedded}）则仍然由 {@code ApmAutoConfiguration}
 * 向宿主 Spring 注册 Servlet。两种模式共用同一个 {@link ApmDispatcher}，接口与返回值一致。
 * </p>
 * <h3>用法</h3>
 * <pre>{@code
 * java -cp "app.jar:lib/*" com.chua.starter.apm.support.ApmServerMain \
 *      -Dplugin.apm.type=skywalking \
 *      -Dskywalking.url=http://127.0.0.1:12800 \
 *      -Dplugin.apm.host=127.0.0.1 -Dplugin.apm.port=12801
 * }</pre>
 * <p>
 * 配置全部走系统属性（与 Spring 侧的 {@code plugin.apm.*} 同名），
 * 便于用 systemd / 启动脚本直接注入，不需要额外的配置文件解析。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class ApmServerMain {

    /**
     * 默认独立服务端口
     */
    private static final int DEFAULT_PORT = 12801;

    private ApmServerMain() {
    }

    /**
     * 启动独立 APM 服务
     *
     * @param args 未使用（配置走系统属性）
     * @throws Exception 启动失败
     */
    public static void main(String[] args) throws Exception {
        ApmProperties properties = new ApmProperties();
        properties.setEnabled(booleanProperty("plugin.apm.enabled", true));
        properties.setMode("standalone");
        properties.setType(stringProperty("plugin.apm.type", ""));
        properties.setPath(stringProperty("plugin.apm.path", "/api/apm/v1"));
        properties.setHost(stringProperty("plugin.apm.host", "127.0.0.1"));
        properties.setPort(intProperty("plugin.apm.port", DEFAULT_PORT));
        properties.setServerType(stringProperty("plugin.apm.server-type", "jdk"));
        properties.setDetectInterval(longProperty("plugin.apm.detect-interval", 10_000L));

        ApmRegistry registry = new ApmRegistry(properties);
        ApmDispatcher dispatcher = new ApmDispatcher(registry, properties);
        ApmStandaloneServer server = new ApmStandaloneServer(properties, dispatcher, new ObjectMapper());
        server.start();

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            registry.close();
            latch.countDown();
        }, "apm-shutdown"));
        latch.await();
    }

    /**
     * 读字符串系统属性
     *
     * @param key          键
     * @param defaultValue 缺省值
     * @return 值
     */
    private static String stringProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /**
     * 读布尔系统属性
     *
     * @param key          键
     * @param defaultValue 缺省值
     * @return 值
     */
    private static boolean booleanProperty(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    /**
     * 读整型系统属性
     *
     * @param key          键
     * @param defaultValue 缺省值
     * @return 值
     */
    private static int intProperty(String key, int defaultValue) {
        try {
            String value = System.getProperty(key);
            return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 读长整型系统属性
     *
     * @param key          键
     * @param defaultValue 缺省值
     * @return 值
     */
    private static long longProperty(String key, long defaultValue) {
        try {
            String value = System.getProperty(key);
            return value == null || value.isBlank() ? defaultValue : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}