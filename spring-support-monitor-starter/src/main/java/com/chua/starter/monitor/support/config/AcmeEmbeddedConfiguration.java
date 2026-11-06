package com.chua.starter.monitor.support.config;

import com.chua.starter.monitor.mapper.MonitorAcmeAccountMapper;
import com.chua.starter.monitor.mapper.MonitorAcmeCertificateMapper;
import com.chua.starter.monitor.mapper.MonitorAcmeOrderMapper;
import com.chua.starter.monitor.properties.AcmeProperties;
import com.chua.starter.monitor.service.impl.AcmeCertificateServiceImpl;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 把 ACME 证书模块并入主后端进程。
 *
 * <p>证书页与主机资产使用同一个后端：ACME 的控制器、服务与定时续签都在本进程内，
 * 前端 {@code /monitor/api/v1/cert/**} 直接由本进程处理，不再依赖独立的 19170 服务
 * （独立服务未随主后端部署时，证书列表必然 404）。</p>
 *
 * <p>这里只做三件事：扫描 acme 控制器与服务、注册 acme 的三个 mapper、绑定
 * {@link AcmeProperties}。不直接导入独立应用的 {@code AcmeConfiguration}——
 * 它会额外注册 {@code monitorServiceInfoContributor}，与主后端同名 Bean 冲突。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Configuration
@EnableConfigurationProperties(AcmeProperties.class)
@ConditionalOnProperty(prefix = "monitor.acme", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
        "com.chua.starter.monitor.controller.acme",
        "com.chua.starter.monitor.service.acme",
        "com.chua.starter.monitor.job"
})
@Import(AcmeCertificateServiceImpl.class)
public class AcmeEmbeddedConfiguration {

    /**
     * ACME 账户 Mapper。
     *
     * <p>不用 {@code @MapperScan} 扫整个 {@code com.chua.starter.monitor.mapper} 包：
     * 该包里还有与主后端同名的 {@code MsMonitorMapper} 等接口，
     * 整包扫描会让两个同名 Bean 互相覆盖。</p>
     *
     * @param sqlSessionTemplate MyBatis 模板
     * @return 账户 Mapper
     */
    @Bean
    public MonitorAcmeAccountMapper monitorAcmeAccountMapper(SqlSessionTemplate sqlSessionTemplate) {
        return sqlSessionTemplate.getMapper(MonitorAcmeAccountMapper.class);
    }

    /**
     * ACME 证书 Mapper。
     *
     * @param sqlSessionTemplate MyBatis 模板
     * @return 证书 Mapper
     */
    @Bean
    public MonitorAcmeCertificateMapper monitorAcmeCertificateMapper(SqlSessionTemplate sqlSessionTemplate) {
        return sqlSessionTemplate.getMapper(MonitorAcmeCertificateMapper.class);
    }

    /**
     * ACME 订单 Mapper。
     *
     * @param sqlSessionTemplate MyBatis 模板
     * @return 订单 Mapper
     */
    @Bean
    public MonitorAcmeOrderMapper monitorAcmeOrderMapper(SqlSessionTemplate sqlSessionTemplate) {
        return sqlSessionTemplate.getMapper(MonitorAcmeOrderMapper.class);
    }
}
