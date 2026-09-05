package com.chua.starter.ueba.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.ueba.support.Ueba;
import com.chua.ueba.support.engine.UebaEngine;
import com.chua.ueba.support.engine.UebaEngineBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * UEBA 自动配置。
 * <p>
 * 当 classpath 存在 {@link UebaEngine}（依赖 utils-support-ueba-starter）且属性
 * {@code plugin.ueba.enabled=true}（默认）时，装配一个可注入的 {@link UebaEngine}
 * Spring Bean。配置来源优先级：{@code configPath} 外部文件 &gt; {@code configResource}
 * classpath 资源；模型目录通过 {@code modelDir} 写入 {@code ueba.model.dir}。
 * Bean 生命周期随 Spring 容器销毁时自动调用 {@code UebaEngine#close()} 释放模型资源。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * &#064;Resource
 * private UebaEngine uebaEngine;
 *
 * UebaResult result = uebaEngine.analyze(trafficEvent);
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(UebaEngine.class)
@ConditionalOnProperty(prefix = "plugin.ueba", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(UebaProperties.class)
public class UebaAutoConfiguration {

    /**
     * 装配 UEBA 分析引擎 Bean。
     *
     * @param properties UEBA 配置属性，不能为 null
     * @return 就绪的分析引擎
     * @throws IllegalStateException 当默认配置加载失败时
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public UebaEngine uebaEngine(UebaProperties properties) {
        UebaEngineBuilder builder = Ueba.engine();
        if (StringUtils.hasText(properties.getModelDir())) {
            builder.modelDir(properties.getModelDir());
        }
        if (StringUtils.hasText(properties.getConfigPath())) {
            builder.configFile(properties.getConfigPath());
        } else {
            builder.configResource(properties.getConfigResource());
        }
        if (properties.isLlm()) {
            builder.enableLlm();
        } else {
            builder.disableLlm();
        }
        UebaEngine engine = builder.build();
        log.info("[UEBA-Spring] UebaEngine 装配完成: configPath={}, configResource={}, modelDir={}, llm={}",
                properties.getConfigPath(), properties.getConfigResource(),
                properties.getModelDir(), properties.isLlm());
        return engine;
    }

    /**
     * UEBA 分析引擎的 ServiceInfo 贡献者。
     *
     * @param properties UEBA 配置属性
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor uebaServiceInfoContributor(UebaProperties properties) {
        return registry -> registry.registerFlag("ueba", "UEBA", true,
                "UEBA 分析引擎",
                "[LLM: " + (properties.isLlm() ? "开" : "关") + "]");
    }
}