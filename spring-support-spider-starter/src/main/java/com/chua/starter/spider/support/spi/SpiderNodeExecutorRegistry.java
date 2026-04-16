package com.chua.starter.spider.support.spi;

import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.spi.executor.*;
import com.chua.starter.spider.support.engine.HumanInputSuspendRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 节点执行器注册表。
 *
 * <p>维护 SpiderNodeType → SpiderNodeExecutor 的映射，
 * 替代 SpiderExecutionEngine 中的 switch-case 分发逻辑。</p>
 *
 * @author CH
 */
@Slf4j
@Component
public class SpiderNodeExecutorRegistry {

    private final Map<SpiderNodeType, SpiderNodeExecutor> registry = new EnumMap<>(SpiderNodeType.class);

    @Autowired
    public SpiderNodeExecutorRegistry(HumanInputSuspendRegistry humanInputSuspendRegistry) {
        register(new DownloaderNodeExecutor());
        register(new UrlExtractorNodeExecutor());
        register(new DataExtractorNodeExecutor());
        register(new DetailFetchNodeExecutor());
        register(new ProcessorNodeExecutor());
        register(new FilterNodeExecutor());
        register(new HumanInputNodeExecutor(humanInputSuspendRegistry));
        register(new PipelineNodeExecutor());
        register(new ConditionSpiNodeExecutor());
        register(new ErrorHandlerSpiNodeExecutor());
        register(new DelaySpiNodeExecutor());
        log.info("[Spider] SpiderNodeExecutorRegistry 初始化完成，已注册 {} 种节点执行器", registry.size());
    }

    /**
     * 注册节点执行器。
     */
    public void register(SpiderNodeExecutor executor) {
        registry.put(executor.supportedType(), executor);
    }

    /**
     * 按节点类型获取执行器。
     *
     * @param type 节点类型
     * @return 对应执行器；若未注册则返回 empty
     */
    public Optional<SpiderNodeExecutor> getExecutor(SpiderNodeType type) {
        return Optional.ofNullable(registry.get(type));
    }

    /**
     * 获取所有已注册的执行器（用于 /capabilities 接口）。
     */
    public Map<SpiderNodeType, SpiderNodeExecutor> getAll() {
        return Map.copyOf(registry);
    }
}
