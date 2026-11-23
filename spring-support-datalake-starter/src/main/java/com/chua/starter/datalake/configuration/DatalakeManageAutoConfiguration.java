package com.chua.starter.datalake.configuration;

import com.chua.datalake.support.pipeline.JdbcPipelineManager;
import com.chua.datalake.support.server.DatalakeServer;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.starter.datalake.controller.DatalakeManageController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 数据中台管理自动装配。
 *
 * <p>两件事：</p>
 * <ol>
 *   <li>宿主存在 {@link DataSource} 且未自定义 {@link PipelineManager} 时，
 *       装配 {@link JdbcPipelineManager}（管线配置落库 + 编译缓存），使管线在重启后仍在；</li>
 *   <li>宿主装配了 {@link DatalakeServer}（即 {@code datalake.enabled=true}）时，
 *       暴露 {@code /v2/datalake/**} 管理端点。</li>
 * </ol>
 *
 * <p>控制器条件用 {@code @ConditionalOnProperty(datalake.enabled=true)}，而不用
 * {@code @ConditionalOnBean(DatalakeServer.class)}：后者跨自动装配类按「bean 定义
 * 是否已登记」判定，受解析/注册顺序影响，在本运行时（Spring Boot 4，2026-11-23
 * 194 实证）恒不命中——jdbcPipelineManager 正常装配、datalakeServer 也已创建，
 * 控制器却被静默跳过，{@code /v2/datalake/**} 整体 404。属性条件与 bean 注册顺序
 * 无关；且 {@code datalake.enabled=true} 时 DatalakeServer 必然由 api 仓的
 * DatalakeAutoConfiguration 装配（本 starter 硬依赖 datalake 类，缺类时属性条件
 * 也不会通过）。{@code @AutoConfigureAfter} 仍保留，保证解析顺序的确定性。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(name = "com.chua.starter.service.configuration.DatalakeAutoConfiguration")
public class DatalakeManageAutoConfiguration {

    /**
     * JDBC 管线管理器：宿主有数据源且未自定义 PipelineManager 时生效。
     *
     * @param dataSource 宿主数据源
     * @return 可持久化的管线管理器
     */
    @Bean
    @ConditionalOnMissingBean(PipelineManager.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    public PipelineManager jdbcPipelineManager(DataSource dataSource) {
        log.info("[datalake-manage] 装配 JdbcPipelineManager（管线配置落库 sys_datalake_pipeline）");
        return new JdbcPipelineManager(dataSource);
    }

    /**
     * 数据中台管理端点：{@code datalake.enabled=true} 时生效（此时 DatalakeServer
     * 必已由 DatalakeAutoConfiguration 装配）；未启用时端点整体 404，
     * 前端据此呈现未启用空态。鉴权沿用宿主统一接口鉴权体系，不设免鉴权白名单。
     *
     * @param server 数据湖服务端
     * @return 管理端点
     */
    @Bean
    @ConditionalOnProperty(name = "datalake.enabled", havingValue = "true")
    public DatalakeManageController datalakeManageController(DatalakeServer server) {
        return new DatalakeManageController(server);
    }
}
