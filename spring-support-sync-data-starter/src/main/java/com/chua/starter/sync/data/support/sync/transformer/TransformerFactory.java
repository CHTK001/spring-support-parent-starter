package com.chua.starter.sync.data.support.sync.transformer;

import com.chua.starter.sync.data.support.sync.transformer.impl.*;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 转换器工厂
 * 管理所有转换器实例
 */
@Component
public class TransformerFactory {
    
    private final Map<String, DataTransformer> transformers = new ConcurrentHashMap<>();
    
    public TransformerFactory() {
        // 注册内置转换器
        register("MAPPING", new FieldMappingTransformer());
        register("FILTER", new DataFilterTransformer());
        register("MASKING", new DataMaskingTransformer());
        register("SCRIPT", new ScriptTransformer());
    }
    
    /**
     * 注册转换器
     */
    public void register(String type, DataTransformer transformer) {
        transformers.put(type.toUpperCase(), transformer);
    }
    
    /**
     * 获取转换器
     */
    public DataTransformer getTransformer(String type) {
        DataTransformer transformer = transformers.get(type.toUpperCase());
        if (transformer == null) {
            throw new IllegalArgumentException("不支持的转换器类型: " + type);
        }
        return transformer;
    }
    
    /**
     * 检查转换器是否存在
     */
    public boolean hasTransformer(String type) {
        return transformers.containsKey(type.toUpperCase());
    }
    
    /**
     * 获取所有转换器类型
     */
    public java.util.Set<String> getSupportedTypes() {
        return transformers.keySet();
    }
}
