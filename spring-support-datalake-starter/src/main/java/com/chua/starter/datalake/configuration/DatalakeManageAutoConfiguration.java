package com.chua.starter.datalake.configuration;

import com.chua.datalake.support.pipeline.JdbcPipelineManager;
import com.chua.datalake.support.server.DatalakeServer;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.starter.datalake.controller.DatalakeManageController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * <p>必须排在 {@code DatalakeAutoConfiguration} 之后处理，否则
 * {@code @ConditionalOnBean(DatalakeServer.class)} 会因 bean 定义尚未登记而恒不命中；
 * 该类位于 api 仓，编译期不可见，故用字符串形式声明排序。</p>
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
     * 数据中台管理端点：仅当 DatalakeServer 已装配（datalake.enabled=true）时生效；
     * 未启用时端点整体 404，前端据此呈现未启用空态。
     *
     * @param server 数据湖服务端
     * @return 管理端点
     */
    @Bean
    @ConditionalOnBean(DatalakeServer.class)
    public DatalakeManageController datalakeManageController(DatalakeServer server) {
        return new DatalakeManageController(server);
    }
}
