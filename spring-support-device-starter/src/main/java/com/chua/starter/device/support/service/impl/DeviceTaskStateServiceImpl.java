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
import java.util.ArrayList;
import java.util.List;

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
     * 凭据级任务名前缀，与 {@code DeviceScheduleManager.CredentialTask} 保持一致
     */
    private static final List<String> CREDENTIAL_TASK_PREFIXES = List.of(
            "offlineDetect", "eventPoll", "deviceSync", "orgSync", "metricCollect");

    /**
     * 任务状态 映射器
     */
    private final DeviceTaskStateMapper taskStateMapper;

    @Override
    /**
     * 列出全部任务状态
     * @return 返回值
    */
    public List<DeviceTaskState> list() {
        return taskStateMapper.selectList(
                new LambdaQueryWrapper<DeviceTaskState>()
                        .orderByAsc(DeviceTaskState::getDeviceTaskStateName));
    }

    @Override
    /**
     * 是否应该运行
     * @param taskName 字符串
     * @return 返回值
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
        // [P3C A20 豁免]
        int interval = state.getDeviceTaskStateInterval() != null ? state.getDeviceTaskStateInterval() : 30;
        if (lastRun == null) {
            return true;
        }
        return LocalDateTime.now().minusSeconds(interval).isAfter(lastRun);
    }

    @Override
    /**
     * 标记Running
     * @param taskName 字符串
     * @return 返回值
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
     * @param taskName 字符串
     * @return 返回值
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
     * @param taskName 字符串
     * @return 返回值
    */
    public DeviceTaskState getState(String taskName) {
        LambdaQueryWrapper<DeviceTaskState> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTaskState::getDeviceTaskStateName, taskName);
        return taskStateMapper.selectOne(wrapper);
    }

    @Override
    /**
     * 更新间隔
     * @param interval 整数
     * @param taskName 字符串
     * @return 返回值
    */
    public void updateInterval(String taskName, int interval) {
        LambdaUpdateWrapper<DeviceTaskState> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DeviceTaskState::getDeviceTaskStateName, taskName);
        wrapper.set(DeviceTaskState::getDeviceTaskStateInterval, interval);
        taskStateMapper.update(null, wrapper);
        log.info("任务间隔已更新: name={}, interval={}s", taskName, interval);
    }

    @Override
    /**
     * 补种任务状态行
     * @param name 任务名称
     * @param interval 间隔
     * @param description 描述
     * @return 是否新补种
    */
    public boolean seed(String name, int interval, String description) {
        if (getState(name) != null) {
            return false;
        }
        DeviceTaskState state = new DeviceTaskState();
        state.setDeviceTaskStateName(name);
        state.setDeviceTaskStateRunning(false);
        state.setDeviceTaskStateInterval(interval);
        state.setDeviceTaskStateDescription(description);
        return taskStateMapper.insert(state) > 0;
    }

    @Override
    /**
     * 删除某套凭据名下的全部任务状态行
     * @param credentialId 凭据 ID
     * @return 删除的行数
    */
    public int deleteByCredentialId(String credentialId) {
        if (credentialId == null || credentialId.isEmpty()) {
            return 0;
        }
        // 逐前缀精确匹配，避免 LIKE 下划线通配把别的凭据行一起带走
        List<String> names = new ArrayList<>(CREDENTIAL_TASK_PREFIXES.size());
        for (String prefix : CREDENTIAL_TASK_PREFIXES) {
            names.add(prefix + "_" + credentialId);
        }
        LambdaQueryWrapper<DeviceTaskState> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DeviceTaskState::getDeviceTaskStateName, names);
        int removed = taskStateMapper.delete(wrapper);
        log.info("清理凭据级任务状态行: credentialId={}, removed={}", credentialId, removed);
        return removed;
    }
}
