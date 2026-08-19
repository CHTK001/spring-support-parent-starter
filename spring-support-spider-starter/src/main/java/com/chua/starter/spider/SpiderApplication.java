package com.chua.starter.spider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 爬虫中心独立启动器。
 *
 * <p>连接真实 MySQL（172.16.0.40）运行，可直接执行
 * {@code mvn spring-boot:run} 启动，用于真实环境联调与前端测试。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@SpringBootApplication
public class SpiderApplication {

    /**
     * 入口方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpiderApplication.class, args);
    }
}