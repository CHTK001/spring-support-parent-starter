package com.chua.starter.job.support.sync;

import com.chua.common.support.network.server.SyncServer;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.dispatch.JobRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 脚本正文同步服务（调度中心侧）。
 *
 * <p>派发命令只携带脚本版本号（glueAt），正文经 {@link JobSyncTopics#GLUE} 主题在
 * "任务保存"与"执行器上线/重连"时推送一次，避免每拍把正文塞进命令负载。</p>
 *
 * <ul>
 *   <li>{@link #push(String)}：保存/手动触发单个脚本任务后推送</li>
 *   <li>{@link #pushAll()}：执行器注册（含重连）后全量推送，覆盖执行器重启丢缓存场景</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class GlueSyncService {

    /**
     * 同步服务端（调度中心角色装配）
     */
    private final SyncServer syncServer;

    /**
     * 任务仓库（读取脚本正文与版本）
     */
    private final JobRepository jobRepository;

    /**
     * 推送单个脚本任务的正文（bean 任务忽略）。
     *
     * @param jobKey 任务键
     * @return 是否发生推送
     */
    public boolean push(String jobKey) {
        if (jobKey == null || jobKey.isBlank()) {
            return false;
        }
        return pushRuntime(jobRepository.runtime(jobKey));
    }

    /**
     * 全量推送所有脚本任务正文（执行器注册/重连后调用）。
     *
     * @return 推送条数
     */
    public int pushAll() {
        int count = 0;
        for (JobRuntime runtime : jobRepository.listGroup(null)) {
            if (pushRuntime(runtime)) {
                count++;
            }
        }
        if (count > 0) {
            log.info("[job] job-glue 全量推送完成 count={}", count);
        }
        return count;
    }

    /**
     * 推送单个快照对应的脚本正文。
     *
     * @param runtime 运行态快照
     * @return 是否发生推送
     */
    private boolean pushRuntime(JobRuntime runtime) {
        if (runtime == null) {
            return false;
        }
        String glueType = runtime.getGlueType();
        if (glueType == null || glueType.isBlank() || "BEAN".equalsIgnoreCase(glueType.trim())
                || runtime.getGlueSource() == null || runtime.getGlueSource().isBlank()) {
            return false;
        }
        try {
            syncServer.publish(JobSyncTopics.GLUE, JobSyncTopics.command(
                    runtime.getJobKey(), false, null, null, null,
                    glueType, runtime.getGlueSource(), runtime.getGlueUpdatetimeMs()));
            log.info("[job] job-glue 已推送 job={} engine={} glueAt={} srcLen={}",
                    runtime.getJobKey(), glueType, runtime.getGlueUpdatetimeMs(),
                    runtime.getGlueSource().length());
            return true;
        } catch (Throwable ex) {
            log.warn("[job] job-glue 推送失败 job={} cause={}", runtime.getJobKey(), ex.getMessage());
            return false;
        }
    }
}
