package com.chua.starter.sync.service.sync;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.pojo.sync.SyncTableStatus;

/**
 * 同步任务表服务
 * <p>
 * 提供同步相关表的初始化、状态检查等功能
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
public interface SyncTableService {

    /**
     * 初始化同步任务相关表
     * <p>
     * 如果表已存在则跳过，否则创建表
     * </p>
     *
     * @param force 是否强制重建（会删除现有表和数据）
     * @return 初始化结果
     */
    ReturnResult<SyncTableStatus> initializeTables(boolean force);

    /**
     * 检查表状态
     *
     * @return 表状态信息
     */
    ReturnResult<SyncTableStatus> checkTableStatus();

    /**
     * 检查主表是否存在
     *
     * @return 是否存在
     */
    boolean isTableExists();
}
