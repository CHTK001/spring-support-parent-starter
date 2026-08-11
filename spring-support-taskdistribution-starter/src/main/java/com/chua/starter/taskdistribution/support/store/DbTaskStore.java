package com.chua.starter.taskdistribution.support.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.taskdistribution.store.TaskStore;
import com.chua.common.support.taskdistribution.task.Task;
import com.chua.common.support.taskdistribution.task.TaskResult;
import com.chua.common.support.taskdistribution.task.TaskStatus;
import com.chua.starter.taskdistribution.support.entity.SysTaskStore;
import com.chua.starter.taskdistribution.support.mapper.TaskStoreMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库任务存储实现。
 *
 * <p>基于 MyBatis-Plus 将任务持久化到数据库，支持应用重启后自动恢复。
 * 表结构：{@code sys_task_store}（taskId / taskType / taskStatus / taskJson / resultJson）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DbTaskStore implements TaskStore {

    /**
     * 内存缓存（减少数据库查询）
     */
    private final Map<String, SysTaskStore> cache = new ConcurrentHashMap<>();

    /**
     * Mapper
     */
    private final TaskStoreMapper mapper;

    /**
     * 构造数据库任务存储。
     *
     * @param mapper MyBatis-Plus Mapper
     */
    public DbTaskStore(TaskStoreMapper mapper) {
        this.mapper = mapper;
        loadFromDb();
    }

    /**
     * 启动时从数据库加载所有任务到缓存。
     */
    private void loadFromDb() {
        try {
            List<SysTaskStore> list = mapper.selectList(null);
            for (SysTaskStore entity : list) {
                cache.put(entity.getTaskId(), entity);
            }
            log.info("数据库任务存储已加载, 任务数: {}", cache.size());
        } catch (Exception e) {
            log.warn("加载数据库任务失败: {}", e.getMessage());
        }
    }

    @Override
    public void saveTask(Task<?> task, TaskStatus status) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        SysTaskStore entity = cache.get(task.getTaskId());
        if (entity == null) {
            entity = new SysTaskStore();
            entity.setTaskId(task.getTaskId());
            entity.setCreateTime(LocalDateTime.now());
        }
        entity.setTaskType(task.getTaskType());
        entity.setTaskStatus(status.name());
        entity.setTaskJson(Json.toJson(task));
        entity.setUpdateTime(LocalDateTime.now());
        cache.put(task.getTaskId(), entity);

        try {
            upsert(entity);
        } catch (Exception e) {
            log.warn("保存任务失败: {}", task.getTaskId(), e);
        }
    }

    @Override
    public void updateStatus(String taskId, TaskStatus status) {
        if (taskId == null) {
            return;
        }
        SysTaskStore entity = cache.get(taskId);
        if (entity != null) {
            entity.setTaskStatus(status.name());
            entity.setUpdateTime(LocalDateTime.now());
        }
        try {
            mapper.update(null, new LambdaUpdateWrapper<SysTaskStore>()
                    .eq(SysTaskStore::getTaskId, taskId)
                    .set(SysTaskStore::getTaskStatus, status.name()));
        } catch (Exception e) {
            log.warn("更新任务状态失败: {}", taskId, e);
        }
    }

    @Override
    public void saveResult(TaskResult<?> result) {
        if (result == null || result.getTaskId() == null) {
            return;
        }
        SysTaskStore entity = cache.get(result.getTaskId());
        if (entity == null) {
            entity = new SysTaskStore();
            entity.setTaskId(result.getTaskId());
            entity.setCreateTime(LocalDateTime.now());
        }
        entity.setResultJson(Json.toJson(result));
        entity.setUpdateTime(LocalDateTime.now());
        cache.put(result.getTaskId(), entity);

        try {
            upsert(entity);
        } catch (Exception e) {
            log.warn("保存结果失败: {}", result.getTaskId(), e);
        }
    }

    /**
     * 插入或更新（upsert 语义）。
     *
     * @param entity 实体
     */
    private void upsert(SysTaskStore entity) {
        SysTaskStore existing = mapper.selectById(entity.getTaskId());
        if (existing == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Task<?> getTask(String taskId) {
        SysTaskStore entity = cache.get(taskId);
        if (entity == null) {
            entity = mapper.selectOne(new LambdaQueryWrapper<SysTaskStore>()
                    .eq(SysTaskStore::getTaskId, taskId));
            if (entity != null) {
                cache.put(taskId, entity);
            }
        }
        if (entity != null && entity.getTaskJson() != null) {
            return Json.fromJson(entity.getTaskJson(), Task.class);
        }
        return null;
    }

    @Override
    public TaskStatus getStatus(String taskId) {
        SysTaskStore entity = cache.get(taskId);
        if (entity == null) {
            entity = mapper.selectOne(new LambdaQueryWrapper<SysTaskStore>()
                    .eq(SysTaskStore::getTaskId, taskId));
            if (entity != null) {
                cache.put(taskId, entity);
            }
        }
        if (entity != null && entity.getTaskStatus() != null) {
            try {
                return TaskStatus.valueOf(entity.getTaskStatus());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TaskResult<?> getResult(String taskId) {
        SysTaskStore entity = cache.get(taskId);
        if (entity == null) {
            entity = mapper.selectOne(new LambdaQueryWrapper<SysTaskStore>()
                    .eq(SysTaskStore::getTaskId, taskId));
            if (entity != null) {
                cache.put(taskId, entity);
            }
        }
        if (entity != null && entity.getResultJson() != null) {
            return Json.fromJson(entity.getResultJson(), TaskResult.class);
        }
        return null;
    }

    @Override
    public List<Task<?>> getRecoverableTasks() {
        List<Task<?>> result = new ArrayList<>();
        try {
            List<SysTaskStore> list = mapper.selectList(new LambdaQueryWrapper<SysTaskStore>()
                    .in(SysTaskStore::getTaskStatus, "PENDING", "RUNNING"));
            for (SysTaskStore entity : list) {
                cache.put(entity.getTaskId(), entity);
                if (entity.getTaskJson() != null) {
                    Task<?> task = Json.fromJson(entity.getTaskJson(), Task.class);
                    if (task != null) {
                        result.add(task);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询可恢复任务失败", e);
        }
        return result;
    }

    @Override
    public void removeTask(String taskId) {
        cache.remove(taskId);
        try {
            mapper.delete(new LambdaQueryWrapper<SysTaskStore>()
                    .eq(SysTaskStore::getTaskId, taskId));
        } catch (Exception e) {
            log.warn("删除任务失败: {}", taskId, e);
        }
    }

    @Override
    public void clear() {
        cache.clear();
        try {
            mapper.delete(null);
        } catch (Exception e) {
            log.warn("清空任务失败", e);
        }
    }

    @Override
    public void close() {
        cache.clear();
    }
}