package com.chua.starter.job;

import com.chua.starter.job.example.DemoJobs;
import com.chua.starter.job.support.JobAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 执行器 (EXECUTOR) 模式可执行应用, 用于「启动服务测接口」全业务验证。
 *
 * <p>运行方式 (模块目录下):</p>
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.arguments="--plugin.job.role=EXECUTOR --plugin.job.sync.executor.server-url=tcp://127.0.0.1:8081"
 * </pre>
 *
 * <p>效果: 装配 {@link JobAutoConfiguration} 并以 EXECUTOR 角色启动,
 * 通过 SyncClient 连上调度中心, 订阅 cmd, 本地执行 handler, 上行 TASK_LOG。
 * 需与 {@code JobSchedulerApplication} 配合组成完整链路。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@SpringBootApplication
@Import({JobAutoConfiguration.class, DemoJobs.class})
@MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)
public class JobExecutorApplication {

    /**
     * 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(JobExecutorApplication.class, args);
    }
}
