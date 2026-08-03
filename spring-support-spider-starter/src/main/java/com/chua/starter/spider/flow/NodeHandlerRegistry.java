package com.chua.starter.spider.flow;

import com.chua.starter.spider.enums.SpiderNodeType;
import com.chua.starter.spider.flow.handler.DbNodeHandler;
import com.chua.starter.spider.flow.handler.ExtractNodeHandler;
import com.chua.starter.spider.flow.handler.FunctionNodeHandler;
import com.chua.starter.spider.flow.handler.LoopNodeHandler;
import com.chua.starter.spider.flow.handler.OutputNodeHandler;
import com.chua.starter.spider.flow.handler.RequestNodeHandler;
import com.chua.starter.spider.flow.handler.ScriptNodeHandler;
import com.chua.starter.spider.flow.handler.SqlNodeHandler;
import com.chua.starter.spider.flow.handler.StartNodeHandler;
import com.chua.starter.spider.flow.handler.StopNodeHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点处理器注册中心。
 *
 * <p>启动时收集所有 {@link NodeHandler} Bean，按 supportedType() 索引到 map，
 * 流程执行时按节点类型查找对应 Handler。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeHandlerRegistry {

    /**
     * 未知类型返回 null（不会被调用）
     */
    private static final NodeHandler UNKNOWN = null;

    /**
     * 所有 NodeHandler Bean
     */
    private final List<NodeHandler> handlers;

    /**
     * 按类型索引的 Handler 映射（启动后填充）
     */
    private Map<String, NodeHandler> handlerMap;

    /**
     * 初始化：按 supportedType 索引。
     */
    @PostConstruct
    public void init() {
        Map<String, NodeHandler> map = new HashMap<>();
        for (NodeHandler h : handlers) {
            String type = h.supportedType();
            if (type != null) {
                map.put(type, h);
            }
        }
        this.handlerMap = map;
        log.info("[Flow] 节点处理器注册完成: {}", map.keySet());
    }

    /**
     * 根据类型获取 Handler。
     *
     * @param type 节点类型编码
     * @return 对应 Handler，未找到返回 null
     */
    public NodeHandler get(String type) {
        if (type == null || handlerMap == null) {
            return UNKNOWN;
        }
        return handlerMap.get(type.toLowerCase());
    }

    /**
     * 根据枚举获取 Handler。
     *
     * @param type 节点类型枚举
     * @return 对应 Handler
     */
    public NodeHandler get(SpiderNodeType type) {
        return get(type == null ? null : type.getCode());
    }

    /**
     * 获取所有已注册的 Handler（用于调试面板展示节点类型清单）。
     *
     * @return Handler 映射的副本
     */
    public Map<String, NodeHandler> all() {
        return handlerMap == null ? Map.of() : new HashMap<>(handlerMap);
    }
}
