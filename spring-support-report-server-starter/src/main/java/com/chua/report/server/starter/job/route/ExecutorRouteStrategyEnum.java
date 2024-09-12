package com.chua.report.server.starter.job.route;

import com.chua.report.server.starter.job.route.strategy.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by xuxueli on 17/3/10.
 */
@AllArgsConstructor
@Getter
public enum ExecutorRouteStrategyEnum {

    // 根据任务配置首先执行的策略
    FIRST("jobconf_route_first", new ExecutorRouteFirst()),
    // 根据任务配置最后执行的策略
    LAST("jobconf_route_last", new ExecutorRouteLast()),
    // 根据任务配置进行轮询执行的策略
    ROUND("jobconf_route_round", new ExecutorRouteRound()),
    // 根据任务配置随机选择执行者的策略
    RANDOM("jobconf_route_random", new ExecutorRouteRandom()),
    // 根据任务配置使用一致性哈希选择执行者的策略
    CONSISTENT_HASH("jobconf_route_consistenthash", new ExecutorRouteConsistentHash()),
    // 根据任务配置使用最少使用频率选择执行者的策略
    LEAST_FREQUENTLY_USED("jobconf_route_lfu", new ExecutorRouteLFU()),
    // 根据任务配置使用最近最少使用选择执行者的策略
    LEAST_RECENTLY_USED("jobconf_route_lru", new ExecutorRouteLRU()),
    // 根据任务配置进行分片广播执行的策略
    SHARDING_BROADCAST("jobconf_route_shard", null);
    private final String title;
    private final ExecutorRouter router;

    public static ExecutorRouteStrategyEnum match(String name, ExecutorRouteStrategyEnum defaultItem){
        if (name != null) {
            for (ExecutorRouteStrategyEnum item: ExecutorRouteStrategyEnum.values()) {
                if (item.name().equals(name)) {
                    return item;
                }
            }
        }
        return defaultItem;
    }

}
