package com.chua.starter.job;


import com.chua.starter.job.support.JobAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 调度中心 (调度器) 模式可执行应用, 用于「启动服务测接口」全业务验证。
 *
 * <p>运行方式 (模块目录下):</p>
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.arguments="--plugin.job.role=SCHEDULER --plugin.job.sync.scheduler.listen-port=8081"
 * </pre>
 *
 * <p>效果: 装配 {@link JobAutoConfiguration} 并以 SCHEDULER 角色启动,
 * Leader 抢占 + 单秒扫描仓库 + 通过 同步服务端 长链广播/单播 CMD,
 * 再配合 {@code JobExecutorApplication} 组成完整的调度→执行→回传链路。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@SpringBootApplication
@Import(JobAutoConfiguration.class)
@MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)
public class JobSchedulerApplication {

    /**
     * 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(JobSchedulerApplication.class, args);
    }
}
