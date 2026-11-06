package com.chua.starter.lowcode;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 低代码模块集成测试用的最小启动类。
 *
 * <p>只启用自动装配，不扫描业务包：模块能力全部经
 * {@code META-INF/spring/...AutoConfiguration.imports} 装配，正好验证「加依赖即生效」。
 * 不引入 SpringBootApplication，避免把测试包当成组件扫描根扫进无关 bean。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class LowcodeTestApplication {
}
