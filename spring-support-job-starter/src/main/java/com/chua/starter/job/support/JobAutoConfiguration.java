package com.chua.starter.job.support;

import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.job.support.cluster.LeaderElector;
import com.chua.starter.job.support.dispatch.InMemoryJobRepository;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.execute.JobExecutor;
import com.chua.starter.job.support.handler.JobAnnotationScanner;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.JobLogSink;
import com.chua.starter.job.support.log.StdoutJobLogSink;
import com.chua.starter.job.support.loop.JobLeaderLoop;
import com.chua.starter.job.support.schedule.ScheduledAnnotationScanner;
import com.chua.starter.job.support.sync.ExecutorSyncBinder;
import com.chua.starter.job.support.sync.InMemorySyncClient;
import com.chua.starter.job.support.sync.InMemorySyncServer;
import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.SchedulerSyncBinder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 自研调度模块自动配置入口。
 *
 * <h3>三模式装配</h3>
 * <table border="1">
 *   <tr><th>Role</th><th>LeaderLoop</th><th>Executor</th><th>SyncBinder</th></tr>
 *   <tr><td>SCHEDULER</td><td>装配</td><td>不装配</td><td>SchedulerSyncBinder</td></tr>
 *   <tr><td>EXECUTOR</td><td>不装配</td><td>装配</td><td>ExecutorSyncBinder</td></tr>
 *   <tr><td>SINGLE</td><td>装配</td><td>装配</td><td>不装配</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = JobProperties.PRE, name = "enable", havingValue = "true")
@RequiredArgsConstructor
public class JobAutoConfiguration {

    /**
     * Mapper 接口所在包常量。
     * <p>业务方主启动类上需添加 {@code @MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)}
     * 让 MyBatis-Plus 接管本模块的 Mapper 接口。</p>
     */
    public static final String MAPPER_PACKAGE = "com.chua.starter.job.support.store";

    /**
     * 配置属性
     */
    private final JobProperties properties;

    /**
     * 当前节点 ID Bean, 缺省时按 hostName-pid 推导。
     */
    @Bean
    @ConditionalOnMissingBean(name = "jobNodeId")
    public String jobNodeId() {
        String configured = properties.getNodeId();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return runtimeName + "-" + Integer.toHexString(UUID.randomUUID().hashCode());
    }

    /**
     * Spring 上下文任务调度器 (供 Spring 原生 @Scheduled 使用)。
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskScheduler jobTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getPool().getCoreSize());
        scheduler.setThreadNamePrefix("job-spring-scheduler-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /**
     * @Scheduled 注解扫描器。
     */
    @Bean
    @ConditionalOnMissingBean
    public ScheduledAnnotationScanner jobScheduledAnnotationScanner(TaskScheduler jobTaskScheduler) {
        return new ScheduledAnnotationScanner(jobTaskScheduler);
    }

    /**
     * 处理器注册表。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobHandlerFactory jobHandlerFactory() {
        return JobHandlerFactory.getInstance();
    }

    /**
     * @Job 注解扫描器。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobAnnotationScanner jobAnnotationScanner() {
        return new JobAnnotationScanner();
    }

    /**
     * 进程内事件总线。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobSyncBus jobSyncBus() {
        return new JobSyncBus();
    }

    /**
     * 任务仓库默认实现 (InMemory 兜底), MyBatis-Plus 存在时被 MyBatis 版覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobRepository inMemoryJobRepository() {
        return new InMemoryJobRepository();
    }

    /**
     * Leader 选举器 (CAS 占位)。
     */
    @Bean
    @ConditionalOnMissingBean
    public LeaderElector jobLeaderElector(String jobNodeId) {
        return new LeaderElector(jobNodeId);
    }

