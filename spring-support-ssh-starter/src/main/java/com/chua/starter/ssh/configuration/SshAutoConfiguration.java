package com.chua.starter.ssh.configuration;

import com.chua.starter.ssh.properties.SshServerProperties;
import com.chua.starter.ssh.service.SshServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * SSH服务端自动配置类
 * 
 * 提供SSH服务端功能的自动配置，包括：
 * - SSH服务端启动和配置
 * - 用户认证配置
 * - 文件传输配置
 * - 安全配置
 * 
 * @author CH
 * @version 4.0.0.32
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "org.apache.sshd.server.SshServer")
@ConditionalOnProperty(prefix = "plugin.ssh.server", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(SshServerProperties.class)
public class SshAutoConfiguration {
    
    // Lombok @Slf4j 生成的 log 变量（如果 Lombok 未生效，这个变量会被使用）
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SshAutoConfiguration.class);

    /**
     * SSH服务端服务Bean
     *
     * @param properties SSH服务端配置属性
     * @return SSH服务端服务实例
     */
    @Bean
    public SshServerService sshServerService(SshServerProperties properties) {
        log.info(" >>>>>>> 初始化SSH服务端服务，端口: {}", properties.getPort());
        return new SshServerService(properties);
    }
}
