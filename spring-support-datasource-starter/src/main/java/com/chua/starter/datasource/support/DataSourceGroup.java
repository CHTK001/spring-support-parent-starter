package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据源分组
 * <p>
 * 将多个同组数据源聚合为一个逻辑组，支持负载均衡策略。
 * 例如 slave_1、slave_2 自动归入 slave 组，{@code @DS("slave")} 时自动负载均衡。
 * </p>
 *
 * @since 2026/07/14
 * @author CH
 */
@Slf4j
public class DataSourceGroup {

    /** 组名（如 slave） */
    /** 分组名称 */
    private final String groupName;

    /** 组内数据源列表 */
    /** 数据sources */
    private final List<DataSource> dataSources = new ArrayList<>();

    /** 数据源名称列表（用于日志） */
    /** 数据来源names */
    private final List<String> dataSourceNames = new ArrayList<>();

    /** 轮询计数器 */
    /** 计数器 */
    private final AtomicInteger counter = new AtomicInteger(0);

    /** 负载均衡策略 */
    private volatile LoadBalanceStrategy strategy = LoadBalanceStrategy.ROUND_ROBIN;

    public DataSourceGroup(String groupName) {
        this.groupName = groupName;
    }

    /**
     * 添加组成员（幂等，同名数据源不重复添加）
     *
     * @param dataSource     数据源实例
     * @param dataSourceName 数据源名称
     */
    public void addMember(DataSource dataSource, String dataSourceName) {
        int index = dataSourceNames.indexOf(dataSourceName);
        if (index >= 0) {
            // 同名成员替换为最新实例（如监控补包装后替换引用）
            if (dataSources.get(index) != dataSource) {
                dataSources.set(index, dataSource);
                log.info("[数据源分组] 分组 '{}' 更新成员: {}", groupName, dataSourceName);
            }
            return;
        }
        this.dataSources.add(dataSource);
        this.dataSourceNames.add(dataSourceName);
        log.info("[数据源分组] 分组 '{}' 添加成员: {}, 当前成员数: {}", groupName, dataSourceName, dataSources.size());
    }

    /**
     * 根据负载均衡策略选择一个数据源
     *
     * @return 选中的数据源
     */
    public DataSource select() {
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("数据源分组 '" + groupName + "' 中没有可用的数据源");
        }
        if (dataSources.size() == 1) {
            return dataSources.get(0);
        }
        int index = strategy == LoadBalanceStrategy.ROUND_ROBIN
                ? Math.floorMod(counter.getAndIncrement(), dataSources.size())
                : (int) (System.nanoTime() % dataSources.size());
        DataSource selected = dataSources.get(index);
        log.debug("[数据源分组] 分组 '{}' 选择: {} (策略: {}, 索引: {})", groupName, dataSourceNames.get(index), strategy, index);
        return selected;
    }

    /**
     * 获取组名
     *
     * @return 组名
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * 获取组内数据源数量
     *
     * @return 数据源数量
     */
    public int size() {
        return dataSources.size();
    }

    /**
     * 设置负载均衡策略
     *
     * @param strategy 策略
     */
    public void setStrategy(LoadBalanceStrategy strategy) {
        this.strategy = strategy;
        log.info("[数据源分组] 分组 '{}' 切换策略为: {}", groupName, strategy);
    }

    /**
     * 负载均衡策略
     */
    public enum LoadBalanceStrategy {
        /** 轮询 */
        ROUND_ROBIN,
        /** 随机 */
        RANDOM
    }
}
