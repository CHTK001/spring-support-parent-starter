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
 * <p><b>关于重复定义。</b>同一接口在 {@code spring-support-channel-starter} 里也有一份
 * （该模块的 {@code ChannelCheckinTask} 要用它，而 channel-starter 不依赖 ai-starter）。
 * ai-starter 侧曾被误删该文件，导致 **clean build 直接失败**（增量编译靠旧 class 掩盖），
 * 现恢复。两处内容必须保持一致；正确的收敛方向是把它下沉到 common-starter、
 * 由两边共同依赖，而不是继续各存一份。</p>
 *
 * @since 4.0.0.42
 * @author CH
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
     * 不经缓存、直接查询数据库（Redis 不可达时的回落路径）。
     *
     * @param group 分组
     * @param field 字段名
     * @return 配置值，未配置时返回 {@link Optional#empty()}
     */
    Optional<String> getValueDirect(String group, String field);

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
     * @param request 替换请求（含 群体 与 字段）
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
     * @param valueType   值类型：字符串 / 数字 / 布尔值
     * @param description 字段描述（可选）
     * @param sort        排序序号
     */
    void upsertField(String group, String field, String value, String valueType, String description, int sort);

    /**
     * 同步分组内的配置项描述（只改描述，不动用户已保存的值）。
     *
     * <p>用于 provider 迭代时修正文案：{@code GlobalSettingInitializer} 仅在分组为空时写入默认值，
     * 已存在的分组不会刷新，导致代码里的新描述无法生效。本方法只更新「描述与代码不一致」的行，
     * 避免覆盖用户改过的取值。</p>
     *
     * @param group                 配置分组
     * @param fieldToDescription    字段 → 代码中的最新描述
     * @return 实际更新的行数
     */
    int syncDescriptions(String group, java.util.Map<String, String> fieldToDescription);
}
