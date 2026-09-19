package com.chua.starter.job.support;

import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.impl.TcpSyncClient;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.job.support.cluster.LeaderElector;
import com.chua.starter.job.support.dispatch.InMemoryJobRepository;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.execute.JobExecutor;
import com.chua.starter.job.support.handler.JobAnnotationScanner;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.AsyncLogWriter;
import com.chua.starter.job.support.log.FileJobLogSink;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
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
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = JobProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class JobAutoConfiguration {

    /**
     * 映射器 接口所在包常量。
     * <p>业务方主启动类上需添加 {@code @MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)}
     * 让 MyBatis-Plus 接管本模块的 映射器 接口。</p>
     */
    public static final String MAPPER_PACKAGE = "com.chua.starter.job.support.store";

    /**
     * 配置属性
     */
    private final JobProperties properties;

    /**
     * 当前节点 标识 Bean, 缺省时按 主机名称-pid 推导。
     * @return 作业节点id的结果
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
     * @return 作业任务调度器的结果
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
     * @param jobTaskScheduler 作业任务调度器
     * @return 作业调度注解scanner的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public ScheduledAnnotationScanner jobScheduledAnnotationScanner(TaskScheduler jobTaskScheduler) {
        return new ScheduledAnnotationScanner(jobTaskScheduler);
    }

    /**
     * 处理器注册表。
     * @return 作业处理器工厂的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public JobHandlerFactory jobHandlerFactory() {
        return JobHandlerFactory.getInstance();
    }

    /**
     * @作业 注解扫描器。
     * @return 作业注解scanner的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public JobAnnotationScanner jobAnnotationScanner() {
        return new JobAnnotationScanner();
    }

    /**
     * 进程内事件总线。
     * @return 作业同步bus的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public JobSyncBus jobSyncBus() {
        return new JobSyncBus();
    }

    /**
     * 任务仓库默认实现 (入内存 兜底), MyBatis-Plus 存在时被 MyBatis 版覆盖。
     * @return 入内存作业仓库的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("com.baomidou.mybatisplus.core.mapper.BaseMapper")
    public JobRepository inMemoryJobRepository() {
        return new InMemoryJobRepository();
    }

    /**
     * 铅 选举器 (CAS 占位)。
     * @param jobNodeId 作业节点标识
     * @return 作业铅elector的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public LeaderElector jobLeaderElector(String jobNodeId) {
        return new LeaderElector(jobNodeId);
    }

    /**
     * 日志落库默认实现 (stdout 兜底), MyBatis-Plus 存在时被 MyBatis 版覆盖。
     * @return stdout作业日志sink的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("com.baomidou.mybatisplus.core.mapper.BaseMapper")
    public JobLogSink stdoutJobLogSink() {
        return new StdoutJobLogSink();
    }

    /**
     * MyBatis-Plus 任务仓库 (依赖 MyBatis-Plus 时启用, 覆盖默认 入内存)。
     *
     * <p>启用条件: classpath 含 {@code com.baomidou.mybatisplus.core.mapper.BaseMapper};
     * 业务侧无需自行声明 作业仓库。</p>
     * @param jobMapper 作业映射器
     * @return mybatis作业仓库的结果
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
     * 当 {@code plugin.job.log.enabled=true} 时额外启用文件日志和异步写入。
     * @param jobLogMapper 作业日志映射器
     * @return mybatis作业日志sink的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    @org.springframework.context.annotation.Primary
    public JobLogSink mybatisJobLogSink(com.chua.starter.job.support.store.JobLogMapper jobLogMapper) {
        List<JobLogSink> sinks = new ArrayList<>(2);
        com.chua.starter.job.support.store.MybatisJobLogSink dbSink =
                new com.chua.starter.job.support.store.MybatisJobLogSink(jobLogMapper, properties.getLog().isEnabled());
        sinks.add(dbSink);
        if (properties.getLog().isEnabled()) {
            FileJobLogSink fileSink = new FileJobLogSink(properties.getLog().getBasePath());
            sinks.add(fileSink);
            log.info("[job] 文件日志已启用 basePath={}", properties.getLog().getBasePath());
        }
        if (sinks.size() == 1) {
            if (properties.getLog().isAsync()) {
                AsyncLogWriter async = new AsyncLogWriter(sinks, properties.getLog().getQueueSize(), "job-async-log-writer");
                log.info("[job] 异步日志已启用 queueSize={}", properties.getLog().getQueueSize());
                return async;
            }
            return dbSink;
        }
        AsyncLogWriter async = new AsyncLogWriter(sinks, properties.getLog().getQueueSize(), "job-async-log-writer");
        log.info("[job] 组合日志已启用 (DB+File) async={} queueSize={}",
                properties.getLog().isAsync(), properties.getLog().getQueueSize());
        return async;
    }

    /**
     * 铅 选举解析器默认实现: 本地 CAS 占位, 适用于单机或无 MyBatis 场景。
     * @return 入内存铅election解析器的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("com.baomidou.mybatisplus.core.mapper.BaseMapper")
    public com.chua.starter.job.support.cluster.LeaderElectionResolver inMemoryLeaderElectionResolver() {
        return new com.chua.starter.job.support.cluster.InMemoryLeaderElectionResolver();
    }

    /**
     * MyBatis-Plus 行锁版 铅 选举 (依赖 MyBatis-Plus 时覆盖默认)。
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
 // 调度器 角色: 装配 铅循环 + 调度器同步binder
    // ====================================================================

    /**
     * 调度器 端: 注册到仓库公共 同步服务端。
     * <p>SPI 未发现实现时 (精简部署或单元测试),自动 fallback 到 {@link InMemorySyncServer}。</p>
     * @return 作业同步服务端的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER")
    public SyncServer jobSyncServer() {
        if (properties.getSync().isInMemory()) {
            log.info("[job] sync.in-memory=true, 强制使用 InMemorySyncServer (跳过 SPI)");
            /**
             * 入内存同步服务端。
             * @return 入内存同步服务端的结果
             */
            return new InMemorySyncServer();
        }
        ServiceProvider<SyncServer> sp = ServiceProvider.of(SyncServer.class);
        log.info("[job][diag] SyncServer SPI keys={} loader={} ctxLoader={}", sp.names(),
                SyncServer.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader());
        try {
            java.util.Enumeration<java.net.URL> rs = Thread.currentThread().getContextClassLoader()
                    .getResources("META-INF/extensions/com.chua.common.support.network.server.SyncServer");
            java.util.List<String> urls = new java.util.ArrayList<>();
            while (rs.hasMoreElements()) { urls.add(rs.nextElement().toString()); }
            log.info("[job][diag] SyncServer SPI resource URLs={}", urls);
        } catch (Throwable ex) {
            log.warn("[job][diag] SyncServer SPI resource enumeration failed cause={}", ex.getMessage());
        }
        String[] keys = {"rsocket", "tcp", "udp", "websocket", "http"};
        SyncServer server = null;
        for (String k : keys) {
            try {
                SyncServer s = sp.getExtension(k);
                log.info("[job][diag] try key={} -> class={} extClassLoader={}",
                        k, s == null ? "NULL" : s.getClass().getName(), s == null ? "-" : s.getClass().getClassLoader());
                if (s != null) {
                    server = s;
                    break;
                }
            } catch (Throwable t) {
                log.warn("[job][diag] key={} threw cause={}", k, t.getClass().getName() + ":" + t.getMessage(), t);
            }
        }
        log.info("[job][diag] SyncServer final result class={}",
                server == null ? "NULL" : server.getClass().getName());
        if (server == null) {
            log.warn("[job] 未找到 SyncServer SPI 实现, fallback 到 InMemorySyncServer (精简部署或测试环境)");
            /**
             * 入内存同步服务端。
             * @return 入内存同步服务端的结果
             */
            return new InMemorySyncServer();
        }
 // 注入配置的监听 主机/端口, 使 tcp同步服务端 等真实实现绑定到目标端口
        try {
            com.chua.common.support.network.server.ServerSetting setting = server.getSetting();
            if (setting != null) {
                setting.setHost(properties.getSync().getScheduler().getListenHost());
                setting.setPort(properties.getSync().getScheduler().getListenPort());
                log.info("[job] SyncServer 监听地址注入 host={} port={}",
                        setting.getHost(), setting.getPort());
            }
        } catch (Throwable ex) {
            log.warn("[job] SyncServer 监听地址注入失败 cause={}", ex.getMessage());
        }
        return server;
    }

    /**
     * 调度器 角色: bind 同步, 把 bus 的 CMD 推到远端, 并落库 任务-日志。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER")
    public SchedulerSyncBinder jobSchedulerSyncBinder(SyncServer jobSyncServer,
                                                     JobSyncBus jobSyncBus,
                                                     JobLogSink jobLogSink,
                                                     String jobNodeId) {
        return new SchedulerSyncBinder(jobSyncServer, jobSyncBus, jobLogSink, jobNodeId);
    }

    /**
     * 调度器 角色: 通过 SPI 抢占 铅 + 启动续约心跳 + 单秒循环。
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
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER")
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
        /**
         * 作业铅循环。
         * @param properties 属性
         * @param jobLeaderElector 作业铅elector
         * @param jobNodeId 作业节点标识
         * @param jobRepository 作业仓库
         * @param jobSyncBus 作业同步bus
         * @return 作业铅循环的结果
         */
        return new JobLeaderLoop(properties, jobLeaderElector, jobNodeId, jobRepository, jobSyncBus);
    }

    /**
     * 调度器 角色: 铅 续约心跳守护线程。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER")
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
 // 执行器 角色: 装配 执行器同步binder + 作业执行器
    // ====================================================================

    /**
     * 执行器 端: 注册到仓库公共 同步客户端。
     * <p>
     * 优先走仓库 SPI 获取网络协议客户端 (tcp/udp/WebSocket/http/Socket.IO)；
     * 由于 {@link TcpSyncClient} 无无参构造、其一键 SPI 实例化在部分 SPI 环境下返回 空,
     * 当 SPI 未命中或指向 入内存 时, 直接以配置的 服务端-url 构造真实 {@link TcpSyncClient}。
     * </p>
     * @return 作业同步客户端的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "EXECUTOR")
    public SyncClient jobSyncClient() {
        if (properties.getSync().isInMemory()) {
            log.info("[job] sync.in-memory=true, 强制使用 InMemorySyncClient (跳过 SPI)");
            /**
             * 入内存同步客户端。
             * @return 入内存同步客户端的结果
             */
            return new InMemorySyncClient();
        }
        SyncClient spiClient = ServiceProvider.of(SyncClient.class)
                .getExtension("rsocket", "tcp", "udp", "websocket", "http", "socketio");
        if (spiClient != null && !(spiClient instanceof InMemorySyncClient)) {
            log.info("[job] SyncClient SPI 命中 impl={}", spiClient.getClass().getName());
            return spiClient;
        }
        String serverUrl = properties.getSync().getExecutor().getServerUrl();
        log.warn("[job] SyncClient SPI 未命中有效实现 (got={}), 直接构造 TcpSyncClient serverUrl={}",
                spiClient == null ? "null" : spiClient.getClass().getName(), serverUrl);
        /**
         * tcp同步客户端。
         * @param serverUrl 服务端url
         * @return tcp同步客户端的结果
         */
        return new TcpSyncClient(serverUrl);
    }

    /**
     * 执行器 角色: 绑定 同步客户端 与本地事件总线。
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
     * 执行器 角色: 任务执行器。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "EXECUTOR")
    public JobExecutor jobExecutor(JobHandlerFactory jobHandlerFactory,
                                   JobSyncBus jobSyncBus,
                                   String jobNodeId) {
        return new JobExecutor(jobHandlerFactory, jobSyncBus, jobNodeId, buildExecutor());
    }

    // ====================================================================
 // 单个 角色: 装配 铅循环 + 作业执行器 + 默认日志 sink
    // ====================================================================

    /**
     * 单个 角色: 本机自认 铅, 通过本地 CAS 解析器 (MyBatis 未启用时)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SINGLE", matchIfMissing = true)
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
        /**
         * 作业铅循环。
         * @param properties 属性
         * @param jobLeaderElector 作业铅elector
         * @param jobNodeId 作业节点标识
         * @param jobRepository 作业仓库
         * @param jobSyncBus 作业同步bus
         * @return 作业铅循环的结果
         */
        return new JobLeaderLoop(properties, jobLeaderElector, jobNodeId, jobRepository, jobSyncBus);
    }

    /**
     * 单个 角色心跳守护线程 (与 调度器 共享同一实现)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SINGLE", matchIfMissing = true)
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
     * 单个 角色: 本地执行器, 消费本地 bus CMD 事件并执行。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SINGLE", matchIfMissing = true)
    public JobExecutor jobSingleExecutor(JobHandlerFactory jobHandlerFactory,
                                          JobSyncBus jobSyncBus,
                                          String jobNodeId) {
        return new JobExecutor(jobHandlerFactory, jobSyncBus, jobNodeId, buildExecutor());
    }

    // ====================================================================
    //  启动/样例
    // ====================================================================

    /**
     * 启动时把已注册 处理器 同步到内存仓库 (仅仓库为 入内存 时生效)。
     * @param jobRepository 作业仓库
     * @param jobHandlerFactory 作业处理器工厂
     * @return 作业样本seeder的结果
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
         * @return 作业样本seeder的结果
         */
        public JobSampleSeeder(JobRepository repository, JobHandlerFactory factory) {
            this.repository = repository;
            this.factory = factory;
        }

        /**
         * Spring 上下文刷新完成后回调 (此时所有 smart初始化单例 已跑完, @作业 处理器已注册)。
         * 仅根上下文 refresh 时触发 (排除子容器)。
         * @param event 事件
         */
        @org.springframework.context.event.EventListener
        public void onContextRefreshed(org.springframework.context.event.ContextRefreshedEvent event) {
            /**
             * 参见。
             */
            seed();
        }

        /** Seed */
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
        com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry.getInstance().registerFlag(
                "job", "分布式任务",
                properties.isEnable(),
                "[role=" + properties.getRole() + "] [nodeId=" + properties.getNodeId() + "]",
                "[group=" + properties.getCluster().getGroup() + "]");
        log.info("[job] 模块已启用, role={} nodeId={} group={} sync.scheduler={}:{} sync.executor.url={}",
                properties.getRole(),
                properties.getNodeId(),
                properties.getCluster().getGroup(),
                properties.getSync().getScheduler().getListenHost(),
                properties.getSync().getScheduler().getListenPort(),
                properties.getSync().getExecutor().getServerUrl());
    }

    /**
     * 构建执行器, 按 {@code plugin.job.pool.virtual} 切换实现。
     *
     * <ul>
     *   <li>true (默认): 虚拟线程执行器 (per-task, 无 max/queue 限制, 阻塞型任务吞吐最高);</li>
     *   <li>false: 平台线程池 {@code ThreadPoolExecutor} (core/max/queue + AbortPolicy)。</li>
     * </ul>
     *
     * @return 执行器
     */
    private ExecutorService buildExecutor() {
        if (properties.getPool().isVirtual()) {
            log.info("[job] 已启用虚拟线程执行器 pool.virtual=true (per-task 虚拟线程)");
            return JobExecutor.defaultExecutor();
        }
        log.info("[job] 使用平台线程池 pool.virtual=false core={} max={} queue={}",
                properties.getPool().getCoreSize(),
                properties.getPool().getMaxSize(),
                properties.getPool().getQueueSize());
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