    /**
     * 日志落库默认实现 (stdout 兜底), MyBatis-Plus 存在时被 MyBatis 版覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobLogSink stdoutJobLogSink() {
        return new StdoutJobLogSink();
    }

    /**
     * MyBatis-Plus 任务仓库 (依赖 MyBatis-Plus 时启用, 覆盖默认 InMemory)。
     *
     * <p>启用条件: classpath 含 {@code com.baomidou.mybatisplus.core.mapper.BaseMapper};
     * 业务侧无需自行声明 JobRepository。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    @org.springframework.context.annotation.Primary
    public JobRepository mybatisJobRepository(com.chua.starter.job.support.store.JobMapper jobMapper) {
        return new com.chua.starter.job.support.store.MybatisJobRepository(jobMapper);
    }

    /**
     * MyBatis-Plus 任务日志落库 (覆盖默认 stdout)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    @org.springframework.context.annotation.Primary
    public JobLogSink mybatisJobLogSink(com.chua.starter.job.support.store.JobLogMapper jobLogMapper) {
        return new com.chua.starter.job.support.store.MybatisJobLogSink(jobLogMapper);
    }

    /**
     * Leader 选举解析器默认实现: 本地 CAS 占位, 适用于单机或无 MyBatis 场景。
     */
    @Bean
    @ConditionalOnMissingBean
    public com.chua.starter.job.support.cluster.LeaderElectionResolver inMemoryLeaderElectionResolver() {
        return new com.chua.starter.job.support.cluster.InMemoryLeaderElectionResolver();
    }

    /**
     * MyBatis-Plus 行锁版 Leader 选举 (依赖 MyBatis-Plus 时覆盖默认)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    @org.springframework.context.annotation.Primary
    public com.chua.starter.job.support.cluster.LeaderElectionResolver mysqlLeaderElectionResolver(
            com.chua.starter.job.support.store.ClusterMapper clusterMapper) {
        return new com.chua.starter.job.support.store.MysqlLeaderElectionResolver(clusterMapper);
    }

    // ====================================================================
    //  SCHEDULER 角色: 装配 LeaderLoop + SchedulerSyncBinder
    // ====================================================================

    /**
     * Scheduler 端: 注册到仓库公共 SyncServer。
     * <p>SPI 未发现实现时 (精简部署或单元测试),自动 fallback 到 {@link InMemorySyncServer}。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER", matchIfMissing = true)
    public SyncServer jobSyncServer() {
        SyncServer server = ServiceProvider.of(SyncServer.class).getExtension();
        if (server == null) {
            log.warn("[job] 未找到 SyncServer SPI 实现, fallback 到 InMemorySyncServer (精简部署或测试环境)");
            return new InMemorySyncServer();
        }
        return server;
    }

    /**
     * SCHEDULER 角色: bind 同步, 把 bus 的 cmd 推到远端, 并落库 task-log。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER", matchIfMissing = true)
    public SchedulerSyncBinder jobSchedulerSyncBinder(SyncServer jobSyncServer,
                                                     JobSyncBus jobSyncBus,
                                                     JobLogSink jobLogSink,
                                                     String jobNodeId) {
        return new SchedulerSyncBinder(jobSyncServer, jobSyncBus, jobLogSink, jobNodeId);
    }

    /**
     * SCHEDULER 角色: 通过 SPI 抢占 Leader + 启动续约心跳 + 单秒循环。
     *
     * <p>三次装配顺序:</p>
     * <ol>
     *   <li>{@link com.chua.starter.job.support.cluster.LeaderElectionResolver} 抢占 Leader;</li>
     *   <li>抢占成功时 {@link ClusterElectionHeartbeat} 注册为 Spring Bean 持续续约;</li>
     *   <li>{@link JobLeaderLoop} 启动单秒扫描循环。</li>
     * </ol>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER", matchIfMissing = true)
    public JobLeaderLoop jobLeaderLoop(LeaderElector jobLeaderElector,
                                       com.chua.starter.job.support.cluster.LeaderElectionResolver jobLeaderElectionResolver,
                                       JobRepository jobRepository,
                                       JobSyncBus jobSyncBus,
                                       String jobNodeId) {
        String group = properties.getCluster().getGroup();
        int leaseSeconds = properties.getCluster().getLeaseSeconds();
        boolean acquired = jobLeaderElectionResolver.tryAcquire(group, jobNodeId, leaseSeconds);
        if (acquired) {
            jobLeaderElector.markLeader(group);
        }
        return new JobLeaderLoop(properties, jobLeaderElector, jobNodeId, jobRepository, jobSyncBus);
    }

    /**
     * SCHEDULER 角色: Leader 续约心跳守护线程。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER", matchIfMissing = true)
    public com.chua.starter.job.support.cluster.ClusterElectionHeartbeat jobClusterElectionHeartbeat(
            LeaderElector jobLeaderElector,
            com.chua.starter.job.support.cluster.LeaderElectionResolver jobLeaderElectionResolver) {
        com.chua.starter.job.support.cluster.ClusterElectionHeartbeat heartbeat =
                new com.chua.starter.job.support.cluster.ClusterElectionHeartbeat(
                        jobLeaderElector,
                        jobLeaderElectionResolver,
                        properties.getCluster().getGroup(),
                        properties.getCluster().getLeaseSeconds());
        heartbeat.start();
        return heartbeat;
    }

    // ====================================================================
    //  EXECUTOR 角色: 装配 ExecutorSyncBinder + JobExecutor
    // ====================================================================

    /**
     * EXECUTOR 端: 注册到仓库公共 SyncClient。
     * <p>SPI 未发现实现时 fallback 到 {@link InMemorySyncClient}。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "EXECUTOR")
    public SyncClient jobSyncClient() {
        SyncClient client = ServiceProvider.of(SyncClient.class).getExtension();
        if (client == null) {
            log.warn("[job] 未找到 SyncClient SPI 实现, fallback 到 InMemorySyncClient (精简部署或测试环境)");
            return new InMemorySyncClient();
        }
        return client;
    }

    /**
     * EXECUTOR 角色: 绑定 SyncClient 与本地事件总线。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "EXECUTOR")
    public ExecutorSyncBinder jobExecutorSyncBinder(SyncClient jobSyncClient,
                                                    JobSyncBus jobSyncBus,
                                                    String jobNodeId) {
        return new ExecutorSyncBinder(jobSyncClient, jobSyncBus, properties, jobNodeId);
    }

    /**
     * EXECUTOR 角色: 任务执行器。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "EXECUTOR")
    public JobExecutor jobExecutor(JobHandlerFactory jobHandlerFactory,
                                   JobSyncBus jobSyncBus,
                                   String jobNodeId) {
        return new JobExecutor(jobHandlerFactory, jobSyncBus, jobNodeId, buildExecutor());
    }

    // ====================================================================
    //  SINGLE 角色: 装配 LeaderLoop + JobExecutor + 默认日志 sink
    // ====================================================================

    /**
     * SINGLE 角色: 本机自认 Leader, 通过本地 CAS Resolver (MyBatis 未启用时)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SINGLE")
    public JobLeaderLoop jobSingleLeaderLoop(LeaderElector jobLeaderElector,
                                            com.chua.starter.job.support.cluster.LeaderElectionResolver jobLeaderElectionResolver,
                                            JobRepository jobRepository,
                                            JobSyncBus jobSyncBus,
                                            String jobNodeId) {
        String group = properties.getCluster().getGroup();
        int leaseSeconds = properties.getCluster().getLeaseSeconds();
        boolean acquired = jobLeaderElectionResolver.tryAcquire(group, jobNodeId, leaseSeconds);
        if (acquired) {
            jobLeaderElector.markLeader(group);
        }
        return new JobLeaderLoop(properties, jobLeaderElector, jobNodeId, jobRepository, jobSyncBus);
    }

    /**
     * SINGLE 角色心跳守护线程 (与 SCHEDULER 共享同一实现)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SINGLE")
    public com.chua.starter.job.support.cluster.ClusterElectionHeartbeat jobSingleClusterHeartbeat(
            LeaderElector jobLeaderElector,
            com.chua.starter.job.support.cluster.LeaderElectionResolver jobLeaderElectionResolver) {
        com.chua.starter.job.support.cluster.ClusterElectionHeartbeat heartbeat =
                new com.chua.starter.job.support.cluster.ClusterElectionHeartbeat(
                        jobLeaderElector,
                        jobLeaderElectionResolver,
                        properties.getCluster().getGroup(),
                        properties.getCluster().getLeaseSeconds());
        heartbeat.start();
        return heartbeat;
    }

    /**
     * SINGLE 角色: 本地执行器, 消费本地 bus cmd 事件并执行。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SINGLE")
    public JobExecutor jobSingleExecutor(JobHandlerFactory jobHandlerFactory,
                                          JobSyncBus jobSyncBus,
                                          String jobNodeId) {
        return new JobExecutor(jobHandlerFactory, jobSyncBus, jobNodeId, buildExecutor());
    }

    // ====================================================================
    //  启动/样例
    // ====================================================================

    /**
     * 启动时把已注册 handler 同步到内存仓库 (仅仓库为 InMemory 时生效)。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobSampleSeeder jobSampleSeeder(JobRepository jobRepository, JobHandlerFactory jobHandlerFactory) {
        return new JobSampleSeeder(jobRepository, jobHandlerFactory);
    }

    /**
     * 启动期把 {@link JobHandlerFactory} 已注册的处理器同步为 {@link JobDefinition},
     * 仅当仓库为 {@link InMemoryJobRepository} 时生效, 否则跳过。
     *
     * @author CH
     * @since 4.0.0.42
     */
    public static final class JobSampleSeeder {

        /**
         * 仓库
         */
        private final JobRepository repository;

        /**
         * 处理器注册表
         */
        private final JobHandlerFactory factory;

        /**
         * 构造 seeder。
         *
         * @param repository 任务仓库
         * @param factory    处理器注册表
         */
        public JobSampleSeeder(JobRepository repository, JobHandlerFactory factory) {
            this.repository = repository;
            this.factory = factory;
        }

        /**
         * Spring 上下文刷新完成后回调 (此时所有 SmartInitializingSingleton 已跑完, @Job 处理器已注册)。
         * 仅根上下文 refresh 时触发 (排除子容器)。
         */
        @org.springframework.context.event.EventListener
        public void onContextRefreshed(org.springframework.context.event.ContextRefreshedEvent event) {
            seed();
        }

        private void seed() {
            if (!(repository instanceof InMemoryJobRepository inMemory)) {
                return;
            }
            for (JobHandlerFactory factory : java.util.List.of(factory)) {
                for (com.chua.starter.job.support.handler.JobHandler handler : factory.all()) {
                    JobDefinition definition = new JobDefinition();
                    definition.setName(handler.name());
                    definition.setCron("* * * * * *");
                    definition.setEnabled(true);
                    definition.setDesc(handler.desc());
                    inMemory.upsert(definition);
                    log.info("[job] seeder 注册任务 name={} cron=* * * * * *", handler.name());
                }
            }
        }
    }

    /**
     * 启动期声明。
     */
    @PostConstruct
    public void describeOnStart() {
        log.info("[job] 模块已启用, role={} nodeId={} group={} sync.scheduler={}:{} sync.executor.url={}",
                properties.getRole(),
                properties.getNodeId(),
                properties.getCluster().getGroup(),
                properties.getSync().getScheduler().getListenHost(),
                properties.getSync().getScheduler().getListenPort(),
                properties.getSync().getExecutor().getServerUrl());
    }

    /**
     * 构建执行线程池, 数值取自 {@link JobProperties.PoolConfig}。
     *
     * @return 线程池
     */
    private ThreadPoolExecutor buildExecutor() {
        return new ThreadPoolExecutor(
                properties.getPool().getCoreSize(),
                properties.getPool().getMaxSize(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getPool().getQueueSize()),
                JobExecutor.namedThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
