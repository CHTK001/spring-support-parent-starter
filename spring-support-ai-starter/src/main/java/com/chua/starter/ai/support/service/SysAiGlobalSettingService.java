package com.chua.starter.ai.support.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 全局设置服务接口。
 *
 * <p>以「分组 + 字段」键值行方式读写 {@code sys_ai_global_setting} 表，
 * 提供类型安全的值读取与整组替换能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface SysAiGlobalSettingService {

    /**
     * 根据分组和字段名查询配置值。
     *
     * @param group 分组
     * @param field 字段名
     * @return 配置值，未配置时返回 {@link Optional#empty()}
     */
    Optional<String> getValue(String group, String field);

    /**
     * 读取布尔类型配置。
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 未配置时默认值
     * @return 布尔值
     */
    boolean getBoolean(String group, String field, boolean defaultValue);

    /**
     * 列出指定分组下所有启用的配置项。
     *
     * @param group 分组
     * @return 配置项列表
     */
    List<Map<String, Object>> list(String group);

    /**
     * 批量替换指定分组的配置（先删后插，事务内）。
     *
     * @param request 替换请求（含 group 与 fields）
     */
    void replace(Object request);

    /**
     * 增量写入/更新单字段配置，不影响分组内其它已有字段。
     *
     * @param group 分组
     * @param field 字段名
     * @param value 字段值
     * @param description 字段描述（可选）
     */
    void upsertValue(String group, String field, String value, String description);

    /**
     * 完整写入/更新单字段配置（含类型与排序），不影响分组内其它已有字段。
     *
     * @param group       分组
     * @param field       字段名
     * @param value       字段值
     * @param valueType   值类型：STRING / NUMBER / BOOLEAN
     * @param description 字段描述（可选）
     * @param sort        排序序号
     */
    void upsertField(String group, String field, String value, String valueType, String description, int sort);
}
