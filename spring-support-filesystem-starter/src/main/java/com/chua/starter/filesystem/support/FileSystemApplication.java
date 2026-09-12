package com.chua.starter.filesystem.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
* 文件存储模块启动类。
*
* <p>用于分片上传异步合并功能的集成测试，启动嵌入式 H2 并加载 3 张表：
* filesystem_upload_任务、filesystem_upload_chunk、filesystem_upload_文件。</p>
*
* @since 4.0.0.42
* @author CH
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FileSystemApplication {

    /**
    * 临时目录系统属性键
     */
    private static final String TEMP_PATH_KEY = "filesystem.temp.path";

    /**
    * 程序入口。
    *
    * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(FileSystemApplication.class);
        app.addInitializers(ctx -> {
            Environment env = ctx.getEnvironment();
            String tempPath = env.getProperty("plugin.filesystem.temp-path");
            if (tempPath != null && !tempPath.isEmpty()) {
                System.setProperty(TEMP_PATH_KEY, tempPath);
            }
        });
        app.run(args);
    }

    /**
    * 配置嵌入式 H2 数据源并加载 模式 脚本。
    *
    * @return H2 嵌入式数据源
     */
    @Bean
    public DataSource dataSource() {
        /**
        * embeddeddatabase构建器。
        * @return embeddeddatabase构建器的结果
         */
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
    }
}
