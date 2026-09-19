package com.chua.starter.taskdistribution.support.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.taskdistribution.dispatcher.MdcDecorator;
import com.chua.common.support.taskdistribution.store.TaskStore;
import com.chua.common.support.taskdistribution.task.Task;
import com.chua.common.support.taskdistribution.task.TaskResult;
import com.chua.common.support.taskdistribution.task.TaskStatus;
import com.chua.starter.taskdistribution.support.entity.SysTaskStore;
import com.chua.starter.taskdistribution.support.mapper.TaskStoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库任务存储实现。
 *
 * <p>基于 MyBatis-Plus 将任务持久化到数据库，支持应用重启后自动恢复。
 * 表结构：{@code sys_task_store}（任务标识 / 任务类型 / 任务状态 / 任务json / 结果json）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DbTaskStore implements TaskStore {

    /**
     * 内存缓存（减少数据库查询）
     */
    private final Map<String, SysTaskStore> cache = new ConcurrentHashMap<>();

    /**
     * 映射器
     */
    private final TaskStoreMapper mapper;

    /**
     * 构造数据库任务存储。
     *
     * @param mapper MyBatis-Plus 映射器
     */
    public DbTaskStore(TaskStoreMapper mapper) {
        this.mapper = mapper;
        /**
         * 加载从db。
         */
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
    /** 保存任务 */
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
            if (task.getTaskId() != null) {
                MDC.put(MdcDecorator.KEY_TASK_ID, task.getTaskId());
            }
            if (task.getTraceId() != null) {
                MDC.put(MdcDecorator.KEY_TRACE_ID, task.getTraceId());
            }
            /**
             * upsert。
             * @param entity 实体
             */
            upsert(entity);
        } catch (Exception e) {
            log.warn("保存任务失败: {}", task.getTaskId(), e);
        } finally {
            MDC.remove(MdcDecorator.KEY_TASK_ID);
            MDC.remove(MdcDecorator.KEY_TRACE_ID);
        }
    }

    @Override
    /** 更新状态 */
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
            if (taskId != null) {
                MDC.put(MdcDecorator.KEY_TASK_ID, taskId);
            }
            mapper.update(null, new LambdaUpdateWrapper<SysTaskStore>()
                    .eq(SysTaskStore::getTaskId, taskId)
                    .set(SysTaskStore::getTaskStatus, status.name())
                    .set(SysTaskStore::getUpdateTime, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("更新任务状态失败: {}", taskId, e);
        } finally {
            MDC.remove(MdcDecorator.KEY_TASK_ID);
        }
    }

    @Override
    /** 保存结果 */
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
            if (result.getTaskId() != null) {
                MDC.put(MdcDecorator.KEY_TASK_ID, result.getTaskId());
            }
            /**
             * upsert。
             * @param entity 实体
             */
            upsert(entity);
        } catch (Exception e) {
            log.warn("保存结果失败: {}", result.getTaskId(), e);
        } finally {
            MDC.remove(MdcDecorator.KEY_TASK_ID);
        }
    }

    /**
     * 插入或更新（upsert 语义，基于 任务标识 同步保证原子性）。
     *
     * @param entity 实体
     */
    private void upsert(SysTaskStore entity) {
        synchronized (entity.getTaskId().intern()) {
            try {
                mapper.insert(entity);
            } catch (Exception e) {
                // 主键冲突，执行更新
                try {
                    mapper.updateById(entity);
                } catch (Exception updateEx) {
                    log.warn("更新任务记录失败: {}", entity.getTaskId(), updateEx);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * 获取任务。
     * @param taskId 任务标识
     * @return 获取任务的结果
     */
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
    /** 获取状态 */
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
    /**
     * 获取结果。
     * @param taskId 任务标识
     * @return 获取结果的结果
     */
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
    /** 移除任务 */
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
    /** Clear */
    public void clear() {
        cache.clear();
        try {
            mapper.delete(null);
        } catch (Exception e) {
            log.warn("清空任务失败", e);
        }
    }

    @Override
    /** 关闭 */
    public void close() {
        cache.clear();
    }
}
