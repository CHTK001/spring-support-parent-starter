package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceAlarm;
import com.chua.starter.device.support.model.AlarmQuery;
import com.chua.starter.mybatis.entity.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 设备告警查询与处理服务。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceAlarmService {

    /**
     * 分页查询告警。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DeviceAlarm> page(AlarmQuery query);

    /**
     * 更新处理状态。
     *
     * @param id      告警 ID
     * @param status  目标状态：unhandled / handling / resolved / ignored
     * @param handler 处理人，可空
     * @return 更新后的告警
     */
    DeviceAlarm updateStatus(String id, String status, String handler);

    /**
     * 批量更新处理状态。
     *
     * <p>告警页的批量处理 / 批量忽略入口：一次把选中的多条告警置为同一目标状态，
     * 处理人与处理时间统一记录。返回实际更新的条数。</p>
     *
     * @param ids     告警 ID 集合，非空
     * @param status  目标状态：unhandled / handling / resolved / ignored
     * @param handler 处理人，可空
     * @return 实际更新条数
     */
    int updateStatusBatch(List<String> ids, String status, String handler);

    /**
     * 概览指标：总数 / 未处理 / 严重 / 今日新增。
     *
     * @return 指标键值对
     */
    Map<String, Object> stats();

    /**
     * 平台自检落告警（离线检测 / 事件轮询等内部链路调用，非 webhook 入站）。
     *
     * <p>同一设备同一类型在 {@code DEDUPE_WINDOW} 内已存在未处理 / 处理中告警时
     * 不重复插入，避免离线检测每轮都新增一条刷屏。</p>
     *
     * @param deviceId 设备 ID
     * @param type     告警类型（MOTION / IO_ALARM / TEMP / NETWORK / OTHER）
     * @param title    告警标题
     * @param detail   告警详情，可空
     * @param level    级别：critical / major / minor / info
     */
    void record(String deviceId, String type, String title, String detail, String level);

    /**
     * 设备恢复时自动消警：把该设备该类型仍未处理 / 处理中的告警标记为已解决。
     *
     * <p>处理人记为 {@code system}（平台自动消警，非人工操作）；没有未解决告警时
     * 不产生任何副作用。</p>
     *
     * @param deviceId 设备 ID
     * @param type     告警类型
     * @return 被自动解决的告警条数
     */
    int resolveOpen(String deviceId, String type);
}
