package com.chua.starter.job.test;

import com.chua.starter.job.support.JobAutoConfiguration;
import com.chua.starter.job.support.cluster.LeaderElector;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.loop.JobLeaderLoop;
import com.chua.starter.job.support.sync.InMemorySyncServer;
import com.chua.starter.job.support.sync.JobSyncTopics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCHEDULER 模式集成测试。
 *
 * <p>流程:</p>
 * <ol>
 *   <li>Spring 上下文启动并装配 {@link JobAutoConfiguration} (role=SCHEDULER);</li>
 *   <li>Leader 抢占成功 (CAS 或 MyBatis, 测试用 in-memory);</li>
 *   <li>{@link JobLeaderLoop} 启动守护线程,单秒扫描任务仓库;</li>
 *   <li>{@code @Job} 注册到 {@link JobHandlerFactory};</li>
 *   <li>sleep 几秒后,任务被发送到 SyncServer (实际是 InMemorySyncServer fallback)。</li>
 * </ol>
 *
 * @author CH
 */
@SpringBootTest(
        classes = {SchedulerModeTest.TestApp.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "plugin.job.role=SCHEDULER",
                "plugin.job.cluster.group=test-scheduler",
                "plugin.job.sync.scheduler.listen-port=18081"
        }
)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulerModeTest {

    /**
     * 测试用 Spring Boot Application。
     */
    @Import({
            JobAutoConfiguration.class,
            DemoJob.class
    })
    @org.springframework.boot.autoconfigure.SpringBootApplication
    static class TestApp {
    }

    @Autowired
    private LeaderElector leaderElector;

    @Autowired
    private JobLeaderLoop jobLeaderLoop;

    @Autowired
    private JobHandlerFactory jobHandlerFactory;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private InMemorySyncServer syncServer;

    @Autowired
    private DemoJob demoJob;

    @Test
    void schedulerModeShouldPublishAtLeastOneCmd() throws InterruptedException {
        // Leader 抢占
        assertNotNull(leaderElector);
        assertTrue(leaderElector.isLeader(), "SCHEDULER 模式下应当抢占 Leader 成功");

        // LeaderLoop 启动
        assertNotNull(jobLeaderLoop);

        // Handler 注册
        assertNotNull(jobHandlerFactory.get("demo"), "@Job handler 必须注册");

        // 给 3 秒让单秒循环跑几次
        Thread.sleep(3500L);

        // SyncServer 应当收到至少 1 条 TASK_COMMAND 消息
        assertTrue(syncServer.getPublished(JobSyncTopics.TASK_COMMAND).size() >= 1,
                "SCHEDULER 模式下 LeaderLoop 应当把任务推到 SyncServer");
    }
}
