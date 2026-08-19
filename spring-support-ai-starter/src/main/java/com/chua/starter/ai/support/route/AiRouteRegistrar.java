package com.chua.starter.ai.support.route;

import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.protocol.server.ServletHandlerMappingDefinition;
import com.chua.common.support.objects.ConfigureObjectContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI REST 路由注册器。
 * <p>
 * 将 AI 模块的 REST 接口注册到协议层（内嵌 ProtocolServer 对象上下文），
 * 使 Chat/OAS 协议端口可直接调用 AI 能力。
 *
 * @author CH
 */
@Slf4j
@Component
public class AiRouteRegistrar implements ApplicationContextAware {

    /** Application上下文 */
    private ApplicationContext applicationContext;

    @Override
    /** 设置ApplicationContext */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 将全部 AI REST 路由注册到指定的对象上下文。
     * <p>
     * 路由注册通过对象上下文的 MappingBeanDefinitionRegister 完成，
     * 每个路由绑定一个 handler 函数，该函数将请求委托给 Spring MVC 控制器。
     *
     * @param objectContext ProtocolServer 的对象上下文
     */
    public void registerRoutes(ConfigureObjectContext objectContext) {
        if (objectContext == null) {
            log.warn("[AiRouteRegistrar] 对象上下文为空，跳过路由注册");
            return;
        }
        if (applicationContext == null) {
            log.warn("[AiRouteRegistrar] ApplicationContext 未初始化，跳过路由注册");
            return;
        }

        int count = 0;
        var register = objectContext.getMappingBeanDefinitionRegister();

        // 获取控制器 Bean
        Object openaiController = getBean("aiOpenaiController");
        Object agentController = getBean("agentConfigController");
        Object ragController = getBean("ragController");

        // 注册 OpenAI /v1/chat/completions
        if (openaiController != null) {
            register.registerMapping(new ServletHandlerMappingDefinition(
                    "/v1/chat/completions", HttpMethod.POST,
                    req -> invokeControllerMethod(openaiController, "chatCompletions",
                            new Class<?>[]{Object.class, Object.class}, req, null)));
            count++;
        }

        // 注册 OpenAI /v1/models
        if (openaiController != null) {
            register.registerMapping(new ServletHandlerMappingDefinition(
                    "/v1/models", HttpMethod.GET,
                    req -> invokeControllerMethod(openaiController, "models",
                            new Class<?>[]{Object.class}, null)));
            count++;
        }

        // 注册 Agent 模型列表
        if (agentController != null) {
            register.registerMapping(new ServletHandlerMappingDefinition(
                    "/v2/agent-models", HttpMethod.GET,
                    req -> invokeControllerMethod(agentController, "listAgentModels",
                            new Class<?>[]{})));
            count++;
        }

        // 注册 RAG 查询
        if (ragController != null) {
            register.registerMapping(new ServletHandlerMappingDefinition(
                    "/v2/rag/query", HttpMethod.POST,
                    req -> invokeControllerMethod(ragController, "query",
                            new Class<?>[]{Map.class}, req != null && req instanceof Map
                                    ? req : Map.of())));
            count++;
        }

        log.info("[AiRouteRegistrar] AI 路由注册完成，共注册 {} 条路由", count);
    }

    /**
     * 通过反射调用控制器方法。
     */
    private Object invokeControllerMethod(Object controller, String methodName,
                                           Class<?>[] paramTypes, Object... args) {
        try {
            var method = controller.getClass().getMethod(methodName, paramTypes);
            method.setAccessible(true);
            Object[] actualArgs = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                actualArgs[i] = (args != null && i < args.length) ? args[i] : null;
            }
            return method.invoke(controller, actualArgs);
        } catch (Exception e) {
            log.warn("[AiRouteRegistrar] 调用 {}.{} 失败: {}", 
                    controller.getClass().getSimpleName(), methodName, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 获取 Spring Bean。
     */
    private Object getBean(String name) {
        try {
            return applicationContext.getBean(name);
        } catch (BeansException e) {
            log.debug("[AiRouteRegistrar] Bean 未找到，跳过: {}", name);
            return null;
        }
    }
}
