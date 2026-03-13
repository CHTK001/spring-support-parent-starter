package com.chua.starter.sync.data.support.sync.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 转换配置解析器
 * 解析JSON配置为TransformConfig对象
 */
@Slf4j
@Component
public class TransformConfigParser {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 解析单个转换配置
     */
    public TransformConfig parse(String json) {
        try {
            return objectMapper.readValue(json, TransformConfig.class);
        } catch (Exception e) {
            log.error("解析转换配置失败: {}", json, e);
            throw new TransformException("解析转换配置失败", e);
        }
    }
    
    /**
     * 解析转换配置列表
     */
    public List<TransformConfig> parseList(String json) {
        try {
            return objectMapper.readValue(json, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransformConfig.class));
        } catch (Exception e) {
            log.error("解析转换配置列表失败: {}", json, e);
            throw new TransformException("解析转换配置列表失败", e);
        }
    }
    
    /**
     * 从Map解析转换配置
     */
    public TransformConfig parseFromMap(Map<String, Object> map) {
        try {
            String json = objectMapper.writeValueAsString(map);
            return parse(json);
        } catch (Exception e) {
            log.error("从Map解析转换配置失败", e);
            throw new TransformException("从Map解析转换配置失败", e);
        }
    }
    
    /**
     * 构建转换器链
     */
    public TransformerChain buildChain(String json, TransformerFactory factory) {
        List<TransformConfig> configs = parseList(json);
        TransformerChain chain = new TransformerChain(factory);
        
        for (TransformConfig config : configs) {
            String type = config.getType();
            if (type == null || type.isEmpty()) {
                log.warn("转换配置缺少type字段，跳过");
                continue;
            }
            chain.addTransformer(type, config);
        }
        
        return chain;
    }
    
    /**
     * 验证配置格式
     */
    public boolean validate(String json) {
        try {
            parse(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 转换配置为JSON
     */
    public String toJson(TransformConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("转换配置为JSON失败", e);
            throw new TransformException("转换配置为JSON失败", e);
        }
    }
    
    /**
     * 转换配置列表为JSON
     */
    public String toJson(List<TransformConfig> configs) {
        try {
            return objectMapper.writeValueAsString(configs);
        } catch (Exception e) {
            log.error("转换配置列表为JSON失败", e);
            throw new TransformException("转换配置列表为JSON失败", e);
        }
    }
}
