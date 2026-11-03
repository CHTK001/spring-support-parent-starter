package com.chua.starter.server.support.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.handler.JobDispatchContext;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.operate.JobOperator;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.service.ReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 job-starter 的发布定时任务实现。
 *
 * <p>每个项目按需登记两类任务：</p>
 * <ul>
 *     <li>{@code release-publish-{projectId}}：一次性定时发布，按目标时间换算成 cron，
 *     执行一次后立即删除任务并回写状态；</li>
 *     <li>{@code release-git-poll-{projectId}}：Git 定时轮询，按项目配置的 cron 周期
 *     fetch 远端，有更新才触发构建发布。</li>
 * </ul>
 *
 * <p>任务处理器按项目动态注册到 {@link JobHandlerFactory}，任务键与处理器名保持一致；
 * 项目删除、关闭开关或应用启动全量同步时都会重新登记/撤销。</p>
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(JobOperator.class)
public class ReleaseScheduleServiceImpl implements ReleaseScheduleService {

    /**
     * 任务分组
     */
    private static final String JOB_GROUP = "release";

    /**
     * 一次性发布任务键前缀
     */
    private static final String PUBLISH_PREFIX = "release-publish-";

    /**
     * Git 轮询任务键前缀
     */
    private static final String GIT_POLL_PREFIX = "release-git-poll-";

    /**
     * Git 轮询默认 cron（每 5 分钟）
     */
    private static final String DEFAULT_GIT_CRON = "0 */5 * * * ?";

    private final JobOperator jobOperator;
    private final MsServerProjectMapper projectMapper;
    private final ReleaseService releaseService;

