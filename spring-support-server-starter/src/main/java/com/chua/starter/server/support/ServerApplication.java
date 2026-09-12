package com.chua.starter.server.support;

import com.chua.starter.server.support.config.ServerAutoConfiguration;
import com.chua.starter.server.support.config.TerminalWebSocketConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
* 服务器资产模块独立启动入口（仅本地联调使用）
* <p>
* 正式环境通过 {@code ServerAutoConfiguration} 以 启动 方式装配；
* 本地开发可直接启动本类进行前后端联调。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@SpringBootApplication
@Import({ServerAutoConfiguration.class, TerminalWebSocketConfig.class})
public class ServerApplication {

    /**
    * 主方法
    *
    * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
