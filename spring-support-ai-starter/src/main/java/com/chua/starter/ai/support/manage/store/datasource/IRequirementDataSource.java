package com.chua.starter.ai.support.manage.store.datasource;

import java.util.List;
import java.util.Map;

/**
 * 需求数据源提供方 SPI 接口。
 *
 * <p>每个实现类代表一种外部需求来源（飞书多维表格、Jira、Notion、通用 REST API 等）。
 * 通过 {@link java.util.ServiceLoader} 自动发现并注册到 {@link RequirementDataSourceRegistry}。</p>
 *
 * <p>典型使用场景：
 * <ul>
 *   <li>定时同步：按 cron 表达式从外部系统拉取需求条目，写入本地 {@code sys_ai_requirement} 表</li>
 *   <li>实时推送：通过 Webhook 接收外部变更事件，增量更新本地需求</li>
 *   <li>双向同步：本地修改可回写到外部系统（需 provider 实现 write 接口）</li>
 * </ul>
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IRequirementDataSource {

    /**
      * 数据源类型标识（对应 sys_AI_数据_源.sys_AI_数据_源_类型）。
     *
     * @return 类型字符串，如 {@code "lark"} / {@code "jira"} / {@code "notion"} / {@code "generic"}
     */
    String type();

    /**
     * 从外部系统拉取需求列表，写入本地库。
     *
     * @param config     数据源配置（从 sys_AI_数据_源.sys_AI_数据_源_配置 JSON 反序列化）
     * @param lastSyncAt 上次同步时间戳（轮次 ms），提供者 可用于增量同步
     * @return 本次同步新增/更新的需求数量
     */
    int sync(Map<String, Object> config, long lastSyncAt);

    /**
     * 处理 Webhook 推送事件（可选实现，未实现则返回 false）。
     *
     * @param config   数据源配置
     * @param event    外部系统推送的原始事件 JSON
     * @return true = 已消费并处理，false = 未处理（忽略）
     */
    default boolean handleWebhook(Map<String, Object> config, Map<String, Object> event) {
        return false;
    }

    /**
     * 验证数据源配置是否可用（连接测试）。
     *
     * @param config 数据源配置
     * @return true = 配置有效且可连通，false = 配置无效或连接失败
     */
    boolean testConnection(Map<String, Object> config);

    /**
     * 将本地需求的变更回写到外部系统（可选，双向同步）。
     *
     * @param config        数据源配置
     * @param requirementId 本地需求 标识
     * @param remoteId      外部系统中的对应 标识（首次同步时由 同步() 返回并缓存）
     * @param changes       变更字段
     */
    default void writeBack(Map<String, Object> config, String requirementId, String remoteId, Map<String, Object> changes) {
        // 默认不实现：单向同步
    }
}
