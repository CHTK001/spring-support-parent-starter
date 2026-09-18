package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.TimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
* 门禁控制 控制器
*
* @since 4.0.0.42
* @author CH
 */
@RestController
@RequestMapping("/api/device/access")
@RequiredArgsConstructor
public class AccessController {

    /**
    * 时间格式
    */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
    * 设备会话管理器
    */
    private final DeviceSessionManager sessionManager;

    /**
    * 远程开门
    *
    * @param deviceId 设备 标识
    * @param doorId   门编号
    */
    @PostMapping("/unlock")
    public void unlock(@RequestParam String deviceId,
                       @RequestParam int doorId) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            /**
            * runtime异常。
            * @param deviceId deviceid
            * @return runtime异常的结果
            */
            throw new RuntimeException("设备不在线: " + deviceId);
        }
        session.remoteUnlock(doorId);
    }

    /**
    * 查询门禁出入记录
    *
    * @param deviceId 设备 标识
    * @param start    开始时间（yyyy-MM-dd HH:mm:ss）
    * @param end      结束时间（yyyy-MM-dd HH:mm:ss）
    * @return 出入记录列表
    */
    @GetMapping("/records")
    public List<AccessRecord> queryAccessRecords(@RequestParam String deviceId,
                                                 @RequestParam String start,
                                                 @RequestParam String end) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            /**
            * runtime异常。
            * @param deviceId deviceid
            * @return runtime异常的结果
            */
            throw new RuntimeException("设备不在线: " + deviceId);
        }
        return session.queryAccessRecords(parseRange(start, end));
    }

    /**
    * 解析范围
    *
    * @param start 启动
    * @param end 结束
    * @return 解析范围的结果
    */
    private TimeRange parseRange(String start, String end) {
        LocalDateTime s;
        LocalDateTime e;
        try {
            s = LocalDateTime.parse(start, DTF);
        } catch (Exception ex) {
            s = LocalDateTime.now().minusDays(1);
        }
        try {
            e = LocalDateTime.parse(end, DTF);
        } catch (Exception ex) {
            e = LocalDateTime.now();
        }
        /**
        * 时间范围。
        * @param s s
        * @param e e
        * @return 时间范围的结果
        */
        return new TimeRange(s, e);
    }
}
