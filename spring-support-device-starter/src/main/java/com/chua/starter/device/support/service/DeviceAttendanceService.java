package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceAttendanceRecord;
import com.chua.starter.mybatis.entity.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤记录服务：只读查询 + 从门禁记录定时归集。
 *
 * <p>考勤不提供人工新增/修改/删除；数据来源只有两条：
 * webhook 推送和门禁记录归集。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
public interface DeviceAttendanceService {

    /**
     * 把指定时间窗口内的门禁记录归集为考勤记录（按门禁记录主键幂等）。
     *
     * @param start 开始时间（含）
     * @param end   结束时间（含）
     * @return 新增条数
     */
    int syncFromAccess(LocalDateTime start, LocalDateTime end);

    /**
     * 查询考勤记录，时间倒序。
     *
     * @param deviceId 设备 ID，可空
     * @param keyword  姓名/工号/卡号关键字，可空
     * @param start    开始时间，可空
     * @param end      结束时间，可空
     * @return 考勤记录
     */
    List<DeviceAttendanceRecord> listRecords(String deviceId, String keyword,
                                             LocalDateTime start, LocalDateTime end);

    /**
     * 分页查询考勤记录。
     *
     * @param page     页码
     * @param size     每页条数
     * @param deviceId 设备 ID，可空
     * @param keyword  姓名/工号/卡号关键字，可空
     * @param start    开始时间，可空
     * @param end      结束时间，可空
     * @return 分页结果
     */
    PageResult<DeviceAttendanceRecord> pageRecords(int page, int size, String deviceId, String keyword,
                                                   LocalDateTime start, LocalDateTime end);
}
