package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceBarrierGate;
import com.chua.starter.device.support.entity.DeviceVehiclePass;
import com.chua.starter.device.support.service.DeviceBarrierGateService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.service.DeviceVehiclePassService;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.TimeRange;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车闸点位与过车记录控制器。
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/api/device/barrier")
@RequiredArgsConstructor
public class BarrierGateController {

    /**
     * 车闸服务
     */
    private final DeviceBarrierGateService gateService;

    /**
     * 过车记录服务
     */
    private final DeviceVehiclePassService passService;

    /**
     * 会话管理器
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 新增车闸。
     *
     * @param gate 车闸
     * @return 入库后的车闸
     */
    @PostMapping
    public ReturnResult<DeviceBarrierGate> addGate(@RequestBody DeviceBarrierGate gate) {
        return ReturnResult.ok(gateService.addGate(gate));
    }

    /**
     * 修改车闸。
     *
     * @param id   主键
     * @param gate 车闸
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public ReturnResult<Void> updateGate(@PathVariable String id, @RequestBody DeviceBarrierGate gate) {
        gateService.updateGate(id, gate);
        return ReturnResult.ok();
    }

    /**
     * 删除车闸。
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> deleteGate(@PathVariable String id) {
        gateService.deleteGate(id);
        return ReturnResult.ok();
    }

    /**
     * 查询车闸。
     *
     * @param id 主键
     * @return 车闸
     */
    @GetMapping("/{id}")
    public ReturnResult<DeviceBarrierGate> getGate(@PathVariable String id) {
        return ReturnResult.ok(gateService.getGate(id));
    }

    /**
     * 车闸列表。
     *
     * @param deviceId  设备标识，可选
     * @param direction 方向，可选
     * @return 车闸列表
     */
    @GetMapping("/list")
    public ReturnResult<List<DeviceBarrierGate>> listGates(@RequestParam(required = false) String deviceId,
                                                           @RequestParam(required = false) String direction) {
        return ReturnResult.ok(gateService.listGates(deviceId, direction));
    }

    /**
     * 车闸分页。
     *
     * @param page      页码
     * @param size      每页条数
     * @param deviceId  设备标识，可选
     * @param direction 方向，可选
     * @return 分页结果
     */
    @GetMapping("/page")
    public PageResult<DeviceBarrierGate> pageGates(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String deviceId,
                                                   @RequestParam(required = false) String direction) {
        return gateService.pageGates(page, size, deviceId, direction);
    }

    /**
     * 从平台拉取过车记录并落库。
     *
     * @param deviceId 设备标识（需已建立会话）
     * @param start    起始时间
     * @param end      结束时间
     * @return 新增条数
     */
    @PostMapping("/pass/pull")
    public ReturnResult<Integer> pullPasses(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null) {
            throw new IllegalStateException("设备未建立会话: " + deviceId);
        }
        return ReturnResult.ok(passService.pull(session, new TimeRange(start, end)));
    }

    /**
     * 记录过车。
     *
     * @param pass 过车记录
     * @return 入库后的记录
     */
    @PostMapping("/pass")
    public ReturnResult<DeviceVehiclePass> recordPass(@RequestBody DeviceVehiclePass pass) {
        return ReturnResult.ok(passService.record(pass));
    }

    /**
     * 过车记录列表。
     *
     * @param gateId    车闸标识，可选
     * @param direction 方向，可选
     * @param start     起始时间，可选
     * @param end       结束时间，可选
     * @return 过车记录
     */
    @GetMapping("/pass/list")
    public ReturnResult<List<DeviceVehiclePass>> listPasses(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String gateId,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ReturnResult.ok(passService.listPasses(deviceId, gateId, direction, start, end));
    }

    /**
     * 过车记录分页。
     *
     * @param page      页码
     * @param size      每页条数
     * @param gateId    车闸标识，可选
     * @param direction 方向，可选
     * @param start     起始时间，可选
     * @param end       结束时间，可选
     * @return 分页结果
     */
    @GetMapping("/pass/page")
    public PageResult<DeviceVehiclePass> pagePasses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String gateId,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return passService.pagePasses(page, size, deviceId, gateId, direction, start, end);
    }
}
