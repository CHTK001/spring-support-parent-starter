package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceTaskState;
import com.chua.starter.device.support.service.DeviceTaskStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备定时任务状态 控制器
 *
 * <p>暴露时间轮任务的状态（interval / running / lastRunAt）与间隔修改接口，
 * 供「定时任务配置」页展示与调整；任务名与业务映射见 seed SQL 与 DeviceScheduler。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/device/tasks")
@RequiredArgsConstructor
public class DeviceTaskStateController {

    /**
     * 任务状态服务
     */
    private final DeviceTaskStateService taskStateService;

    /**
     * 列出全部任务状态
     *
     * @return 任务状态列表
     */
    @GetMapping("/list")
    public ReturnResult<List<DeviceTaskState>> list() {
        return ReturnResult.ok(taskStateService.list());
    }

    /**
     * 更新任务运行间隔（秒）
     *
     * @param taskName 任务名称
     * @param interval 间隔（秒），必须大于 0
     */
    @PutMapping("/{taskName}/interval")
    public ReturnResult<Void> updateInterval(@PathVariable String taskName,
                                             @RequestParam int interval) {
        if (interval <= 0) {
            throw new IllegalArgumentException("任务间隔必须大于 0");
        }
        taskStateService.updateInterval(taskName, interval);
        return ReturnResult.ok();
    }
}