package com.chua.starter.apm.support.config;

import com.chua.starter.apm.support.controller.ApmOperLogController;
import com.chua.starter.apm.support.mapper.MsApmOperLogMapper;
import com.chua.starter.apm.support.operlog.DefaultApmOperLogSink;
import com.chua.starter.apm.support.scheduler.ApmOperLogScheduler;
import com.chua.starter.apm.support.service.ApmOperLogService;
import com.chua.starter.apm.support.service.impl.ApmOperLogServiceImpl;
import com.chua.starter.apm.support.web.ApmOperLogSink;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * APM 操作日志的数据源条件装配。
 *
 * <p>没有 {@link DataSource} 时整组 Bean 不注册，APM standalone 仍可启动；
 * 嵌入宿主提供数据源时才装配 Mapper、查询控制器、默认落点与保留期调度器。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnBean(DataSource.class)
@MapperScan(basePackages = "com.chua.starter.apm.support.mapper")
@EnableScheduling
public class ApmOperLogConfiguration {

    /**
     * 注册 APM 操作日志服务。
     *
     * @param mapper APM 操作日志 Mapper
     * @return APM 操作日志服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ApmOperLogService apmOperLogService(MsApmOperLogMapper mapper) {
        return new ApmOperLogServiceImpl(mapper);
    }

    /**
     * 注册默认写操作落点。
     *
     * @param operLogService APM 操作日志服务
     * @return 默认写操作落点
     */
    @Bean
    @ConditionalOnMissingBean(ApmOperLogSink.class)
    @ConditionalOnClass(name = "jakarta.servlet.Servlet")
    public ApmOperLogSink defaultApmOperLogSink(ApmOperLogService operLogService) {
        return new DefaultApmOperLogSink(operLogService);
    }

    /**
     * 注册嵌入模式的日志查询控制器。
     *
     * @param operLogService APM 操作日志服务
     * @return 日志查询控制器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "jakarta.servlet.Servlet")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public ApmOperLogController apmOperLogController(ApmOperLogService operLogService) {
        return new ApmOperLogController(operLogService);
    }

    /**
     * 注册日志保留期调度器。
     *
     * @param operLogServiceProvider APM 操作日志服务提供者
     * @return 日志保留期调度器
     */
    @Bean
    @ConditionalOnMissingBean
    public ApmOperLogScheduler apmOperLogScheduler(
            org.springframework.beans.factory.ObjectProvider<ApmOperLogService> operLogServiceProvider) {
        return new ApmOperLogScheduler(operLogServiceProvider);
    }
}
