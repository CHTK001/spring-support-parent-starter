package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceVehicle;
import com.chua.starter.device.support.model.VehicleRenewRequest;
import com.chua.starter.device.support.service.DeviceVehicleService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 车辆档案控制器。
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/api/device/vehicle")
@RequiredArgsConstructor
public class VehicleController {

    /**
     * 车辆服务
     */
    private final DeviceVehicleService vehicleService;

    /**
     * 新增车辆。
     *
     * @param vehicle 车辆
     * @return 入库后的车辆
     */
    @PostMapping
    public ReturnResult<DeviceVehicle> addVehicle(@RequestBody DeviceVehicle vehicle) {
        return ReturnResult.ok(vehicleService.addVehicle(vehicle));
    }

    /**
     * 修改车辆。
     *
     * @param id      主键
     * @param vehicle 车辆
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public ReturnResult<Void> updateVehicle(@PathVariable String id, @RequestBody DeviceVehicle vehicle) {
        vehicleService.updateVehicle(id, vehicle);
        return ReturnResult.ok();
    }

    /**
     * 删除车辆。
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> deleteVehicle(@PathVariable String id) {
        vehicleService.deleteVehicle(id);
        return ReturnResult.ok();
    }

    /**
     * 查询车辆。
     *
     * @param id 主键
     * @return 车辆
     */
    @GetMapping("/{id}")
    public ReturnResult<DeviceVehicle> getVehicle(@PathVariable String id) {
        return ReturnResult.ok(vehicleService.getVehicle(id));
    }

    /**
     * 按车牌查询车辆。
     *
     * @param plateNo 车牌号
     * @return 车辆
     */
    @GetMapping("/by-plate")
    public ReturnResult<DeviceVehicle> getByPlateNo(@RequestParam String plateNo) {
        return ReturnResult.ok(vehicleService.getByPlateNo(plateNo));
    }

    /**
     * 车辆列表。
     *
     * @param keyword 关键字，可选
     * @return 车辆列表
     */
    @GetMapping("/list")
    public ReturnResult<List<DeviceVehicle>> listVehicles(@RequestParam(required = false) String keyword) {
        return ReturnResult.ok(vehicleService.listVehicles(keyword));
    }

    /**
     * 车辆分页。
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字，可选
     * @return 分页结果
     */
    @GetMapping("/page")
    public PageResult<DeviceVehicle> pageVehicles(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String keyword) {
        return vehicleService.pageVehicles(page, size, keyword);
    }

    /**
     * 车辆续期：按月顺延或指定新的有效期止。
     *
     * @param id      主键
     * @param request 续期参数；为空默认顺延 12 个月
     * @return 续期后的车辆
     */
    @PostMapping("/{id}/renew")
    public ReturnResult<DeviceVehicle> renewVehicle(@PathVariable String id,
                                                    @RequestBody(required = false) VehicleRenewRequest request) {
        Integer months = request == null ? null : request.getMonths();
        LocalDateTime validTo = parseTime(request == null ? null : request.getValidTo());
        return ReturnResult.ok(vehicleService.renewVehicle(id, months, validTo));
    }

    /**
     * 解析可选时间。
     *
     * @param text 时间文本
     * @return 时间，空或非法返回 null
     */
    private static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim().replace('T', ' '), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("有效期格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
