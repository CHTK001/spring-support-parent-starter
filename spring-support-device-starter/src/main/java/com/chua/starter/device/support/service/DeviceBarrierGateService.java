package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceBarrierGate;
import com.chua.starter.mybatis.entity.PageResult;

import java.util.List;

/**
 * 车闸点位服务。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceBarrierGateService {

    /**
     * 新增车闸。
     *
     * @param gate 车闸
     * @return 入库后的车闸
     */
    DeviceBarrierGate addGate(DeviceBarrierGate gate);

    /**
     * 修改车闸。
     *
     * @param id   主键
     * @param gate 车闸
     */
    void updateGate(String id, DeviceBarrierGate gate);

    /**
     * 删除车闸（逻辑删除）。
     *
     * @param id 主键
     */
    void deleteGate(String id);

    /**
     * 查询车闸。
     *
     * @param id 主键
     * @return 车闸
     */
    DeviceBarrierGate getGate(String id);

    /**
     * 车闸列表。
     *
     * @param deviceId 设备标识，可选
     * @param direction 方向，可选
     * @return 车闸列表
     */
    List<DeviceBarrierGate> listGates(String deviceId, String direction);

    /**
     * 车闸分页。
     *
     * @param page     页码
     * @param size     每页条数
     * @param deviceId 设备标识，可选
     * @param direction 方向，可选
     * @return 分页结果
     */
    PageResult<DeviceBarrierGate> pageGates(int page, int size, String deviceId, String direction);
}
