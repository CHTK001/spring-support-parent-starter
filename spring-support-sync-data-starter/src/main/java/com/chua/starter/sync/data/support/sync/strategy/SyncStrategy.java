package com.chua.starter.sync.data.support.sync.strategy;

import com.chua.starter.sync.data.support.adapter.DataSourceAdapter;

/**
 * 同步策略接口
 *
 * @author System
 * @since 2026/03/09
 */
public interface SyncStrategy {

    /**
     * 执行同步
     *
     * @param source 源数据适配器
     * @param target 目标数据适配器
     * @param context 同步上下文
     * @return 同步结果
     */
    SyncResult execute(DataSourceAdapter source, DataSourceAdapter target, SyncContext context);

    /**
     * 获取策略类型
     *
     * @return 策略类型
     */
    SyncMode getMode();
}
