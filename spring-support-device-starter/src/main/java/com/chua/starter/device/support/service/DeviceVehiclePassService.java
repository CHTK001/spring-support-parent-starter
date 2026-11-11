package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceVehiclePass;
import com.chua.starter.device.support.model.VehiclePassPullResult;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.TimeRange;
import com.chua.starter.mybatis.entity.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆过车记录服务。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceVehiclePassService {

    /**
     * 记录一次过车。
     *
     * @param pass 过车记录
     * @return 入库后的记录
     */
    DeviceVehiclePass record(DeviceVehiclePass pass);

    /**
     * 查询过车记录。
     *
     * @param gateId    车闸标识，可选
     * @param direction 方向，可选
     * @param start     起始时间，可选
     * @param end       结束时间，可选
     * @return 过车记录
     */
    List<DeviceVehiclePass> listPasses(String deviceId, String gateId, String direction,
                                       LocalDateTime start, LocalDateTime end);

    /**
     * 分页查询过车记录。
     *
     * @param page      页码
     * @param size      每页条数
     * @param gateId    车闸标识，可选
     * @param direction 方向，可选
     * @param start     起始时间，可选
     * @param end       结束时间，可选
     * @return 分页结果
     */
    PageResult<DeviceVehiclePass> pagePasses(int page, int size, String deviceId, String gateId, String direction,
                                             LocalDateTime start, LocalDateTime end);

    /**
     * 从平台会话拉取过车记录并落库（按 车闸+车牌+时间 去重）。
     *
     * @param session 设备会话
     * @param range   时间段
     * @return 新增条数
     */
    default int pull(DeviceSession session, TimeRange range) {
        return pull(session, range, 0L).created();
    }

    /**
     * 从指定游标后拉取过车记录并落库。
     *
     * @param session 设备会话
     * @param range   时间段（仅用于入库过滤）
     * @param afterId 平台侧起始消息 ID（不含）
     * @return 新增条数与拉取后的游标
     */
    VehiclePassPullResult pull(DeviceSession session, TimeRange range, long afterId);
}
