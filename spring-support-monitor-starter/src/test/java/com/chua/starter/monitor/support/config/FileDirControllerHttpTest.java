package com.chua.starter.monitor.support.config;

import com.chua.starter.ai.support.configuration.core.AiFileDirConfiguration;
import com.chua.starter.common.support.api.permission.PermissionControlInterceptor;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.filesystem.support.controller.FileDirController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「选择文件 / 选择文件夹」返回 404 的端到端回归。
 *
 * <p>这条测试<b>真的起一个 Tomcat 并真的发 HTTP 请求</b>，因为要证明的正是
 * 「这个路径有没有映射」——只用反射断言注解，说服不了「请求进来会 404 还是 200」。</p>
 *
 * <p>上下文里只放四样东西：本应用的
 * {@link MonitorFileStorageGatewayConfig}（装配文件存储网关本体）、
 * {@link AiFileDirConfiguration}（目录浏览控制器的唯一注册点）、
 * 生产同款的 {@link PermissionControlInterceptor}，以及最小的 MVC 配置。
 * 不连数据库、不连 Redis。</p>
 *
 * <p>把两个配置类一起放进来是刻意的：真机部署时它们都注册了同名
 * {@code @Bean} 方法 {@code fileDirController}，抛
 * {@code BeanDefinitionOverrideException} 让应用起不来。本测试同时守
 * 「路径能通」和「两个配置类可以共存」。</p>
 *
 * <p>断言两条路径的差异：{@code /v2/file/dir/list} 必须是 200（映射存在且权限拦截放行），
 * 而一个不存在的路径必须不是 200（证明 200 不是「什么都放行」）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@DisplayName("目录浏览接口真请求不再 404")
class FileDirControllerHttpTest {

    /**
     * 真发一次请求：目录浏览接口必须是 200，且响应体是业务 JSON（不是 404 页面）。
     *
     * @throws Exception 起服务或请求失败
     */
    @Test
    @DisplayName("GET /v2/file/dir/list 返回 200")
    void listEndpointReturnsOk() throws Exception {
        try (WebServerHolder holder = start()) {
            HttpResponse<String> response = holder.get("/v2/file/dir/list");
            assertEquals(200, response.statusCode(),
                    "/v2/file/dir/list 应映射到 FileDirController（404 即为本缺陷），实际状态: "
                            + response.statusCode() + "，响应: " + response.body());
            assertTrue(response.body().contains("\"code\""), "响应应是统一返回体，实际: " + response.body());
        }
    }

    /**
     * 对照组：同一服务上不存在的路径必须不是 200，否则上一条断言就没有意义。
     *
     * @throws Exception 起服务或请求失败
     */
    @Test
    @DisplayName("对照组：未映射路径不是 200")
    void unknownPathIsNotOk() throws Exception {
        try (WebServerHolder holder = start()) {
            HttpResponse<String> response = holder.get("/v2/file/dir/definitely-not-exist");
            assertNotEquals(200, response.statusCode(),
                    "未映射路径也返回 200，说明服务把什么都放行了，测试无效");
        }
    }

    /**
     * 起一个只装配目录浏览链路的服务。
     *
     * @return 可关闭的服务句柄
     */
    private static WebServerHolder start() {
        AnnotationConfigServletWebServerApplicationContext context =
                new AnnotationConfigServletWebServerApplicationContext();
        // 关掉文件服务器网关本体（本测试只关心 HTTP 映射），并给出配置类需要的本地存储后端
        Map<String, Object> properties = new HashMap<>();
        properties.put("plugin.filesystem.storage-server.enable", "false");
        // 打开目录浏览开关：本测试要验证的是「接口能通」，而不是「开关关掉时返回业务错误」
        properties.put("plugin.filesystem.dir-list", "true");
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test-props", properties));
        context.registerBean(MonitorFileStorageConfig.LOCAL_STORAGE_BEAN_NAME,
                com.chua.common.support.storage.FileStorage.class,
                FileDirControllerHttpTest::dummyStorage);
        // 随机端口，避免与本机既有服务抢端口
        context.registerBean("tomcatServletWebServerFactory", TomcatServletWebServerFactory.class,
                () -> new TomcatServletWebServerFactory(0));
        // DispatcherServlet 走标准的 ServletRegistrationBean，交由上下文在 refresh 时挂载并启动容器
        context.registerBean("dispatcherServletRegistration", ServletRegistrationBean.class,
                () -> new ServletRegistrationBean<DispatcherServlet>(
                        new DispatcherServlet(context), "/"));
        context.register(TestWebConfig.class, MonitorFileStorageGatewayConfig.class, AiFileDirConfiguration.class);
        context.refresh();
        WebServer server = context.getWebServer();
        return new WebServerHolder(context, server, server.getPort());
    }

    /**
     * 本地存储后端的替身：本测试不读写文件，只需要满足配置类的装配依赖。
     *
     * @return 存储后端代理
     */
    private static com.chua.common.support.storage.FileStorage dummyStorage() {
        return (com.chua.common.support.storage.FileStorage) java.lang.reflect.Proxy.newProxyInstance(
                FileDirControllerHttpTest.class.getClassLoader(),
                new Class<?>[]{com.chua.common.support.storage.FileStorage.class},
                (proxy, method, args) -> {
                    // Object 方法交给代理自身语义，避免落进 Map 键或打日志时炸掉
                    switch (method.getName()) {
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "dummyFileStorage";
                        default:
                            break;
                    }
                    return switch (method.getReturnType().getName()) {
                        case "boolean" -> false;
                        case "int" -> 0;
                        case "long" -> 0L;
                        default -> null;
                    };
                });
    }

    /**
     * 最小 MVC 配置：挂上生产同款权限拦截器。
     */
    @EnableWebMvc
    static class TestWebConfig implements WebMvcConfigurer, ApplicationContextAware {

        /**
         * 上下文（用于取 {@link AuthService} 的 ObjectProvider）
         */
        private ApplicationContext applicationContext;

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            // 生产同款拦截器。上下文里没有 AuthService（等同于 plugin.oauth.enable=false），
            // 拦截器对 @Permission 直接放行——这正是要把文件选择接口放开的那条路径。
            registry.addInterceptor(new PermissionControlInterceptor(
                            applicationContext.getBeanProvider(AuthService.class)))
                    .addPathPatterns("/**");
        }
    }

    /**
     * 服务句柄：负责收尾，避免测试结束后端口泄漏。
     *
     * @param context 上下文
     * @param server  服务
     * @param port    实际端口
     */
    private record WebServerHolder(AnnotationConfigServletWebServerApplicationContext context,
                                   WebServer server, int port) implements AutoCloseable {
        /**
         * 发一次 GET。
         *
         * @param path 路径
         * @return 响应
         * @throws IOException          网络异常
         * @throws InterruptedException 被中断
         */
        HttpResponse<String> get(String path) throws IOException, InterruptedException {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        @Override
        public void close() {
            server.stop();
            context.close();
        }
    }
}
