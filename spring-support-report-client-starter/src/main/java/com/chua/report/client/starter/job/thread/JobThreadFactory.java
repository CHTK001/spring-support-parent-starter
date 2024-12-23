package com.chua.report.client.starter.job.thread;

import com.chua.report.client.starter.job.handler.JobHandler;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 作业线程
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
public class JobThreadFactory {

    private static final JobThreadFactory INSTANCE = new JobThreadFactory();
    private static final ConcurrentMap<Integer, JobThread> jobThreadRepository = new ConcurrentHashMap<Integer, JobThread>();

    /**
     * 注册作业线程
     *
     * @param jobId           作业id
     * @param handler         处理程序
     * @param removeOldReason 删除旧原因
     * @return {@link JobThread}
     */
    public static JobThread registJobThread(int jobId, JobHandler handler, String removeOldReason){
        JobThread newJobThread = new JobThread(jobId, handler);
        newJobThread.start();
        log.info(">>>>>>>>>>> job regist JobThread success, jobId:{}, handler:{}", jobId, handler);
        SpringBeanUtils.autowireBean(newJobThread);
        JobThread oldJobThread = jobThreadRepository.put(jobId, newJobThread);
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
        }

        return newJobThread;
    }

    /**
     * 删除作业线程
     *
     * @param jobId           作业id
     * @param removeOldReason 删除旧原因
     * @return {@link JobThread}
     */
    public static JobThread removeJobThread(int jobId, String removeOldReason){
        JobThread oldJobThread = jobThreadRepository.remove(jobId);
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();

            return oldJobThread;
        }
        return null;
    }

    /**
     * 加载作业线程
     *
     * @param jobId 作业id
     * @return {@link JobThread}
     */
    public static JobThread loadJobThread(int jobId){
        return jobThreadRepository.get(jobId);
    }
    /**
     * 获取实例
     *
     * @return {@link JobThreadFactory}
     */
    public static JobThreadFactory getInstance() {
        return INSTANCE;
    }
}
