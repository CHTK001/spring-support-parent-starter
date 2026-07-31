package com.chua.starter.filesystem.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * 文件存储模块启动类。
 *
 * <p>用于分片上传异步合并功能的集成测试，启动嵌入式 H2 并加载 3 张表：
 * filesystem_upload_task、filesystem_upload_chunk、filesystem_upload_file。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FileSystemApplication {

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FileSystemApplication.class, args);
    }

    /**
     * 配置嵌入式 H2 数据源并加载 schema 脚本。
     *
     * @return H2 嵌入式数据源
     */
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
    }
}
