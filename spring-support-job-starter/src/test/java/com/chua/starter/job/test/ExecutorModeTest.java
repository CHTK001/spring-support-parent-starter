package com.chua.starter.job.test;

import com.chua.starter.job.support.JobAutoConfiguration;
import com.chua.starter.job.support.execute.JobExecutor;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.sync.InMemorySyncClient;
import com.chua.starter.job.support.sync.JobSyncTopics;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXECUTOR 模式集成测试。
 *
 * <p>流程:</p>
 * <ol>
 *   <li>Spring 上下文启动并装配 {@link JobAutoConfiguration} (role=EXECUTOR);</li>
 *   <li>{@link JobExecutor} 启动并订阅 TASK_COMMAND 主题;</li>
 *   <li>{@code @Job} 注册到 {@link JobHandlerFactory};</li>
 *   <li>从 SyncClient 推送一条 TASK_COMMAND 消息;</li>
 *   <li>验证 handler 被执行, invokeCount &gt; 0;</li>
 *   <li>验证 TASK_LOG 消息被 SyncClient send 上行。</li>
 * </ol>
 *
 * <p>SyncClient / SyncServer 在 SPI 找不到时 fallback 到主包内 {@link InMemorySyncClient}。</p>
 *
 * @author CH
 */
@SpringBootTest(
        classes = {ExecutorModeTest.TestApp.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "plugin.job.role=EXECUTOR",
                "plugin.job.sync.in-memory=true",
        }
)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ExecutorModeTest {

    /**
     * 测试用 Spring Boot Application。
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
    private JobExecutor jobExecutor;

    @Autowired
    private JobHandlerFactory jobHandlerFactory;

    @Autowired
    private InMemorySyncClient syncClient;

    @Autowired
    private DemoJob demoJob;

    @Test
    void executorModeShouldHandleIncomingCmd() throws InterruptedException {
        // Handler 注册
        assertNotNull(jobHandlerFactory.get("demo"), "@Job handler 必须注册");

        // Executor 启动后会自动订阅 TASK_COMMAND (由 ExecutorSyncBinder 完成)
        Thread.sleep(500L);

        // 通过 SyncClient 模拟 Scheduler 推送 cmd
        int before = demoJob.getInvokeCount();
        syncClient.deliver(JobSyncTopics.TASK_COMMAND, "demo");
        Thread.sleep(500L);
        int after = demoJob.getInvokeCount();

        assertTrue(after > before,
                "EXECUTOR 模式下收到 cmd 后 handler 应被调用, 实际 before=" + before + " after=" + after);

        // 验证 TASK_LOG 已被上行 send (由 ExecutorSyncBinder 完成)
        Thread.sleep(300L);
        assertTrue(syncClient.getSent(JobSyncTopics.TASK_LOG).size() >= 1,
                "EXECUTOR 模式下 TASK_LOG 应当被 send 上行");
    }
}
