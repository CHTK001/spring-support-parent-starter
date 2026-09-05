package com.chua.starter.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 真实 Spring Boot 集成测试应用。
 *
 * <p>聚合 oauth-client（接口/按钮权限）、mybatis（数据权限）、common（api 版本/灰度）、
 * datasource（多数据源/@DS/合并/分片）各模块的自动装配，供集成测试驱动。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootApplication
public class SpringSupportTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringSupportTestApplication.class, args);
    }
}
