package com.chua.starter.collect.store;

import com.chua.starter.collect.engine.CollectSink;

/**
 * 采集结果落库通道工厂。
 *
 * <p>执行服务在每次任务启动时经本工厂取得落库通道。默认实现按
 * {@code plugin.collect.persistence.type} 选择内置实现（database=数据库落库，
 * none=只跑流程不落结果）；宿主系统注册自己的 {@code CollectSinkFactory} Bean
 * 即可整体替换持久化实现（如写 ES、写数仓），替换后执行引擎与服务层无感知。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface CollectSinkFactory {

    /**
     * 创建一次任务执行使用的落库通道。
     *
     * <p>一次任务一个实例；实现方应保证通道把失败消化为日志，不把采集流程带崩。</p>
     *
     * @param flowId  流程ID，草稿运行为 null
     * @param taskId  任务ID
     * @param persist 是否需要把采集结果写入存储
     * @return 落库通道
     */
    CollectSink create(Integer flowId, Long taskId, boolean persist);
}
