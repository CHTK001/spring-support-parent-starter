package com.chua.starter.project.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 发布项目管理模块自动配置
 * <p>
   * 扫描部署项目/部署日志全部组件与 MyBatis-Plus 映射器，开启事务管理。
   * 远程部署/升级/启停复用 spring-支持-服务端-启动 提供的 SSH 连接池、
 * 主机凭据解析与 SFTP 能力。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.project.support")
@MapperScan(basePackages = "com.chua.starter.project.support.mapper")
@EnableTransactionManagement
public class ProjectAutoConfiguration {

    /**
      * 发布项目管理模块的 服务信息 贡献者。
     *
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor projectServiceInfoContributor() {
        return registry -> registry.registerFlag("project", "发布项目管理", true,
                "发布项目管理模块",
                "[部署/升级/启停脚本/部署日志]");
    }
}