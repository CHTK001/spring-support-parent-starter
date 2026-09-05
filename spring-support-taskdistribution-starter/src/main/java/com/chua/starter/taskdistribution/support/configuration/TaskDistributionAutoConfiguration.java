package com.chua.starter.taskdistribution.support.configuration;

import com.chua.common.support.taskdistribution.dispatcher.DispatcherProvider;
import com.chua.common.support.taskdistribution.dispatcher.InMemoryDispatcherProvider;
import com.chua.common.support.taskdistribution.manager.ResultBuffer;
import com.chua.common.support.taskdistribution.manager.TaskManager;
import com.chua.common.support.taskdistribution.node.NodeTable;
import com.chua.common.support.taskdistribution.spi.TaskExecutor;
import com.chua.common.support.taskdistribution.spi.TaskExecutorRegistry;
import com.chua.common.support.taskdistribution.store.FileTaskStore;
import com.chua.common.support.taskdistribution.store.InMemoryTaskStore;
import com.chua.common.support.taskdistribution.store.TaskStore;
import com.chua.common.support.taskdistribution.task.TaskIdGenerator;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.taskdistribution.support.mapper.TaskStoreMapper;
import com.chua.starter.taskdistribution.support.properties.TaskDistributionProperties;
import com.chua.starter.taskdistribution.support.store.DbTaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 任务分发自动配置。
 *
 * <p>装配 NodeTable、TaskManager、ResultBuffer、DispatcherProvider 等核心 Bean，
 * 支持自动恢复、批量处理和 SPI 存储扩展。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(TaskDistributionProperties.class)
@ConditionalOnProperty(prefix = "taskdistribution", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaskDistributionAutoConfiguration {

    /**
     * 持久化存储 Bean（按 storeType 选择 memory / file / db）。
     *
     * @param properties 配置属性
     * @param mapper     数据库 Mapper（storeType = db 时自动注入）
     * @return TaskStore 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskStore taskStore(TaskDistributionProperties properties,
                               @Autowired(required = false) TaskStoreMapper mapper) {
        String storeType = properties.getStoreType();
        if ("file".equalsIgnoreCase(storeType)) {
            log.info("任务分发使用文件存储: {}", properties.getStorePath());
            return new FileTaskStore(properties.getStorePath());
        }
        if ("db".equalsIgnoreCase(storeType)) {
            if (mapper != null) {
                log.info("任务分发使用数据库存储");
                return new DbTaskStore(mapper);
            }
            log.warn("storeType=db 但未引入 mybatis 依赖或未配置数据源，回退到内存存储");
        }
        return new InMemoryTaskStore();
    }

    /**
     * 节点注册表 Bean。
     *
     * @return NodeTable 实例
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public NodeTable nodeTable() {
        return new NodeTable();
    }

    /**
     * 任务管理器 Bean（启动时自动恢复未完成任务）。
     *
     * @param store 持久化存储（可选，无则纯内存）
     * @return TaskManager 实例
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public TaskManager taskManager(TaskStore store) {
        TaskManager manager = new TaskManager();
        if (store != null) {
            manager.setStore(store);
            List<?> recovered = manager.recover();
            if (!recovered.isEmpty()) {
                log.info("任务管理器已恢复 {} 个未完成任务", recovered.size());
            }
        }
        return manager;
    }

    /**
     * 结果缓冲器 Bean。
     *
     * @return ResultBuffer 实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ResultBuffer resultBuffer() {
        return new ResultBuffer();
    }

    /**
     * 执行器注册中心 Bean。
     *
     * @return TaskExecutorRegistry 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskExecutorRegistry taskExecutorRegistry() {
        return new TaskExecutorRegistry();
    }

    /**
     * 派发提供者 Bean（默认内存实现，支持批量处理）。
     *
     * @param properties 配置属性
     * @return DispatcherProvider 实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public DispatcherProvider dispatcherProvider(TaskDistributionProperties properties) {
        int batchSize = properties.getBatchSize();
        int consumerThreads = properties.getConsumerThreads();
        InMemoryDispatcherProvider provider = new InMemoryDispatcherProvider(
                InMemoryDispatcherProvider.DEFAULT_CAPACITY, batchSize, consumerThreads);
        provider.start();
        return provider;
    }

    /**
     * 初始化节点 ID 和自动注册 TaskExecutor。
     *
     * @param properties 配置属性
     * @param registry   执行器注册中心
     * @param nodeTable  节点注册表
     * @param executors  用户自定义 TaskExecutor 列表
     */
    @Bean
    public ApplicationRunner taskDistributionInitializer(
            TaskDistributionProperties properties,
            TaskExecutorRegistry registry,
            NodeTable nodeTable,
            @Autowired(required = false)
            List<TaskExecutor<?>> executors) {
        return args -> {
            String nodeId = properties.getNodeId();
            if (nodeId == null || nodeId.isEmpty()) {
                nodeId = "node-" + System.currentTimeMillis();
                properties.setNodeId(nodeId);
            }
            TaskIdGenerator.setDefaultNodeId(nodeId);
            log.info("任务分发初始化完成, nodeId: {}, strategy: {}, batchSize: {}, consumerThreads: {}",
                    nodeId, properties.getStrategy(), properties.getBatchSize(), properties.getConsumerThreads());

            if (executors != null) {
                for (TaskExecutor<?> executor : executors) {
                    registry.register(executor);
                    log.debug("注册任务执行器: {}", executor.taskType());
                }
            }
        };
    }

    /**
     * 任务分发的 ServiceInfo 贡献者。
     *
     * @param properties 配置属性
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor taskDistributionServiceInfoContributor(TaskDistributionProperties properties) {
        return registry -> registry.registerFlag("taskdistribution", "任务分发", true,
                "任务分发模块",
                "[存储: " + properties.getStoreType() + " 策略: " + properties.getStrategy() + "]");
    }
}