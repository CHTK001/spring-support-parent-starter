package com.chua.starter.monitor.support.config;

import com.chua.starter.filesystem.support.controller.FileDirController;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「选择文件 / 选择文件夹」404 的回归测试。
 *
 * <p>缺陷成因：{@link FileDirController} 只被 {@code FileSystemAutoConfiguration#fileDirController}
 * 装配，而 monitor 的 {@code application.yml} 把该配置类整体 exclude 掉了，
 * 于是 {@code /v2/file/dir/list} 没有任何映射——前端拿到的是 404，
 * 而 {@code plugin.filesystem.dir-list=true} 的开关看起来又像是「已经开了」。</p>
 *
 * <p>现在唯一注册点是 ai-starter 的 {@code AiFileDirConfiguration}
 * （条件 {@code plugin.filesystem.dir-list=true}）。这条链路在真机部署时暴露过
 * 一个更严重的缺陷：monitor 的 {@code MonitorFileStorageGatewayConfig} 也注册了同名
 * {@code @Bean} 方法 {@code fileDirController}，两个 {@code @Configuration} 都无条件
 * 注册，直接抛 {@code BeanDefinitionOverrideException} 导致应用起不来。
 * 本地单模块测试没加载 ai-starter 上下文，因此漏掉了该冲突。</p>
 *
 * <p>本测试守四件事：控制器到前端调用路径的映射仍然是
 * {@code GET /v2/file/dir/list}、该映射绑在 {@code list} 方法上、类上前缀与前端契约一致、
 * 以及 monitor 自身<b>不再</b>重复声明该 Bean（反向断言，防止冲突复发）。
 * 不启动整个应用（需要库与 Redis），只把真实控制器交给
 * {@link RequestMappingHandlerMapping} 解析注解。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@DisplayName("目录浏览控制器注册")
class MonitorFileStorageGatewayConfigTest {

    /**
     * 控制器必须映射到前端调用的 {@code GET /v2/file/dir/list}。
     *
     * @throws Exception 映射解析失败
     */
    @Test
    @DisplayName("控制器映射 GET /v2/file/dir/list")
    void mapsListEndpoint() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            registerController(context);
            RequestMappingHandlerMapping mapping = handlerMapping(context);
            List<String> patterns = new ArrayList<>();
            for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
                patterns.addAll(info.getPatternValues());
            }
            assertTrue(patterns.contains("/v2/file/dir/list"),
                    "缺少 /v2/file/dir/list 映射（前端会拿到 404），实际: " + patterns);
        }
    }

    /**
     * 该映射必须绑在控制器的 {@code list} 方法上，而不是别的同路径方法。
     *
     * @throws Exception 映射解析失败
     */
    @Test
    @DisplayName("映射绑定到控制器 list 方法")
    void handlerIsBoundToList() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            registerController(context);
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping(context).getHandlerMethods().entrySet()) {
                if (entry.getKey().getPatternValues().contains("/v2/file/dir/list")) {
                    assertEquals(FileDirController.class, entry.getValue().getBeanType());
                    assertEquals("list", entry.getValue().getMethod().getName());
                    return;
                }
            }
            throw new AssertionError("未找到 /v2/file/dir/list 的处理器");
        }
    }

    /**
     * 控制器类上的前缀必须与前端契约一致。
     */
    @Test
    @DisplayName("控制器前缀为 /v2/file/dir")
    void controllerPrefix() {
        RequestMapping annotation = FileDirController.class.getAnnotation(RequestMapping.class);
        assertNotNull(annotation, "FileDirController 缺少 @RequestMapping");
        assertEquals("/v2/file/dir", annotation.value()[0],
                "前端 fetchLocalFiles 调用的就是 /v2/file/dir/list");
    }

    /**
     * 必须存在 {@code GET /list} 处理器（前端真正打到的方法）。
     */
    @Test
    @DisplayName("存在 GET /list 处理器")
    void hasListHandler() {
        List<String> getPaths = new ArrayList<>();
        for (Method method : FileDirController.class.getDeclaredMethods()) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            if (mapping != null) {
                for (String path : mapping.value()) {
                    getPaths.add(path);
                }
            }
        }
        assertTrue(getPaths.contains("/list"), "缺少 GET /list，实际: " + getPaths);
    }

    /**
     * monitor 自身配置<b>不得</b>再声明 {@link FileDirController} Bean。
     *
     * <p>唯一注册点是 ai-starter 的 {@code AiFileDirConfiguration}。两处都注册会
     * 出现两个同名 {@code @Bean} 方法 {@code fileDirController}，抛
     * {@code BeanDefinitionOverrideException} 让应用直接启动失败（真机部署实测）。
     * 这条断言是反向的：它防的是「有人以为本地要兜底，又把注册加回来」。</p>
     */
    @Test
    @DisplayName("monitor 配置不再重复注册控制器")
    void monitorConfigDoesNotDeclareController() {
        List<Class<?>> beanTypes = new ArrayList<>();
        for (Method method : MonitorFileStorageGatewayConfig.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)) {
                beanTypes.add(method.getReturnType());
            }
        }
        assertFalse(beanTypes.contains(FileDirController.class),
                "MonitorFileStorageGatewayConfig 不应声明 FileDirController Bean（会与 ai-starter 冲突），实际: " + beanTypes);
    }

    /**
     * 把真实的控制器实例注册进测试上下文。
     *
     * @param context 测试上下文
     */
    private static void registerController(GenericApplicationContext context) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setDirList(true);
        context.registerBean(FileDirController.class, () -> new FileDirController(properties));
        context.refresh();
    }

    /**
     * 构建一个已扫描完 Bean 的请求映射。
     *
     * @param context 已刷新的上下文
     * @return 映射实例
     */
    private static RequestMappingHandlerMapping handlerMapping(GenericApplicationContext context) {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.setApplicationContext(context);
        mapping.afterPropertiesSet();
        return mapping;
    }
}
