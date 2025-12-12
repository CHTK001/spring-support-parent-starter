package com.chua.report.client.starter.job.handler;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 作业处理程序工厂
 * <p>
 * 采用单例模式，负责管理所有已注册的JobHandler实例。
 * 使用ConcurrentHashMap保证线程安全。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
public class JobHandlerFactory {

    /**
     * 单例实例
     */
    private static final JobHandlerFactory INSTANCE = new JobHandlerFactory();

    /**
     * JobHandler存储仓库
     * key: 处理器名称
     * value: JobHandler实例
     */
    private static final ConcurrentMap<String, JobHandler> JOB_HANDLER_REPOSITORY = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止外部实例化
     */
    private JobHandlerFactory() {
    }

    /**
     * 获取工厂单例实例
     *
     * @return 工厂实例
     */
    public static JobHandlerFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 注册作业处理器
     * <p>
     * 如果处理器名称已存在，则不会覆盖原有处理器。
     * </p>
     *
     * @param name       处理器名称
     * @param jobHandler 作业处理程序实例
     * @return 如果名称已存在返回原有处理器，否则返回null
     */
    public JobHandler register(String name, JobHandler jobHandler) {
        log.debug("注册JobHandler: {}", name);
        JobHandler existing = JOB_HANDLER_REPOSITORY.putIfAbsent(name, jobHandler);
        if (existing != null) {
            log.warn("JobHandler已存在, 跳过注册: {}", name);
        }
        return existing;
    }

    /**
     * 根据名称获取处理器
     *
     * @param name 处理器名称
     * @return JobHandler实例，如果不存在返回null
     */
    public JobHandler get(String name) {
        return JOB_HANDLER_REPOSITORY.get(name);
    }

    /**
     * 获取所有已注册的处理器名称
     *
     * @return 处理器名称集合
     */
    public Set<String> keys() {
        return JOB_HANDLER_REPOSITORY.keySet();
    }

    /**
     * 获取已注册处理器数量
     *
     * @return 处理器数量
     */
    public int size() {
        return JOB_HANDLER_REPOSITORY.size();
    }

    /**
     * 判断处理器是否已注册
     *
     * @param name 处理器名称
     * @return 如果已注册返回true
     */
    public boolean contains(String name) {
        return JOB_HANDLER_REPOSITORY.containsKey(name);
    }
}
