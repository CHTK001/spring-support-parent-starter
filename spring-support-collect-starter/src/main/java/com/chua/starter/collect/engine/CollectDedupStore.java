package com.chua.starter.collect.engine;

/**
 * 业务主键判重存储。
 *
 * <p>由服务层用采集结果表实现，去重节点据此跳过历史已采条目。
 * 声明在引擎侧是为了让节点执行器不依赖 Mapper 与服务实现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectDedupStore {

    /**
     * 该主键是否已经采集过。
     *
     * @param flowId 当前流程ID
     * @param global 是否跨流程判重
     * @param key    业务主键值
     * @return 已存在返回 true
     */
    boolean seen(Integer flowId, boolean global, String key);
}
