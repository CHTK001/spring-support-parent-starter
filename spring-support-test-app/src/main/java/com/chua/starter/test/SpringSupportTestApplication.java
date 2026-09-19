package com.chua.starter.test;

import com.chua.starter.common.support.api.configuration.ApiConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 真实 Spring Boot 集成测试应用。
 *
 * <p>聚合 oauth-client（接口/按钮权限）、mybatis（数据权限）、common（api 版本/灰度）、
 * datasource（多数据源/@DS/合并/分片）各模块的自动装配，供集成测试驱动。</p>
 *
 * <p>{@link ApiConfiguration} 未注册为自动配置，此处应用级显式导入：
 * 经 {@code WebMvcRegistrations} 装配版本路由映射（yaml 已关闭 uniform/encode/decode，
 * 仅激活版本接管），保证含同路径多版本控制器的所有测试上下文一致启动。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootApplication
@MapperScan("com.chua.starter.test.mapper")
@Import(ApiConfiguration.class)
public class SpringSupportTestApplication {

    /**
     * main。
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringSupportTestApplication.class, args);
    }
}
