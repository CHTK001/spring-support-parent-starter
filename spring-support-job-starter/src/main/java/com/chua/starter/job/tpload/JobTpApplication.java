package com.chua.starter.job.tpload;

import com.chua.starter.job.support.JobAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
* 吞吐量压测专用单机 (单个) 应用, 仅装配 {@link TpJobs}, 排除并发压测任务干扰。
*
* @since 4.0.0.42
* @author CH
 */
@SpringBootApplication
@Import({JobAutoConfiguration.class, TpJobs.class})
@MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)
public class JobTpApplication {

    /**
    * 启动入口。
    *
    * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(JobTpApplication.class, args);
        // 吞吐量压测不需要任何 Web/常驻线程, main 返回会导致 JVM 直接退出;
 // 这里持有 wait 保持进程存活, 测试结束时由外部 停止-处理 终止。
        synchronized (JobTpApplication.class) {
            try {
                JobTpApplication.class.wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}