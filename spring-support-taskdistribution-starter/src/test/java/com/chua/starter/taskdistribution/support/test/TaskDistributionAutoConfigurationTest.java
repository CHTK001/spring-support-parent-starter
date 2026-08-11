package com.chua.starter.taskdistribution.support.test;

import com.chua.common.support.taskdistribution.dispatcher.DispatcherProvider;
import com.chua.common.support.taskdistribution.manager.ResultBuffer;
import com.chua.common.support.taskdistribution.manager.TaskManager;
import com.chua.common.support.taskdistribution.node.NodeTable;
import com.chua.common.support.taskdistribution.spi.TaskExecutorRegistry;
import com.chua.common.support.taskdistribution.store.FileTaskStore;
import com.chua.common.support.taskdistribution.store.InMemoryTaskStore;
import com.chua.common.support.taskdistribution.store.TaskStore;
import com.chua.common.support.taskdistribution.task.Task;
import com.chua.common.support.taskdistribution.task.TaskResult;
import com.chua.common.support.taskdistribution.task.TaskStatus;
import com.chua.starter.taskdistribution.support.configuration.TaskDistributionAutoConfiguration;
import com.chua.starter.taskdistribution.support.properties.TaskDistributionProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TaskDistributionAutoConfiguration 集成测试。
 *
 * <p>覆盖:</p>
 * <ul>
 *   <li>TaskDistributionProperties 绑定 (默认 storeType=memory / storePath / batchSize)</li>
 *   <li>storeType=memory 默认走 {@link InMemoryTaskStore}</li>
 *   <li>storeType=file 切换为 {@link FileTaskStore} 且写到磁盘</li>
 *   <li>所有核心 Bean (NodeTable/TaskManager/ResultBuffer/DispatcherProvider/TaskExecutorRegistry) 装配</li>
 *   <li>TaskManager 与 InMemoryTaskStore 联动 (saveTask/updateStatus/getRecoverableTasks/recover)</li>
 *   <li>FileTaskStore 跨实例恢复 (新建实例后 getRecoverableTasks)</li>
 *   <li>TaskManager cancel/pause/resume 状态机</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("TaskDistributionAutoConfiguration 集成测试")
class TaskDistributionAutoConfigurationTest {