    /**
     * 应用就绪后把库中已启用的定时任务重新登记到调度器。
     */
    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void syncAll() {
        try {
            List<MsServerProject> projects = projectMapper.selectList(null);
            for (MsServerProject project : projects) {
                if (Boolean.TRUE.equals(project.getScheduleEnabled())
                        || Boolean.TRUE.equals(project.getGitPollEnabled())) {
                    syncProject(project.getId());
                }
            }
            cleanupOrphanJobs(projects);
        } catch (Exception e) {
            log.warn("[发布定时] 启动同步失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 撤销库里已不存在或已关闭开关的任务，避免重启后残留幽灵调度。
     * @param projects 参数
     * @return 返回值
    */
    private void cleanupOrphanJobs(List<MsServerProject> projects) {
        Map<Long, MsServerProject> byId = new LinkedHashMap<>();
        for (MsServerProject project : projects) {
            byId.put(project.getId(), project);
        }
        try {
            List<JobRuntime> jobs = jobOperator.list(JOB_GROUP);
            if (jobs == null) {
                return;
            }
            for (JobRuntime job : jobs) {
                String key = job.getJobKey();
                Long projectId = parseProjectId(key);
                if (projectId == null) {
                    continue;
                }
                MsServerProject project = byId.get(projectId);
                boolean publishAlive = project != null
                        && key.startsWith(PUBLISH_PREFIX)
                        && Boolean.TRUE.equals(project.getScheduleEnabled())
                        && "PENDING".equals(project.getScheduleStatus());
                boolean pollAlive = project != null
                        && key.startsWith(GIT_POLL_PREFIX)
                        && Boolean.TRUE.equals(project.getGitPollEnabled());
                if (!publishAlive && !pollAlive) {
                    jobOperator.remove(key);
                    JobHandlerFactory.getInstance().deregister(key);
                }
            }
        } catch (Exception e) {
            log.debug("[发布定时] 清理残留任务失败: {}", e.getMessage());
        }
    }

    @Override
    public void syncProject(Long projectId) {
        if (projectId == null) {
            return;
        }
        MsServerProject project = projectMapper.selectById(projectId);
        removeProject(projectId);
        if (project == null) {
            return;
        }
        if (Boolean.TRUE.equals(project.getScheduleEnabled())) {
            declarePublish(project);
        }
        if (Boolean.TRUE.equals(project.getGitPollEnabled())) {
            declareGitPoll(project);
        }
    }

    /**
     * 登记一次性定时发布任务。
     * @param project 参数
     * @return 返回值
    */
    private void declarePublish(MsServerProject project) {
        LocalDateTime time = project.getScheduleTime();
        if (time == null || !time.isAfter(LocalDateTime.now())) {
            log.warn("[发布定时] 项目 {} 的定时发布时间为空或已过期: {}", project.getId(), time);
            return;
        }
        if (!"PENDING".equals(project.getScheduleStatus())) {
            // 已执行过或已取消的任务不再登记，避免重启后重复发布
            return;
        }
        String jobKey = PUBLISH_PREFIX + project.getId();
        JobHandlerFactory.getInstance().register(new PublishJobHandler(jobKey, project.getId()));
        JobSpec spec = baseSpec(jobKey, "定时发布-" + project.getProjectName(), project.getId());
        spec.setScheduleType(JobSpec.TYPE_CRON);
        spec.setScheduleTime(toCron(time));
        spec.setDesc("项目「" + project.getProjectName() + "」于 " + time + " 执行一次发布");
        boolean ok = jobOperator.declare(spec);
        log.info("[发布定时] 项目 {} 登记一次性发布任务 {}，计划时间 {}，结果={}",
                project.getId(), jobKey, time, ok);
    }

    /**
     * 登记 Git 定时轮询任务。
     * @param project 参数
     * @return 返回值
    */
    private void declareGitPoll(MsServerProject project) {
        if (project.getGitUrl() == null || project.getGitUrl().trim().isEmpty()) {
            log.warn("[发布定时] 项目 {} 开启 Git 轮询但未配置仓库地址，跳过", project.getId());
            return;
        }
        String cron = project.getGitPollCron();
        if (cron == null || cron.trim().isEmpty()) {
            cron = DEFAULT_GIT_CRON;
        }
        String jobKey = GIT_POLL_PREFIX + project.getId();
        JobHandlerFactory.getInstance().register(new GitPollJobHandler(jobKey, project.getId()));
        JobSpec spec = baseSpec(jobKey, "Git轮询-" + project.getProjectName(), project.getId());
        spec.setScheduleType(JobSpec.TYPE_CRON);
        spec.setScheduleTime(cron.trim());
        spec.setDesc("项目「" + project.getProjectName() + "」定时拉取 Git，有更新自动发布");
        boolean ok = jobOperator.declare(spec);
        log.info("[发布定时] 项目 {} 登记 Git 轮询任务 {}，cron={}，结果={}",
                project.getId(), jobKey, cron, ok);
    }

    /**
     * 组装修剪公共字段的任务规格。
     * @param jobKey 字符串
     * @param jobName 字符串
     * @param projectId 长整数
     * @return 返回值
    */
    private JobSpec baseSpec(String jobKey, String jobName, Long projectId) {
        JobSpec spec = new JobSpec();
        spec.setJobNo(jobKey);
        spec.setJobKey(jobKey);
        spec.setJobName(jobName);
        spec.setGroup(JOB_GROUP);
        spec.setEnabled(true);
        spec.setExecuteParam(String.valueOf(projectId));
        spec.setDispatchMode(JobSpec.DISPATCH_LOCAL);
        spec.setGlueType(JobSpec.GLUE_BEAN);
        spec.setMisfireStrategy(JobSpec.MISFIRE_DO_NOTHING);
        spec.setConcurrentPolicy(JobSpec.CONCURRENT_SKIP);
        spec.setAuthor("release");
        return spec;
    }

    @Override
    public void removeProject(Long projectId) {
        if (projectId == null) {
            return;
        }
        String publishKey = PUBLISH_PREFIX + projectId;
        String gitKey = GIT_POLL_PREFIX + projectId;
        try {
            jobOperator.remove(publishKey);
        } catch (Exception ignored) {
            // 任务不存在时 remove 返回 false；异常仅记录不影响后续撤销
        }
        try {
            jobOperator.remove(gitKey);
        } catch (Exception ignored) {
            // 同上
        }
        JobHandlerFactory.getInstance().deregister(publishKey);
        JobHandlerFactory.getInstance().deregister(gitKey);
    }

    @Override
    public Map<String, Object> status(Long projectId) {
        Map<String, Object> out = new LinkedHashMap<>();
        Long id = projectId;
        if (id == null) {
            return out;
        }
        fillRuntime(out, PUBLISH_PREFIX + id, "publish");
        fillRuntime(out, GIT_POLL_PREFIX + id, "gitPoll");
        return out;
    }

    /**
     * fill Runtime
     * @param Map<String 参数
     * @param jobKey 字符串
     * @param out 参数
     * @param prefix 字符串
     * @return 返回值
    */
    private void fillRuntime(Map<String, Object> out, String jobKey, String prefix) {
        JobRuntime runtime = null;
        try {
            runtime = jobOperator.runtime(jobKey);
        } catch (Exception e) {
            log.debug("[发布定时] 读取任务 {} 运行态失败: {}", jobKey, e.getMessage());
        }
        out.put(prefix + "JobKey", jobKey);
        out.put(prefix + "Registered", runtime != null);
        if (runtime != null) {
            out.put(prefix + "NextFireMs", runtime.getNextFireMs());
            out.put(prefix + "LastFireMs", runtime.getLastFireMs());
            out.put(prefix + "Enabled", runtime.isEnabled());
        }
    }

    /**
     * LocalDateTime 换算为 6 段 cron（秒 分 时 日 月 周）。
     * @param time 参数
     * @return 返回值
    */
    private String toCron(LocalDateTime time) {
        return time.getSecond() + " " + time.getMinute() + " " + time.getHour() + " "
                + time.getDayOfMonth() + " " + time.getMonthValue() + " ?";
    }

    /**
     * parse Project Id
     * @param jobKey 字符串
     * @return 返回值
    */
    private Long parseProjectId(String jobKey) {
        if (jobKey == null) {
            return null;
        }
        try {
            if (jobKey.startsWith(PUBLISH_PREFIX)) {
                return Long.valueOf(jobKey.substring(PUBLISH_PREFIX.length()));
            }
            if (jobKey.startsWith(GIT_POLL_PREFIX)) {
                return Long.valueOf(jobKey.substring(GIT_POLL_PREFIX.length()));
            }
        } catch (NumberFormatException ignored) {
            // 非本模块命名规则的任务不处理
        }
        return null;
    }

    /**
     * 一次性发布处理器。
     */
    private final class PublishJobHandler implements JobHandler {

        private final String name;
        private final Long projectId;

        private PublishJobHandler(String name, Long projectId) {
            this.name = name;
            this.projectId = projectId;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String desc() {
            return "发布管理-定时发布";
        }

        @Override
        public void execute() {
            String param = JobDispatchContext.param();
            Long id = projectId;
            if (param != null && !param.trim().isEmpty()) {
                try {
                    id = Long.valueOf(param.trim());
                } catch (NumberFormatException ignored) {
                    // 参数异常时回落到注册时绑定的项目ID
                }
            }
            MsServerProject project = projectMapper.selectById(id);
            if (project == null) {
                jobOperator.remove(name);
                JobHandlerFactory.getInstance().deregister(name);
                return;
            }
            if (!"PENDING".equals(project.getScheduleStatus())) {
                // 已被取消/已执行：直接撤销任务
                jobOperator.remove(name);
                JobHandlerFactory.getInstance().deregister(name);
                return;
            }
            boolean success = false;
            String message = null;
            try {
                ReturnResult<String> result = releaseService.runUpgrade(id, new LinkedHashMap<>());
                success = result != null && result.isOk();
                message = result == null ? "无返回结果" : result.getMsg();
            } catch (Exception e) {
                message = e.getMessage();
                log.error("[发布定时] 项目 {} 定时发布异常: {}", id, e.getMessage(), e);
            } finally {
                MsServerProject update = new MsServerProject();
                update.setId(id);
                update.setScheduleEnabled(false);
                update.setScheduleStatus(success ? "DONE" : "FAILED");
                update.setUpdateTime(LocalDateTime.now());
                projectMapper.updateById(update);
                jobOperator.remove(name);
                JobHandlerFactory.getInstance().deregister(name);
            }
            if (!success) {
                log.warn("[发布定时] 项目 {} 定时发布失败: {}", id, message);
            } else {
                log.info("[发布定时] 项目 {} 定时发布完成", id);
            }
        }
    }

    /**
     * Git 轮询处理器。
     */
    private final class GitPollJobHandler implements JobHandler {

        private final String name;
        private final Long projectId;

        private GitPollJobHandler(String name, Long projectId) {
            this.name = name;
            this.projectId = projectId;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String desc() {
            return "发布管理-Git定时拉取";
        }

        @Override
        public void execute() {
            String param = JobDispatchContext.param();
            Long id = projectId;
            if (param != null && !param.trim().isEmpty()) {
                try {
                    id = Long.valueOf(param.trim());
                } catch (NumberFormatException ignored) {
                    // 参数异常时回落到注册时绑定的项目ID
                }
            }
            MsServerProject project = projectMapper.selectById(id);
            if (project == null || !Boolean.TRUE.equals(project.getGitPollEnabled())) {
                jobOperator.remove(name);
                JobHandlerFactory.getInstance().deregister(name);
                return;
            }
            try {
                ReturnResult<Map<String, Object>> result = releaseService.gitPollDeploy(id);
                if (result == null || !result.isOk()) {
                    log.warn("[Git轮询] 项目 {} 检查失败: {}", id, result == null ? "无返回" : result.getMsg());
                    return;
                }
                Map<String, Object> data = result.getData();
                if (data != null && Boolean.TRUE.equals(data.get("changed"))) {
                    log.info("[Git轮询] 项目 {} 检测到更新，已触发发布，taskId={}", id, data.get("taskId"));
                }
            } catch (Exception e) {
                log.error("[Git轮询] 项目 {} 执行异常: {}", id, e.getMessage(), e);
            }
        }
    }
}
