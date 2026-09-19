package com.chua.starter.ai.support.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 设置「全局默认 + 用户覆盖」读取服务。
 *
 * <p>配置值分两层：全局层存 {@code sys_ai_global_setting}（管理员维护，对所有人生效），
 * 用户层存 {@code sys_ai_user_setting}（登录用户自己维护）。本服务负责按
 * 「用户覆盖 -&gt; 全局默认」的顺序解析单个字段，并提供合并视图与覆盖项的写入/撤销。</p>
 *
 * <p>用户标识为空（未登录、后台线程、AppKey 机器流量）时，一律解析为全局默认，
 * 保证后台任务不受请求上下文缺失影响。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiUserSettingService {

    /**
     * 解析字段值：优先取该用户的覆盖，缺失时回落到全局默认。
     *
     * @param userId 用户标识，可为空（空表示只取全局默认）
     * @param group  分组
     * @param field  字段名
     * @return 配置值，两层都未配置时返回 {@link Optional#empty()}
     */
    Optional<String> resolve(String userId, String group, String field);

    /**
     * 解析布尔字段值，两层都未配置或解析失败时返回默认值。
     *
     * @param userId       用户标识，可为空
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 布尔值
     */
    boolean resolveBoolean(String userId, String group, String field, boolean defaultValue);

    /**
     * 读取该用户的覆盖值（不含全局默认）。
     *
     * @param userId 用户标识
     * @param group  分组
     * @param field  字段名
     * @return 覆盖值，未覆盖时返回 {@link Optional#empty()}
     */
    Optional<String> getUserValue(String userId, String group, String field);

    /**
     * 合并视图：全局默认逐条给出，被覆盖的字段用用户值替换并标记来源。
     *
     * @param userId 用户标识，可为空（空表示只看全局默认）
     * @param group  分组
     * @return 每项含 group/field/value/valueType/description/sort/overridden/defaultValue
     */
    List<Map<String, Object>> listMerged(String userId, String group);

    /**
     * 列出该用户在指定分组下的全部覆盖项。
     *
     * @param userId 用户标识
     * @param group  分组
     * @return 覆盖项列表
     */
    List<Map<String, Object>> listOverrides(String userId, String group);

    /**
     * 写入/更新单个覆盖项。
     *
     * @param userId      用户标识
     * @param group       分组
     * @param field       字段名
     * @param value       值
     * @param valueType   值类型，可为空（默认 STRING）
     * @param description 描述，可为空
     */
    void upsertValue(String userId, String group, String field, String value, String valueType, String description);

    /**
     * 删除单个覆盖项，使该字段回落到全局默认。
     *
     * @param userId 用户标识
     * @param group  分组
     * @param field  字段名
     * @return 被删除的行数
     */
    int deleteValue(String userId, String group, String field);

    /**
     * 清空该用户在指定分组下的全部覆盖项。
     *
     * @param userId 用户标识
     * @param group  分组
     * @return 被删除的行数
     */
    int clearGroup(String userId, String group);
}
