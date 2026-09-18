package com.chua.starter.job;


import com.chua.starter.job.support.JobAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
* 单机 (单个) 模式可执行应用, 用于「启动服务测接口」全业务验证。
*
* <p>运行方式 (模块目录下):</p>
* <pre>
* mvn spring-boot:run -Dspring-boot.run.arguments="--plugin.job.role=SINGLE"
* </pre>
*
* <p>效果: 装配 {@link JobAutoConfiguration} 并以 SINGLE 角色启动,
* 铅 自选 + 单秒扫描 + 本地执行, 观察 处理器 的成功/失败/原生定时日志。</p>
*
* @since 4.0.0.42
* @author CH
 */
@SpringBootApplication
@Import(JobAutoConfiguration.class)
@MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)
public class JobSingleApplication {

    /**
    * 启动入口。
    *
    * @param args 命令行参数
    */
    public static void main(String[] args) {
        SpringApplication.run(JobSingleApplication.class, args);
    }
}
