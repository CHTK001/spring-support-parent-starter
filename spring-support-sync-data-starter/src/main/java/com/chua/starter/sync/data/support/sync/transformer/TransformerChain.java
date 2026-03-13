package com.chua.starter.sync.data.support.sync.transformer;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 转换器链
 * 支持多个转换器串联执行
 */
@Slf4j
public class TransformerChain {
    
    private final List<TransformerNode> nodes = new ArrayList<>();
    private final TransformerFactory factory;
    
    public TransformerChain(TransformerFactory factory) {
        this.factory = factory;
    }
    
    /**
     * 添加转换器节点
     */
    public TransformerChain addTransformer(String type, TransformConfig config) {
        DataTransformer transformer = factory.getTransformer(type);
        nodes.add(new TransformerNode(type, transformer, config));
        return this;
    }
    
    /**
     * 执行转换链
     */
    public Map<String, Object> execute(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        
        Map<String, Object> current = input;
        
        for (TransformerNode node : nodes) {
            try {
                log.debug("执行转换器: {}", node.type);
                current = node.transformer.transform(current, node.config);
                
                // 如果转换结果为null（被过滤），则终止链
                if (current == null) {
                    log.debug("数据被过滤器过滤，终止转换链");
                    return null;
                }
            } catch (Exception e) {
                log.error("转换器 {} 执行失败", node.type, e);
                throw new TransformException("转换器执行失败: " + node.type, e);
            }
        }
        
        return current;
    }
    
    /**
     * 获取转换器数量
     */
    public int size() {
        return nodes.size();
    }
    
    /**
     * 清空转换器链
     */
    public void clear() {
        nodes.clear();
    }
    
    /**
     * 转换器节点
     */
    private static class TransformerNode {
        private final String type;
        private final DataTransformer transformer;
        private final TransformConfig config;
        
        public TransformerNode(String type, DataTransformer transformer, TransformConfig config) {
            this.type = type;
            this.transformer = transformer;
            this.config = config;
        }
    }
}
