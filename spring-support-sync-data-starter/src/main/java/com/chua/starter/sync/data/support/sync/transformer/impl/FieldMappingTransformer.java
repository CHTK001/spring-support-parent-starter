package com.chua.starter.sync.data.support.sync.transformer.impl;

import com.chua.starter.sync.data.support.sync.transformer.DataTransformer;
import com.chua.starter.sync.data.support.sync.transformer.TransformConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 字段映射转换器
 * 将源字段映射到目标字段
 *
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
public class FieldMappingTransformer implements DataTransformer {

    @Override
    public Map<String, Object> transform(Map<String, Object> input, TransformConfig config) {
        if (input == null || input.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, String> mappings = config.getFieldMappings();
        if (mappings == null || mappings.isEmpty()) {
            return new HashMap<>(input);
        }

        Map<String, Object> output = new HashMap<>();
        
        // 应用字段映射
        mappings.forEach((sourceField, targetField) -> {
            if (input.containsKey(sourceField)) {
                output.put(targetField, input.get(sourceField));
            }
        });

        // 保留未映射的字段（如果配置允许）
        if (Boolean.TRUE.equals(config.getKeepUnmappedFields())) {
            input.forEach((key, value) -> {
                if (!mappings.containsKey(key) && !output.containsKey(key)) {
                    output.put(key, value);
                }
            });
        }

        return output;
    }

    @Override
    public boolean validateConfig(TransformConfig config) {
        if (config == null) {
            log.error("转换配置不能为空");
            return false;
        }

        Map<String, String> mappings = config.getFieldMappings();
        if (mappings == null || mappings.isEmpty()) {
            log.error("字段映射配置不能为空");
            return false;
        }

        // 检查映射配置的有效性
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                log.error("源字段名不能为空");
                return false;
            }
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                log.error("目标字段名不能为空");
                return false;
            }
        }

        return true;
    }

    public String getType() {
        return "FIELD_MAPPING";
    }
}
