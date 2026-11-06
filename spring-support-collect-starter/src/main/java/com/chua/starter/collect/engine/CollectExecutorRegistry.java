package com.chua.starter.collect.engine;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节点执行器注册表。
 *
 * <p>同时接纳 Spring Bean 与 {@code @Spi} 扩展：Spring 侧优先，SPI 侧用于第三方 jar 零侵入补充节点。
 * 同一 shape 重复注册时后到者被忽略并告警。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class CollectExecutorRegistry {

    /**
     * shape 到执行器。
     */
    private final Map<String, CollectShapeExecutor> executors = new LinkedHashMap<>();

    /**
     * 构造注册表并收集执行器。
     *
     * @param provider Spring 容器内的执行器
     */
    public CollectExecutorRegistry(ObjectProvider<CollectShapeExecutor> provider) {
        provider.orderedStream().forEach(this::register);
        try {
            ServiceProvider.of(CollectShapeExecutor.class).collect(this::register);
        } catch (Exception ex) {
            log.debug("[collect] SPI 执行器收集跳过: {}", ex.getMessage());
        }
        log.info("[collect] 节点执行器注册完成 count={} shapes={}", executors.size(), executors.keySet());
    }

    /**
     * 注册执行器。
     *
     * @param executor 执行器
     */
    private void register(CollectShapeExecutor executor) {
        if (executor == null || executor.supportShape() == null || executor.supportShape().isBlank()) {
            return;
        }
        CollectShapeExecutor previous = executors.putIfAbsent(executor.supportShape(), executor);
        if (previous != null && previous != executor) {
            log.warn("[collect] 节点类型重复注册，已忽略 shape={} 由 {} 提供",
                    executor.supportShape(), previous.getClass().getSimpleName());
        }
    }

    /**
     * 取执行器。
     *
     * @param shape 节点类型
     * @return 执行器，未注册返回 null
     */
    public CollectShapeExecutor get(String shape) {
        return executors.get(shape);
    }

    /**
     * 是否已注册该节点类型。
     *
     * @param shape 节点类型
     * @return 已注册返回 true
     */
    public boolean contains(String shape) {
        return executors.containsKey(shape);
    }

    /**
     * 全部已注册节点类型。
     *
     * @return 类型集合
     */
    public Set<String> shapes() {
        return Set.copyOf(executors.keySet());
    }

    /**
     * 全部节点元数据，供设计器工具箱与属性面板渲染。
     *
     * @return 元数据列表
     */
    public List<CollectShapeMeta> metas() {
        List<CollectShapeMeta> metas = new ArrayList<>();
        for (CollectShapeExecutor executor : executors.values()) {
            CollectShapeMeta meta = executor.meta();
            if (meta != null) {
                metas.add(meta);
            }
        }
        return metas;
    }
}
