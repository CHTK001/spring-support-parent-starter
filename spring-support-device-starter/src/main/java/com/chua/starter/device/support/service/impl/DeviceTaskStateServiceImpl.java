package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.device.support.entity.DeviceTaskState;
import com.chua.starter.device.support.mapper.DeviceTaskStateMapper;
import com.chua.starter.device.support.service.DeviceTaskStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 任务状态服务实现（Trigger 模式）
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTaskStateServiceImpl implements DeviceTaskStateService {

    /**
     * 任务状态 映射器
     */
    private final DeviceTaskStateMapper taskStateMapper;

    @Override
    /**
     * 是否应该运行
    */
    public boolean shouldRun(String taskName) {
        DeviceTaskState state = getState(taskName);
        if (state == null) {
            return false;
        }
        if (Boolean.TRUE.equals(state.getDeviceTaskStateRunning())) {
            return false;
        }
        LocalDateTime lastRun = state.getDeviceTaskStateLastRunAt();
        int interval = state.getDeviceTaskStateInterval() != null ? state.getDeviceTaskStateInterval() : 30;
        if (lastRun == null) {
            return true;
        }
        return LocalDateTime.now().minusSeconds(interval).isAfter(lastRun);
    }

    @Override
    /**
     * 标记Running
    */
    public boolean markRunning(String taskName) {
        LambdaUpdateWrapper<DeviceTaskState> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DeviceTaskState::getDeviceTaskStateName, taskName);
        wrapper.set(DeviceTaskState::getDeviceTaskStateRunning, true);
        wrapper.set(DeviceTaskState::getDeviceTaskStateLastRunAt, LocalDateTime.now());
        return taskStateMapper.update(null, wrapper) > 0;
    }

    @Override
    /**
     * 标记饰面
    */
    public void markFinished(String taskName) {
        LambdaUpdateWrapper<DeviceTaskState> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DeviceTaskState::getDeviceTaskStateName, taskName);
        wrapper.set(DeviceTaskState::getDeviceTaskStateRunning, false);
        taskStateMapper.update(null, wrapper);
    }

    @Override
    /**
     * 获取状态
    */
    public DeviceTaskState getState(String taskName) {
        LambdaQueryWrapper<DeviceTaskState> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTaskState::getDeviceTaskStateName, taskName);
        return taskStateMapper.selectOne(wrapper);
    }

    @Override
    /**
     * 更新间隔
    */
    public void updateInterval(String taskName, int interval) {
        LambdaUpdateWrapper<DeviceTaskState> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DeviceTaskState::getDeviceTaskStateName, taskName);
        wrapper.set(DeviceTaskState::getDeviceTaskStateInterval, interval);
        taskStateMapper.update(null, wrapper);
        log.info("任务间隔已更新: name={}, interval={}s", taskName, interval);
    }
}
