package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceAttendanceRecord;
import com.chua.starter.device.support.service.DeviceAttendanceService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 考勤记录控制器（只读）。
 *
 * <p>不提供新增/修改/删除接口：数据由 webhook 或门禁记录归集任务写入。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@RestController
@RequestMapping("/api/device/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    /**
     * 时间格式
     */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 考勤服务
     */
    private final DeviceAttendanceService attendanceService;

    /**
     * 考勤记录列表。
     *
     * @param deviceId 设备 ID，可选
     * @param keyword  姓名/工号/卡号，可选
     * @param start    开始时间，可选
     * @param end      结束时间，可选
     * @return 考勤记录
     */
    @GetMapping("/list")
    public ReturnResult<List<DeviceAttendanceRecord>> list(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return ReturnResult.ok(attendanceService.listRecords(deviceId, keyword,
                parseTime(start), parseTime(end)));
    }

    /**
     * 考勤记录分页。
     *
     * @param page     页码
     * @param size     每页条数
     * @param deviceId 设备 ID，可选
     * @param keyword  姓名/工号/卡号，可选
     * @param start    开始时间，可选
     * @param end      结束时间，可选
     * @return 分页结果
     */
    @GetMapping("/page")
    public PageResult<DeviceAttendanceRecord> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return attendanceService.pageRecords(page, size, deviceId, keyword,
                parseTime(start), parseTime(end));
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
            return LocalDateTime.parse(text.trim().replace('T', ' '), DTF);
        } catch (Exception ex) {
            return null;
        }
    }
}
