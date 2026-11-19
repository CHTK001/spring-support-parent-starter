package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceSyncOutbox;
import com.chua.starter.device.support.entity.DeviceSyncRecord;
import com.chua.starter.device.support.model.SyncOutboxQuery;
import com.chua.starter.device.support.model.SyncRecordQuery;
import com.chua.starter.mybatis.entity.PageResult;

import java.util.Map;

/**
 * 同步记录查询服务：同步执行记录 + 推送队列（outbox）的查询与重试。
 *
 * <p>记录由 {@link DeviceSyncService} 在每次拉取/写回时落库；本服务只负责
 * 读取视图与 outbox 重试编排，不重复实现同步业务。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceSyncRecordService {

    /**
     * 分页查询同步执行记录。
     *
     * @param query 筛选条件（vendor / mode / phase / status / start / end）
     * @return 分页结果，按开始时间倒序
     */
    PageResult<DeviceSyncRecord> pageRecords(SyncRecordQuery query);

    /**
     * 分页查询推送队列。
     *
     * @param query 筛选条件（vendor / action / status / deviceId）
     * @return 分页结果，按创建时间倒序
     */
    PageResult<DeviceSyncOutbox> pageOutbox(SyncOutboxQuery query);

    /**
     * 概览计数：同步记录按状态、推送队列按状态。
     *
     * @return records / outbox 两个维度的状态计数
     */
    Map<String, Object> summary();

    /**
     * 重试一条推送队列记录：重新写回厂商，成功置 sent，失败按退避推迟下次重试。
     *
     * @param outboxId 队列记录 ID
     * @return 更新后的队列记录
     */
    DeviceSyncOutbox retryOutbox(String outboxId);
}
