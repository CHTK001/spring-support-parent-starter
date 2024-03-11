package com.chua.starter.monitor.job.handler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 作业处理程序
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public class JobHandlerFactory {

    private static final JobHandlerFactory INSTANCE = new JobHandlerFactory();

    private static ConcurrentMap<String, JobHandler> jobHandlerRepository = new ConcurrentHashMap<>();

    public static JobHandlerFactory getInstance() {
        return INSTANCE;
    }


    /**
     * 登记
     *
     * @param name       名称
     * @param jobHandler 作业处理程序
     * @return {@link JobHandler}
     */
    public JobHandler register(String name, JobHandler jobHandler) {
        return jobHandlerRepository.putIfAbsent(name, jobHandler);
    }

    /**
     * 收到
     *
     * @param name 名称
     * @return {@link JobHandler}
     */
    public JobHandler get(String name) {
        return jobHandlerRepository.get(name);
    }

    public Set<String> keys() {
        return jobHandlerRepository.keySet();
    }
}
