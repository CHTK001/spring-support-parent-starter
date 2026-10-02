package com.chua.starter.monitor.support.datasync;

import com.chua.starter.datasync.mapping.DataSyncFieldMapping;

/**
 * 简单字段映射（源字段到目标字段）。
 *
 * <p>datasync 引擎的 {@link DataSyncFieldMapping} 只有接口，仓里唯一的实现是
 * {@code YamlConfigFileParser} 内部的私有 record，控制台无法复用；而字段映射需要按用户在
 * 界面上配的内容在运行期构造，因此在这里补一个不可变实现，交给引擎的
 * {@code DefaultFieldMappingConverter#applyMappings} 使用，保证转换语义与引擎一致。</p>
 *
 * @param sourceField 源字段名
 * @param targetField 目标字段名
 * @param converter   类型转换器标识（如 {@code toInteger}），可为 空表示原样透传
 * @author CH
 * @since 4.0.0.43
 */
public record SimpleFieldMapping(String sourceField, String targetField, String converter)
        implements DataSyncFieldMapping {
}