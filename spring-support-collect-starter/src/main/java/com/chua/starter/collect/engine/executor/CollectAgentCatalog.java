package com.chua.starter.collect.engine.executor;

import com.chua.common.support.ai.agent.Agent;
import com.chua.starter.collect.vo.CollectOptionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 采集设计器使用的 Agent 目录。
 *
 * <p>不直接编译依赖 AI starter：运行期按 Bean 名 {@code agentRegistry} 取
 * {@code AgentRegistry}，这样采集模块可以独立部署；没有 AI starter 时返回空列表。
 * 只读取注册表，不创建、不启动 Agent。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.chua.starter.ai.support.registry.AgentRegistry")
public class CollectAgentCatalog {

    /**
     * Agent 注册表 Bean 名称，由 AI starter 的 {@code AiConfiguration} 提供。
     */
    private static final String REGISTRY_BEAN = "agentRegistry";

    /**
     * Spring 上下文，用于可选地查找 AI starter 的注册表。
     */
    private final ApplicationContext applicationContext;

    /**
     * 构造 Agent 目录。
     *
     * @param applicationContext Spring 上下文
     */
    public CollectAgentCatalog(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 已注册 Agent 的下拉选项。
     *
     * @return 按名称排序的选项；没有 AI 注册表时为空
     */
    public List<CollectOptionVO> options() {
        return names().stream()
                .map(name -> new CollectOptionVO(name, name))
                .toList();
    }

    /**
     * 已注册 Agent 名称。
     *
     * @return 名称列表；注册表不可用时为空
     */
    public List<String> names() {
        Object registry = registry();
        if (registry == null) {
            return List.of();
        }
        try {
            Method method = registry.getClass().getMethod("getNames");
            Object value = method.invoke(registry);
            if (!(value instanceof Collection<?> collection)) {
                return List.of();
            }
            List<String> names = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    names.add(String.valueOf(item));
                }
            }
            names.sort(Comparator.naturalOrder());
            return names;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("[collect] 读取 AgentRegistry 失败: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 按名称取运行期 Agent。
     *
     * @param name Agent 名称
     * @return Agent；注册表或名称不存在时返回 null
     */
    public Agent get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Object registry = registry();
        if (registry == null) {
            return null;
        }
        try {
            Object value = registry.getClass().getMethod("getByName", String.class).invoke(registry, name);
            return value instanceof Agent agent ? agent : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("[collect] 查找 Agent 失败: name={}, error={}", name, ex.getMessage());
            return null;
        }
    }

    /**
     * 取注册表 Bean；AI starter 未装配时返回 null。
     *
     * @return AgentRegistry 实例
     */
    private Object registry() {
        try {
            return applicationContext.getBean(REGISTRY_BEAN);
        } catch (BeansException ex) {
            return null;
        }
    }
}
