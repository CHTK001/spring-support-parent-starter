package com.chua.starter.job.test;

import com.chua.starter.job.support.JobAutoConfiguration;
import com.chua.starter.job.support.execute.JobExecutor;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.sync.JobSyncTopics;
import com.chua.starter.job.test.stub.InMemorySyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 *   <li>从 SyncClient stub 推送一条 TASK_COMMAND 消息;</li>
 *   <li>验证 handler 被执行, invokeCount &gt; 0;</li>
 *   <li>验证 TASK_LOG 消息被 SyncClient send 上行。</li>
 * </ol>
 *
 * @author CH
 */
@SpringBootTest(
        classes = {ExecutorModeTest.TestApp.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "plugin.job.role=EXECUTOR",
                "plugin.job.cluster.group=test-executor"
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
            DemoJob.class,
            TestStubs.class
    })
    @org.springframework.boot.autoconfigure.SpringBootApplication
    static class TestApp {
    }

    /**
     * 测试桩配置: 提供 {@link com.chua.common.support.network.sync.SyncClient} stub。
     */
    @TestConfiguration
    static class TestStubs {

        /**
         * 提供 in-memory SyncClient 替代 SPI bean。
         *
         * @return stub
         */
        @Bean
        @Primary
        public InMemorySyncClient jobStubSyncClient() {
            return new InMemorySyncClient();
        }
    }

    @Autowired
    private JobExecutor jobExecutor;

    @Autowired
    private JobHandlerFactory jobHandlerFactory;

    @Autowired
    private InMemorySyncClient stubSyncClient;

    @Autowired
    private DemoJob demoJob;

    @Test
    void executorModeShouldHandleIncomingCmd() throws InterruptedException {
        // Handler 注册
        assertNotNull(jobHandlerFactory.get("demo"), "@Job handler 必须注册");

        // Executor 启动后会自动订阅 TASK_COMMAND (由 ExecutorSyncBinder 完成)
        Thread.sleep(500L);

        // 通过 stub 模拟 Scheduler 推送 cmd
        int before = demoJob.getInvokeCount();
        stubSyncClient.deliver(JobSyncTopics.TASK_COMMAND, "demo");
        Thread.sleep(500L);
        int after = demoJob.getInvokeCount();

        assertTrue(after > before,
                "EXECUTOR 模式下收到 cmd 后 handler 应被调用, 实际 before=" + before + " after=" + after);

        // 验证 TASK_LOG 已被上行 send (由 ExecutorSyncBinder 完成)
        Thread.sleep(300L);
        assertTrue(stubSyncClient.getSent(JobSyncTopics.TASK_LOG).size() >= 1,
                "EXECUTOR 模式下 TASK_LOG 应当被 send 上行");
    }
}
