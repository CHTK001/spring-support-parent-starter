package com.chua.starter.job.test;

import com.chua.starter.job.support.JobAutoConfiguration;
import com.chua.starter.job.support.cluster.LeaderElector;
import com.chua.starter.job.support.execute.JobExecutor;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.loop.JobLeaderLoop;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SINGLE 模式集成测试。
 *
 * <p>流程:</p>
 * <ol>
 *   <li>Spring 上下文启动并装配 {@link JobAutoConfiguration} (role=SINGLE);</li>
 *   <li>Leader 抢占成功, {@link JobLeaderLoop} 启动守护线程;</li>
 *   <li>{@code @Job} 注册到 {@link JobHandlerFactory};</li>
 *   <li>sleep 几秒后, {@link JobExecutor} 至少执行 1 次。</li>
 * </ol>
 *
 * @author CH
 */
@SpringBootTest(
        classes = {SingleModeTest.TestApp.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "plugin.job.role=SINGLE",
                "plugin.job.cluster.group=test-single"
        }
)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SingleModeTest {

    /**
     * 测试用 Spring Boot Application。
     * <p>明确指定 {@code @Import} 而不是标准 {@code @SpringBootApplication},
     * 因为我们不希望扫描我们 module 之外的 Component。</p>
     */
    @Import({
            JobAutoConfiguration.class,
            DemoJob.class
    })
    @MapperScan("com.chua.starter.job.support.store")
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
    private JobExecutor jobExecutor;

    @Autowired
    private DemoJob demoJob;

    @Test
    void singleModeShouldTriggerAtLeastOnce() throws InterruptedException {
        // Leader 抢占
        assertNotNull(leaderElector);
        assertTrue(leaderElector.isLeader(), "SINGLE 模式下应当抢占 Leader 成功");

        // LeaderLoop 启动
        assertNotNull(jobLeaderLoop);

        // Handler 注册
        assertNotNull(jobHandlerFactory.get("demo"), "@Job handler 必须注册");

        // 给 3 秒让单秒循环跑几次
        int before = demoJob.getInvokeCount();
        Thread.sleep(3500L);
        int after = demoJob.getInvokeCount();

        // 至少触发 1 次
        assertTrue(after > before,
                "期望 3s 内至少触发 1 次, 实际 before=" + before + " after=" + after);
    }
}