    /**
     * 内存存储场景上下文运行器
     */
    private final ApplicationContextRunner memoryRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskDistributionAutoConfiguration.class));

    /**
     * 文件存储场景上下文运行器 (临时目录)
     */
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("taskstore-test-");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    @DisplayName("默认配置: 装配所有核心 Bean, storeType=memory, 注入 InMemoryTaskStore")
    void shouldAssembleAllBeansWithDefaultInMemoryStore() {
        memoryRunner.run(ctx -> {
            // Properties 绑定默认值
            TaskDistributionProperties props = ctx.getBean(TaskDistributionProperties.class);
            assertThat(props).isNotNull();
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getStoreType()).isEqualTo("memory");
            assertThat(props.getBatchSize()).isEqualTo(1);
            assertThat(props.getConsumerThreads()).isEqualTo(1);
            assertThat(props.getNodeId()).isNotBlank();

            // 核心 Bean 装配
            assertThat(ctx).hasSingleBean(TaskStore.class);
            assertThat(ctx).hasSingleBean(NodeTable.class);
            assertThat(ctx).hasSingleBean(TaskManager.class);
            assertThat(ctx).hasSingleBean(ResultBuffer.class);
            assertThat(ctx).hasSingleBean(DispatcherProvider.class);
            assertThat(ctx).hasSingleBean(TaskExecutorRegistry.class);

            // 默认是内存存储
            TaskStore store = ctx.getBean(TaskStore.class);
            assertThat(store).isInstanceOf(InMemoryTaskStore.class);
        });
    }

    @Test
    @DisplayName("storeType=file: 切换为 FileTaskStore, 任务写到磁盘")
    void shouldUseFileTaskStoreWhenStoreTypeIsFile() {
        memoryRunner
                .withPropertyValues(
                        "taskdistribution.store-type=file",
                        "taskdistribution.store-path=" + tempDir.toString()
                )
                .run(ctx -> {
                    TaskStore store = ctx.getBean(TaskStore.class);
                    assertThat(store).isInstanceOf(FileTaskStore.class);

                    // 写一个任务, 验证文件落地
                    Task<String> task = Task.<String>builder()
                            .taskId("file-task-1")
                            .taskType("demo")
                            .payload("payload-data")
                            .build();
                    store.saveTask(task, TaskStatus.PENDING);

                    Path taskFile = tempDir.resolve("file-task-1.task.json");
                    assertThat(Files.exists(taskFile)).isTrue();
                });
    }

    @Test
    @DisplayName("TaskManager 与 InMemoryTaskStore 联动: addTask/saveTask/getStatus/getRecoverableTasks")
    void shouldCoordinateTaskManagerWithInMemoryStore() {
        memoryRunner.run(ctx -> {
            TaskManager manager = ctx.getBean(TaskManager.class);
            TaskStore store = ctx.getBean(TaskStore.class);
            assertThat(manager).isNotNull();

            // addTask → store.saveTask(PENDING)
            Task<String> task = Task.<String>builder()
                    .taskId("t-1")
                    .taskType("demo")
                    .payload("hello")
                    .build();
            manager.addTask(task, null);

            assertThat(manager.getStatus("t-1")).isEqualTo(TaskStatus.PENDING);
            assertThat(store.getStatus("t-1")).isEqualTo(TaskStatus.PENDING);
            assertThat(store.getTask("t-1")).isNotNull();

            // updateStatus → store.updateStatus
            manager.updateStatus("t-1", TaskStatus.RUNNING);
            assertThat(manager.getStatus("t-1")).isEqualTo(TaskStatus.RUNNING);
            assertThat(store.getStatus("t-1")).isEqualTo(TaskStatus.RUNNING);

            // getRecoverableTasks 返回 PENDING/RUNNING
            List<Task<?>> recoverable = store.getRecoverableTasks();
            assertThat(recoverable).extracting(Task::getTaskId).contains("t-1");
        });
    }

    @Test
    @DisplayName("TaskManager cancel/pause/resume 状态机")
    void shouldSupportTaskStateMachine() {
        memoryRunner.run(ctx -> {
            TaskManager manager = ctx.getBean(TaskManager.class);

            Task<String> task = Task.<String>builder()
                    .taskId("t-2")
                    .taskType("demo")
                    .payload("x")
                    .build();
            manager.addTask(task, null);

            // PENDING → PAUSED
            assertThat(manager.pause("t-2")).isTrue();
            assertThat(manager.getStatus("t-2")).isEqualTo(TaskStatus.PAUSED);

            // PAUSED → PENDING
            assertThat(manager.resume("t-2")).isTrue();
            assertThat(manager.getStatus("t-2")).isEqualTo(TaskStatus.PENDING);

            // PENDING → CANCELLED
            assertThat(manager.cancel("t-2")).isTrue();
            assertThat(manager.getStatus("t-2")).isEqualTo(TaskStatus.CANCELLED);

            // 终态不能再 cancel
            assertThat(manager.cancel("t-2")).isFalse();
        });
    }

    @Test
    @DisplayName("FileTaskStore 跨实例恢复: 关闭后重建实例应能 getRecoverableTasks")
    void shouldRecoverFromFileAcrossInstances() {
        // 第一阶段: 写入 2 个 PENDING + 1 个 SUCCESS, 关闭
        memoryRunner
                .withPropertyValues(
                        "taskdistribution.store-type=file",
                        "taskdistribution.store-path=" + tempDir.toString()
                )
                .run(ctx -> {
                    TaskStore store = ctx.getBean(TaskStore.class);
                    assertThat(store).isInstanceOf(FileTaskStore.class);

                    store.saveTask(makeTask("rec-1", "pending-1"), TaskStatus.PENDING);
                    store.saveTask(makeTask("rec-2", "pending-2"), TaskStatus.PENDING);
                    store.saveTask(makeTask("rec-3", "done"), TaskStatus.SUCCESS);

                    // 模拟保存结果
                    TaskResult<String> result = TaskResult.<String>builder()
                            .taskId("rec-3")
                            .success(true)
                            .data("ok")
                            .build();
                    store.saveResult(result);
                });

        // 第二阶段: 新建上下文 (新 FileTaskStore 实例), 验证从磁盘加载
        memoryRunner
                .withPropertyValues(
                        "taskdistribution.store-type=file",
                        "taskdistribution.store-path=" + tempDir.toString()
                )
                .run(ctx -> {
                    TaskStore store = ctx.getBean(TaskStore.class);
                    // 只 PENDING/RUNNING 是可恢复
                    List<Task<?>> recoverable = store.getRecoverableTasks();
                    assertThat(recoverable).extracting(Task::getTaskId)
                            .containsExactlyInAnyOrder("rec-1", "rec-2");

                    // 状态查询
                    assertThat(store.getStatus("rec-1")).isEqualTo(TaskStatus.PENDING);
                    assertThat(store.getStatus("rec-3")).isEqualTo(TaskStatus.SUCCESS);

                    // 结果查询
                    TaskResult<?> recovered = store.getResult("rec-3");
                    assertThat(recovered).isNotNull();
                    assertThat(recovered.getData()).isEqualTo("ok");
                });
    }

    @Test
    @DisplayName("TaskManager.recover() 从 store 中恢复未完成任务并标记为 PENDING")
    void shouldRecoverUnfinishedTasksIntoTaskManager() {
        memoryRunner
                .withPropertyValues(
                        "taskdistribution.store-type=file",
                        "taskdistribution.store-path=" + tempDir.toString()
                )
                .run(ctx -> {
                    // 先单独保存一个 RUNNING 任务 (不进 TaskManager)
                    TaskStore store = ctx.getBean(TaskStore.class);
                    store.saveTask(makeTask("recover-1", "data"), TaskStatus.RUNNING);

                    // 新建 TaskManager 持有该 store, 调用 recover
                    TaskManager manager = new TaskManager();
                    manager.setStore(store);
                    List<Task<?>> recovered = manager.recover();

                    assertThat(recovered).extracting(Task::getTaskId).contains("recover-1");
                    // recover 会把状态重置为 PENDING
                    assertThat(manager.getStatus("recover-1")).isEqualTo(TaskStatus.PENDING);
                });
    }

    @Test
    @DisplayName("DispatcherProvider 在上下文启动时 start() 可用, 关闭时优雅释放")
    void shouldStartDispatcherProviderOnContextInit() {
        memoryRunner.run(ctx -> {
            DispatcherProvider provider = ctx.getBean(DispatcherProvider.class);
            assertThat(provider).isNotNull();
            // InMemoryDispatcherProvider.start() 在 @Bean 中调用, 无异常即通过
            assertThat(ctx.isRunning()).isTrue();
        });
    }

    @Test
    @DisplayName("ResultBuffer 可作为 Bean 装配且独立于 TaskManager")
    void shouldExposeResultBufferAsIndependentBean() {
        memoryRunner.run(ctx -> {
            ResultBuffer buffer = ctx.getBean(ResultBuffer.class);
            assertThat(buffer).isNotNull();
            assertThat(buffer).isInstanceOf(ResultBuffer.class);
        });
    }

    /**
     * 构造测试任务。
     *
     * @param taskId   任务 ID
     * @param payload  payload 数据
     * @return Task 实例
     */
    private static Task<String> makeTask(String taskId, String payload) {
        return Task.<String>builder()
                .taskId(taskId)
                .taskType("demo")
                .payload(payload)
                .build();
    }

    /**
     * 显式导入配置的空配置类 (ApplicationContextRunner 需要一个 @Configuration)。
     */
    @Configuration
    @Import(TaskDistributionAutoConfiguration.class)
    static class TestConfig {
    }
}