package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceAccessRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门禁出入记录服务：定时抽取（落库）+ 查询。
 *
 * <p>抽取方向为「设备会话实时直查 → 落库」：遍历在线会话的
 * {@code queryAccessRecords}，把厂商返回的 {@code AccessRecord} 转成本地实体，
 * 按 (deviceId, occurredAt) 幂等落库，供门禁记录页离线查询。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceAccessRecordService {

    /**
     * 抽取指定时间窗口内的门禁记录（由时间轮任务调用）。
     *
     * @param start 开始时间（含）
     * @param end   结束时间（含）
     * @return 新增落库条数
     */
    int extractRecent(LocalDateTime start, LocalDateTime end);

    /**
     * 按条件查询已落库的门禁记录，时间倒序。
     *
     * @param deviceId 设备 ID，可空
     * @param start    开始时间，可空
     * @param end      结束时间，可空
     * @return 记录列表
     */
    List<DeviceAccessRecord> queryRecords(String deviceId, LocalDateTime start, LocalDateTime end);
}