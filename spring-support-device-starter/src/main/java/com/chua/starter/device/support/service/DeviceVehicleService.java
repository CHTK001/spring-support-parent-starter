package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceVehicle;
import com.chua.starter.mybatis.entity.PageResult;

import java.util.List;

/**
 * 车辆档案与过车记录服务。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceVehicleService {

    /**
     * 新增车辆。
     *
     * @param vehicle 车辆
     * @return 入库后的车辆
     */
    DeviceVehicle addVehicle(DeviceVehicle vehicle);

    /**
     * 修改车辆。
     *
     * @param id      主键
     * @param vehicle 车辆
     */
    void updateVehicle(String id, DeviceVehicle vehicle);

    /**
     * 删除车辆（逻辑删除）。
     *
     * @param id 主键
     */
    void deleteVehicle(String id);

    /**
     * 查询车辆。
     *
     * @param id 主键
     * @return 车辆
     */
    DeviceVehicle getVehicle(String id);

    /**
     * 车牌精确查询。
     *
     * @param plateNo 车牌号
     * @return 车辆，未找到返回 null
     */
    DeviceVehicle getByPlateNo(String plateNo);

    /**
     * 车辆列表。
     *
     * @param keyword 车牌/车主/电话关键字
     * @return 车辆列表
     */
    List<DeviceVehicle> listVehicles(String keyword);

    /**
     * 车辆分页。
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字
     * @return 分页结果
     */
    PageResult<DeviceVehicle> pageVehicles(int page, int size, String keyword);
}
