package com.chua.starter.job.support;

import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.job.support.cluster.LeaderElector;
import com.chua.starter.job.support.dispatch.InMemoryJobRepository;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.execute.JobExecutor;
import com.chua.starter.job.support.handler.JobAnnotationScanner;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.AsyncLogWriter;
import com.chua.starter.job.support.log.FileJobLogSink;
import com.chua.starter.job.support.log.JobLogRepository;
import com.chua.starter.job.support.log.JobLogSink;
import com.chua.starter.job.support.log.LocalJobLogBinder;
import com.chua.starter.job.support.log.StdoutJobLogSink;
import com.chua.starter.job.support.loop.JobLeaderLoop;
import com.chua.starter.job.support.manage.JobGovernanceService;
import com.chua.starter.job.support.manage.JobManageController;
import com.chua.starter.job.support.operate.DefaultJobOperator;
import com.chua.starter.job.support.operate.JobOperator;
import com.chua.starter.job.support.schedule.ScheduledAnnotationScanner;
import com.chua.starter.job.support.sync.ExecutorSyncBinder;
import com.chua.starter.job.support.sync.GlueSyncService;
import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.RemoteHandlerRegistry;
import com.chua.starter.job.support.sync.SchedulerSyncBinder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
     * 同步服务端 (仅调度中心角色装配, 治理接口用它读在线执行器)
     */
    private final ObjectProvider<SyncServer> syncServerProvider;

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
     * 任务操作器：登记任务、改调度时间、启停、立即派发、删除。
     *
     * <p>调度层的唯一写入口，业务模块（采集等）与对外 HTTP 接口共用同一实现，
     * 保证"改了时间/点了启停"在两条路径上的生效语义完全一致。</p>
     *
     * @param repository 任务仓库
     * @param jobSyncBus 事件总线
     * @param jobNodeId  当前节点标识
     * @return 作业operator的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public JobOperator jobOperator(JobRepository repository, JobSyncBus jobSyncBus, String jobNodeId) {
        return new DefaultJobOperator(repository, jobSyncBus, jobNodeId);
    }

    /**
     * 任务治理的公共实现 (与传输层无关)。
     *
     * <p>装配门槛与治理控制器一致：Servlet 应用 + classpath 有 spring-webmvc + 显式开启
     * {@code plugin.job.manage.enable=true}。宿主若要换一个认证口径 (如自带控制台会话),
     * 复用本 bean 再挂自己的出口即可。</p>
     *
     * @param jobOperator       任务治理接口
     * @param jobLogRepository  执行日志读取侧
     * @param jobLeaderElector  本地选主状态
     * @return 治理实现
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "manage.enable", havingValue = "true")
    public JobGovernanceService jobGovernanceService(JobOperator jobOperator,
                                                      JobLogRepository jobLogRepository,
                                                      LeaderElector jobLeaderElector,
                                                      RemoteHandlerRegistry remoteHandlerRegistry,
                                                      ObjectProvider<GlueSyncService> glueSyncServiceProvider) {
        return new JobGovernanceService(jobOperator, jobLogRepository, properties,
                jobLeaderElector, syncServerProvider, remoteHandlerRegistry, glueSyncServiceProvider);
    }

    /**
     * 任务治理外部接口 (HTTP)。
     *
     * <p>装配门槛：Servlet 应用 + classpath 有 spring-webmvc + 显式开启
     * {@code plugin.job.manage.enable=true}。{@code sys_job} 是全局资源 (无归属列)，
     * 出口整体要求管理员权限，故默认关闭、按需开启。</p>
     *
     * @param governance 任务治理实现
     * @return 任务治理控制器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "manage.enable", havingValue = "true")
    public JobManageController jobManageController(JobGovernanceService governance) {
        return new JobManageController(governance);
    }

    /**
     * 执行日志读取侧默认实现 (不落库时返回空页), MyBatis-Plus 存在时被 MyBatis 版覆盖。
     *
     * @return 空实现的日志仓库
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("com.baomidou.mybatisplus.core.mapper.BaseMapper")
    public JobLogRepository emptyJobLogRepository() {
        return new JobLogRepository() {
        };
    }

    /**
     * MyBatis-Plus 版执行日志读取侧 (依赖 MyBatis-Plus 时启用, 业务侧无需自行声明)。
     *
     * @param jobLogMapper 日志映射器
     * @param jobMapperProvider 任务映射器提供者, 缺失时按台账任务名搜日志会落空
     * @return MyBatis 版日志仓库
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    @org.springframework.context.annotation.Primary
    public JobLogRepository mybatisJobLogRepository(
            com.chua.starter.job.support.store.JobLogMapper jobLogMapper,
            org.springframework.beans.factory.ObjectProvider<com.chua.starter.job.support.store.JobMapper> jobMapperProvider) {
        return new com.chua.starter.job.support.store.MybatisJobLogRepository(jobLogMapper,
                jobMapperProvider.getIfAvailable());
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
     * Leader 选举器 (CAS 占位)。
     * @param jobNodeId 作业节点标识
     * @return 作业 Leader 选举器的结果
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
        // 留空分组落到本节点的 cluster.group：Leader 只扫同组任务，落 default 会"登记成功但永不派发"
        return new com.chua.starter.job.support.store.MybatisJobRepository(jobMapper,
                properties.getCluster().getGroup());
    }

    /**
     * MyBatis-Plus 任务日志落库 (覆盖默认 stdout)。
     * 当 {@code plugin.job.log.enabled=true} 时额外启用文件日志和异步写入。
     * @param jobLogMapper 作业日志映射器
     * @param jobMapperProvider 作业映射器提供者, 缺失时日志行不落任务编号
     * @return mybatis作业日志sink的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    @org.springframework.context.annotation.Primary
    public JobLogSink mybatisJobLogSink(com.chua.starter.job.support.store.JobLogMapper jobLogMapper,
                                        org.springframework.beans.factory.ObjectProvider<com.chua.starter.job.support.store.JobMapper> jobMapperProvider) {
        List<JobLogSink> sinks = new ArrayList<>(2);
        com.chua.starter.job.support.store.MybatisJobLogSink dbSink =
                new com.chua.starter.job.support.store.MybatisJobLogSink(jobLogMapper,
                        jobMapperProvider.getIfAvailable(), properties.getLog().isEnabled());
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
     * Leader 选举解析器默认实现: 本地 CAS 占位, 适用于单机或无 MyBatis 场景。
     * @return 入内存 Leader 选举解析器的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("com.baomidou.mybatisplus.core.mapper.BaseMapper")
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
        String resolverKey = properties.getCluster().getResolver();
        if (resolverKey != null && !resolverKey.isBlank()) {
            ServiceProvider<com.chua.starter.job.support.cluster.LeaderElectionResolver> provider =
                    ServiceProvider.of(com.chua.starter.job.support.cluster.LeaderElectionResolver.class);
            com.chua.starter.job.support.cluster.LeaderElectionResolver spi = null;
            try {
                spi = provider.getNewExtension(resolverKey.trim(), clusterMapper);
            } catch (RuntimeException ex) {
                log.debug("[job] 选主扩展带参实例化失败, 试无参 resolver={} cause={}",
                        resolverKey, ex.getMessage());
            }
            if (spi == null) {
                try {
                    spi = provider.getExtension(resolverKey.trim());
                } catch (RuntimeException ex) {
                    log.debug("[job] 选主扩展无参实例化失败 resolver={} cause={}",
                            resolverKey, ex.getMessage());
                }
            }
            if (spi != null) {
                log.info("[job] 选主实现经 SPI 切换 resolver={} impl={}",
                        resolverKey, spi.getClass().getSimpleName());
                return spi;
            }
            log.warn("[job] 未注册该选主实现, 回落数据库实现 resolver={}", resolverKey);
        }
        return new com.chua.starter.job.support.store.MysqlLeaderElectionResolver(clusterMapper);
    }

    // ====================================================================
 // 调度器 角色: 装配 Leader循环 + 调度器同步binder
    // ====================================================================

    /**
     * 调度器端注册到真实同步服务端。
     *
     * @return 作业同步服务端
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER")
    public SyncServer jobSyncServer() {
        if (properties.getSync().isInMemory()) {
            throw new UnsupportedOperationException("同步服务不支持 in-memory 模式，请配置真实 SyncServer SPI");
        }
        ServiceProvider<SyncServer> sp = ServiceProvider.of(SyncServer.class);
        com.chua.common.support.network.server.ServerSetting setting =
                com.chua.common.support.network.server.ServerSetting.defaults();
        setting.setHost(properties.getSync().getScheduler().getListenHost());
        setting.setPort(properties.getSync().getScheduler().getListenPort());
        for (String key : syncServerCandidates(properties.getSync().getScheduler().getProtocol())) {
            try {
                // 同步服务端实现普遍以 ServerSetting 构造，先按带参 SPI 实例化，避免落到无参构造失败。
                SyncServer server = sp.getNewExtension(key, setting);
                if (server != null) {
                    log.info("[job] SyncServer 协议已选定 protocol={} listen={}:{}",
                            key, setting.getHost(), setting.getPort());
                    return server;
                }
            } catch (RuntimeException ex) {
                log.warn("[job] SyncServer SPI 带参实例化失败 protocol={} cause={}", key, ex.getMessage());
            }
        }
        throw new IllegalStateException("未找到真实 SyncServer SPI 实现，请配置服务端同步协议");
    }

    /**
     * 调度中心 SyncServer 的协议候选顺序：配置的协议优先，其余按兜底顺序补齐。
     *
     * <p>兜底顺序里 rsocket 排最后：{@code RSocketSyncServer} 目前只做出站，
     * 入站报文不会回调 {@code SyncServerListener#onMessage}，
     * 选中它会让执行器注册永远进不了注册表（在线节点恒空）。</p>
     *
     * @param protocol 配置的协议（可为空）
     * @return 候选协议列表
     */
    private static List<String> syncServerCandidates(String protocol) {
        List<String> keys = new ArrayList<>();
        String configured = protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
        if (!configured.isEmpty()) {
            keys.add(configured);
        }
        for (String fallback : new String[]{"tcp", "websocket", "udp", "http", "rsocket"}) {
            if (!keys.contains(fallback)) {
                keys.add(fallback);
            }
        }
        return keys;
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
                                                     String jobNodeId,
                                                     RemoteHandlerRegistry remoteHandlerRegistry,
                                                     ObjectProvider<GlueSyncService> glueSyncServiceProvider) {
        return new SchedulerSyncBinder(jobSyncServer, jobSyncBus, jobLogSink, jobNodeId,
                remoteHandlerRegistry, glueSyncServiceProvider);
    }

    /**
     * 脚本正文同步服务（调度中心侧）：保存/执行器上线时推送正文，派发命令只带版本号。
     *
     * @param jobSyncServer  同步服务端
     * @param jobRepository  任务仓库
     * @return 同步服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER")
    public GlueSyncService jobGlueSyncService(SyncServer jobSyncServer, JobRepository jobRepository) {
        return new GlueSyncService(jobSyncServer, jobRepository);
    }

    /**
     * 执行器处理器清单注册表（调度中心侧）。
     *
     * <p>执行器上报 handler 清单后，调度中心据此校正"未注册"判定与执行器列表；
     * 单机/执行器角色下注册表恒为空，不影响原有语义。</p>
     *
     * @return 处理器清单注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public RemoteHandlerRegistry remoteHandlerRegistry() {
        return new RemoteHandlerRegistry();
    }

    /**
     * 调度器 角色: 通过 SPI 抢占 Leader + 启动续约心跳 + 单秒循环。
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
                                       String jobNodeId,
                                       Environment environment) {
        // 选主租约键与作业分组解耦: 租约走 electionNamespace, 扫描仍按 cluster.group 过滤作业归属
        String electionNamespace = properties.getCluster()
                .resolveElectionNamespace(environment.getProperty("spring.application.name"));
        int leaseSeconds = properties.getCluster().getLeaseSeconds();
        boolean acquired = jobLeaderElectionResolver.tryAcquire(electionNamespace, jobNodeId, leaseSeconds);
        if (acquired) {
            jobLeaderElector.markLeader(electionNamespace);
        }
        /**
         * 作业Leader循环。
         * @param properties 属性
         * @param jobLeaderElector 作业 Leader 选举器
         * @param jobNodeId 作业节点标识
         * @param jobRepository 作业仓库
         * @param jobSyncBus 作业同步bus
         * @return 作业Leader循环的结果
         */
        return new JobLeaderLoop(properties, jobLeaderElector, jobNodeId, jobRepository, jobSyncBus);
    }

    /**
     * 调度器 角色: Leader 续约心跳守护线程。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SCHEDULER")
    public com.chua.starter.job.support.cluster.ClusterElectionHeartbeat jobClusterElectionHeartbeat(
            LeaderElector jobLeaderElector,
            com.chua.starter.job.support.cluster.LeaderElectionResolver jobLeaderElectionResolver,
            Environment environment) {
        com.chua.starter.job.support.cluster.ClusterElectionHeartbeat heartbeat =
                new com.chua.starter.job.support.cluster.ClusterElectionHeartbeat(
                        jobLeaderElector,
                        jobLeaderElectionResolver,
                        properties.getCluster()
                                .resolveElectionNamespace(environment.getProperty("spring.application.name")),
                        properties.getCluster().getLeaseSeconds());
        heartbeat.start();
        return heartbeat;
    }

    // ====================================================================
 // 执行器 角色: 装配 执行器同步binder + 作业执行器
    // ====================================================================

    /**
     * 执行器端装配真实同步客户端。
     *
     * @return 作业同步客户端
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "EXECUTOR")
    public SyncClient jobSyncClient() {
        if (properties.getSync().isInMemory()) {
            throw new UnsupportedOperationException("同步客户端不支持 in-memory 模式，请配置真实 SyncClient SPI");
        }
        String serverUrl = properties.getSync().getExecutor().getServerUrl();
        ServiceProvider<SyncClient> sp = ServiceProvider.of(SyncClient.class);
        List<String> keys = new ArrayList<>();
        String scheme = schemeOf(serverUrl);
        if (scheme != null) {
            keys.add(scheme);
        }
        for (String candidate : new String[]{"rsocket", "tcp", "udp", "websocket", "http", "socketio"}) {
            if (!keys.contains(candidate)) {
                keys.add(candidate);
            }
        }
        for (String key : keys) {
            SyncClient client = trySyncClient(sp, key, serverUrl, false);
            if (client == null) {
                client = trySyncClient(sp, key, serverUrl, true);
            }
            if (client != null) {
                return client;
            }
        }
        throw new IllegalStateException("未找到真实 SyncClient SPI 实现，请配置执行器同步协议和 serverUrl");
    }

    /**
     * 尝试按 key 获取 SyncClient；带参模式用 serverUrl 走构造注入。
     *
     * @param sp        服务提供者
     * @param key       SPI key
     * @param serverUrl 调度中心长链 URL
     * @param withArgs  是否使用带参构造
     * @return 可用实例；不可用返回 null（不抛出，交由调用方尝试下一个 key）
     */
    private SyncClient trySyncClient(ServiceProvider<SyncClient> sp, String key,
                                     String serverUrl, boolean withArgs) {
        try {
            SyncClient client = withArgs ? sp.getNewExtension(key, serverUrl) : sp.getExtension(key);
            if (client != null) {
                return client;
            }
        } catch (Throwable ex) {
            log.debug("[job] SyncClient key={} withArgs={} 不可用 cause={}", key, withArgs, ex.getMessage());
        }
        return null;
    }

    /**
     * 提取 URL scheme。
     *
     * @param url URL，如 tcp://127.0.0.1:8081
     * @return scheme（小写），无则返回 null
     */
    private static String schemeOf(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.indexOf("://");
        return idx <= 0 ? null : url.substring(0, idx).toLowerCase();
    }

    /**
     * 执行器 角色: 绑定 同步客户端 与本地事件总线。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "EXECUTOR")
    public ExecutorSyncBinder jobExecutorSyncBinder(SyncClient jobSyncClient,
                                                    JobSyncBus jobSyncBus,
                                                    String jobNodeId,
                                                    ApplicationContext applicationContext) {
        return new ExecutorSyncBinder(jobSyncClient, jobSyncBus, properties, jobNodeId, applicationContext);
    }

    /**
     * 执行器 角色: 任务执行器。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "EXECUTOR")
    public JobExecutor jobExecutor(JobHandlerFactory jobHandlerFactory,
                                   JobSyncBus jobSyncBus,
                                   String jobNodeId,
                                   ObjectProvider<com.chua.starter.job.support.notify.JobAlarmNotifier> alarmNotifierProvider,
                                   ApplicationContext applicationContext) {
        return new JobExecutor(jobHandlerFactory, jobSyncBus, jobNodeId, buildExecutor(),
                aggregateAlarmNotifiers(alarmNotifierProvider), JobSpec.CONCURRENT_ALLOW, applicationContext);
    }

    /**
     * 收集宿主注册的告警通道并按类型聚合成一个路由器。
     *
     * <p>一条都没注册时返回 {@code null}，让执行器走"未接入告警通道"的既有分支；
     * 注册了多条（邮件 + Webhook 等）时由聚合器按台账的告警类型选择一条投递。</p>
     *
     * @param provider 宿主注册的通道实现
     * @return 聚合后的通道，未注册任何实现时为 null
     */
    private static com.chua.starter.job.support.notify.JobAlarmNotifier aggregateAlarmNotifiers(
            ObjectProvider<com.chua.starter.job.support.notify.JobAlarmNotifier> provider) {
        java.util.List<com.chua.starter.job.support.notify.JobAlarmNotifier> notifiers =
                provider.orderedStream().toList();
        if (notifiers.isEmpty()) {
            return null;
        }
        return new com.chua.starter.job.support.notify.CompositeJobAlarmNotifier(notifiers);
    }

    // ====================================================================
 // 单个 角色: 装配 Leader循环 + 作业执行器 + 默认日志 sink
    // ====================================================================

    /**
     * 单个 角色: 本机自认 Leader, 通过本地 CAS 解析器 (MyBatis 未启用时)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SINGLE", matchIfMissing = true)
    public JobLeaderLoop jobSingleLeaderLoop(LeaderElector jobLeaderElector,
                                            com.chua.starter.job.support.cluster.LeaderElectionResolver jobLeaderElectionResolver,
                                            JobRepository jobRepository,
                                            JobSyncBus jobSyncBus,
                                            String jobNodeId,
                                            Environment environment) {
        // 与调度器角色同口径: 选主租约键与作业分组解耦
        String electionNamespace = properties.getCluster()
                .resolveElectionNamespace(environment.getProperty("spring.application.name"));
        int leaseSeconds = properties.getCluster().getLeaseSeconds();
        boolean acquired = jobLeaderElectionResolver.tryAcquire(electionNamespace, jobNodeId, leaseSeconds);
        if (acquired) {
            jobLeaderElector.markLeader(electionNamespace);
        }
        /**
         * 作业Leader循环。
         * @param properties 属性
         * @param jobLeaderElector 作业 Leader 选举器
         * @param jobNodeId 作业节点标识
         * @param jobRepository 作业仓库
         * @param jobSyncBus 作业同步bus
         * @return 作业Leader循环的结果
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
            com.chua.starter.job.support.cluster.LeaderElectionResolver jobLeaderElectionResolver,
            Environment environment) {
        com.chua.starter.job.support.cluster.ClusterElectionHeartbeat heartbeat =
                new com.chua.starter.job.support.cluster.ClusterElectionHeartbeat(
                        jobLeaderElector,
                        jobLeaderElectionResolver,
                        properties.getCluster()
                                .resolveElectionNamespace(environment.getProperty("spring.application.name")),
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
                                          String jobNodeId,
                                          ObjectProvider<com.chua.starter.job.support.notify.JobAlarmNotifier> alarmNotifierProvider,
                                          ApplicationContext applicationContext) {
        return new JobExecutor(jobHandlerFactory, jobSyncBus, jobNodeId, buildExecutor(),
                aggregateAlarmNotifiers(alarmNotifierProvider), JobSpec.CONCURRENT_ALLOW, applicationContext);
    }

    /**
     * 单个 角色: 本机执行日志落地桥。
     *
     * <p>执行结果只发到本地总线, 而 TASK_LOG 的订阅者只在 调度器 角色装配;
     * 缺这座桥时单机角色自己的每次执行都不会进 {@code sys_job_log}。</p>
     *
     * @param jobSyncBus  作业同步bus
     * @param jobLogSink  日志落地目标
     * @return 单机日志桥的结果
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = JobProperties.PRE, name = "role", havingValue = "SINGLE", matchIfMissing = true)
    public LocalJobLogBinder jobSingleLogBinder(JobSyncBus jobSyncBus, JobLogSink jobLogSink) {
        return new LocalJobLogBinder(jobSyncBus, jobLogSink);
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

        /**
         * Seed
        */
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
