package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源分组注册表
 * <p>
 * 管理所有数据源分组，自动根据数据源名称前缀进行分组。
   * 命名规则：群体_member → 组名为 群体，成员名为 群体_member
 * 例如：slave_1、slave_2 → 组名为 slave，包含两个成员
 * </p>
 *
 * @since 2026/07/14
 * @author CH
 */
@Slf4j
public class DataSourceGroupRegistry {

    /** 分组映射：群体名称 → 数据源群体 */
    private static final Map<String, DataSourceGroup> GROUP_MAP = new ConcurrentHashMap<>();

    /** 数据源到组的反向映射：ds名称 → 群体名称 */
    private static final Map<String, String> DS_TO_GROUP = new ConcurrentHashMap<>();

    /** 分隔符 */
    /** Separator */
    private static final String SEPARATOR = "_";

    /** 全局默认负载均衡策略（可选，为空时使用各分组自身默认值） */
    /** 默认策略 */
    private static DataSourceGroup.LoadBalanceStrategy defaultStrategy;

    /**
     * 数据源群体registry。
     */
    private DataSourceGroupRegistry() {}

    /**
     * 注册数据源到分组（如果适用）
     * <p>
     * 自动检测数据源名称是否包含分组前缀。
     * 例如 "slave_1" 会归入 "slave" 组，"master" 不会归入任何组。
     * </p>
     *
     * @param dsName     数据源名称
     * @param dataSource 数据源实例
     */
    public static void register(String dsName, DataSource dataSource) {
        String groupName = extractGroupName(dsName);
        if (groupName == null) {
            return;
        }

        DS_TO_GROUP.put(dsName, groupName);
        DataSourceGroup group = GROUP_MAP.get(groupName);
        if (group == null) {
            group = new DataSourceGroup(groupName);
            // 如果设置了全局默认策略，应用到新分组
            if (defaultStrategy != null) {
                group.setStrategy(defaultStrategy);
            }
            GROUP_MAP.put(groupName, group);
        }
        group.addMember(dataSource, dsName);
        log.debug("[数据源分组] 注册 '{}' 到分组 '{}'", dsName, groupName);
    }

    /**
     * 根据名称获取数据源（优先从分组获取，支持负载均衡）
     *
     * @param name 数据源名称或组名
     * @return 数据源实例，不存在时返回 空
     */
    public static DataSource getDataSource(String name) {
        if (name == null) {
            return null;
        }
        DataSourceGroup group = GROUP_MAP.get(name);
        if (group != null) {
            return group.select();
        }
        return null;
    }

    /**
     * 判断是否为分组名
     *
     * @param name 名称
     * @return 是否为分组名
     */
    public static boolean isGroup(String name) {
        return name != null && GROUP_MAP.containsKey(name);
    }

    /**
     * 获取分组
     *
     * @param groupName 组名
     * @return 分组，不存在时返回 空
     */
    public static DataSourceGroup getGroup(String groupName) {
        return GROUP_MAP.get(groupName);
    }

    /**
     * 获取所有分组名称
     *
     * @return 分组名称集合
     */
    public static Set<String> getGroupNames() {
        return GROUP_MAP.keySet();
    }

    /**
     * 清除所有分组
     */
    public static synchronized void clear() {
        GROUP_MAP.clear();
        DS_TO_GROUP.clear();
        log.info("[数据源分组] 已清除所有分组");
    }

    /**
     * 获取数据源所属的组名
     *
     * @param dsName 数据源名称
     * @return 组名，不在任何组时返回 空
     */
    public static String getGroupNameByDs(String dsName) {
        return DS_TO_GROUP.get(dsName);
    }

    /**
     * 设置分组负载均衡策略
     *
     * @param groupName 组名
     * @param strategy  策略名称：round_robin 或 随机
     */
    public static void setLoadBalanceStrategy(String groupName, String strategy) {
        DataSourceGroup group = GROUP_MAP.get(groupName);
        if (group != null) {
            group.setStrategy(parseStrategy(strategy));
        }
    }

    /**
     * 设置全局默认负载均衡策略（对所有已存在的分组生效，新分组也会继承）
     *
     * @param strategy 策略名称：round_robin 或 随机
     */
    public static void setGlobalLoadBalanceStrategy(String strategy) {
        defaultStrategy = parseStrategy(strategy);
        log.info("[数据源分组] 设置全局负载均衡策略: {}", defaultStrategy);
        for (DataSourceGroup group : GROUP_MAP.values()) {
            group.setStrategy(defaultStrategy);
        }
    }

    /**
     * 解析策略名称
     * @param strategy strategy
     * @return 解析strategy的结果
     */
    private static DataSourceGroup.LoadBalanceStrategy parseStrategy(String strategy) {
        return "random".equalsIgnoreCase(strategy)
                ? DataSourceGroup.LoadBalanceStrategy.RANDOM
                : DataSourceGroup.LoadBalanceStrategy.ROUND_ROBIN;
    }

    /**
     * 从数据源名称中提取组名
     * <p>
     * 规则：如果名称包含 "_" 且最后一段是数字，则前缀为组名。
     * 例如 "slave_1" → "slave"，"slave_2" → "slave"
     * 不包含 "_" 或最后一段不是数字的不属于分组。
     * </p>
     *
     * @param dsName 数据源名称
     * @return 组名，不适用时返回 空
     */
    static String extractGroupName(String dsName) {
        if (dsName == null || dsName.isEmpty()) {
            return null;
        }
        int lastUnderscore = dsName.lastIndexOf(SEPARATOR);
        if (lastUnderscore <= 0 || lastUnderscore >= dsName.length() - 1) {
            return null;
        }
        String suffix = dsName.substring(lastUnderscore + 1);
        if (!suffix.matches("\\d+")) {
            return null;
        }
        return dsName.substring(0, lastUnderscore);
    }
}
