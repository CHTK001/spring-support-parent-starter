// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.test;

import com.chua.starter.ueba.configuration.UebaAutoConfiguration;
import com.chua.ueba.support.dto.TrafficEvent;
import com.chua.ueba.support.dto.UebaResult;
import com.chua.ueba.support.engine.UebaEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UEBA 自动装配集成测试。
 * <p>
 * 使用 {@link ApplicationContextRunner} 真实加载 {@link UebaAutoConfiguration}，
 * 验证引擎 Bean 被装配且可用于真实分析（规则回退路径，无需外部模型）。
 * 不使用全量 {@code @SpringBootTest} 上下文——测试-app 全上下文会因 common-starter
 * 缓存配置 引用 spring-数据-Redis 而失败（与该模块无关）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class UebaRealTest {

    /**
     * 真实加载 UEBA 自动配置的运行器
    */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UebaAutoConfiguration.class));

    /**
     * 验证 uebaengine Bean 已装配。
     */
    @Test
    void autoConfiguredEngineExists() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(UebaEngine.class));
    }

    /**
     * 真实分析：正常流量判 NORMAL，滥用流量判非 NORMAL。
     */
    @Test
    void analyzeNormalAndAbuse() {
        contextRunner.run(context -> {
            UebaEngine engine = context.getBean(UebaEngine.class);
            long t = System.currentTimeMillis();
            UebaResult normal = null;
            for (int i = 0; i < 10; i++) {
                normal = engine.analyze(TrafficEvent.builder()
                        .ip("10.0.0.1")
                        .path("/index")
                        .method("GET")
                        .statusCode(200)
                        .userAgent("Mozilla/5.0")
                        .timestamp(t + i * 1000L)
                        .responseTimeMs(50)
                        .build());
            }
            /**
             * 断言that。
             * @param normal.getRiskLevel( normal.获取risk级别(
             */
            assertThat(normal.getRiskLevel()).isEqualTo(UebaResult.RiskLevel.NORMAL);

            UebaResult abuse = null;
            for (int i = 0; i < 6; i++) {
                abuse = engine.analyze(TrafficEvent.builder()
                        .ip("203.0.113.9")
                        .path("/admin")
                        .method("POST")
                        .statusCode(500)
                        .userAgent("python-requests/" + i)
                        .timestamp(t + 100L + i * 20L)
                        .responseTimeMs(3000)
                        .build());
            }
            /**
             * 断言that。
             * @param abuse.getRiskLevel( abuse.获取risk级别(
             */
            assertThat(abuse.getRiskLevel()).isNotEqualTo(UebaResult.RiskLevel.NORMAL);
        });
    }

    /**
     * 空 事件必须被拒绝（P3C 规约十五）。
     */
    @Test
    void analyzeNullRejected() {
        contextRunner.run(context -> {
            UebaEngine engine = context.getBean(UebaEngine.class);
            assertThatThrownBy(() -> engine.analyze(null))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }}