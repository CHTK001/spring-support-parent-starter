package com.chua.starter.plugin.store;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 更新数据
 * 
 * @author CH
 * @since 2025/1/16
 */
@Data
public class UpdateData {

    /**
     * 更新字段映射
     */
    private Map<String, Object> fields = new HashMap<>();

    /**
     * 设置字段值
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 当前对象
     */
    public UpdateData set(String field, Object value) {
        fields.put(field, value);
        return this;
    }

    /**
     * 批量设置字段值
     * 
     * @param fieldMap 字段映射
     * @return 当前对象
     */
    public UpdateData setAll(Map<String, Object> fieldMap) {
        fields.putAll(fieldMap);
        return this;
    }

    /**
     * 创建更新数据
     * 
     * @return 更新数据对象
     */
    public static UpdateData create() {
        return new UpdateData();
    }

    /**
     * 创建更新数据并设置字段
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 更新数据对象
     */
    public static UpdateData create(String field, Object value) {
        return new UpdateData().set(field, value);
    }

    /**
     * 检查是否为空
     * 
     * @return 是否为空
     */
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * 获取字段数量
     * 
     * @return 字段数量
     */
    public int size() {
        return fields.size();
    }
}
