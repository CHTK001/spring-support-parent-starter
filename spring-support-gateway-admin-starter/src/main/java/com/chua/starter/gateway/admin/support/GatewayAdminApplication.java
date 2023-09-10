package com.chua.starter.gateway.admin.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;

/**
 * 网关应用程序
 *
 * @author CH
 * @since 2023/09/10
 */
@SpringBootApplication(
        exclude = {LdapAutoConfiguration.class}
)
public class GatewayAdminApplication {

    public static void main(final String[] args) {
        SpringApplication.run(GatewayAdminApplication.class, args);
    }


}
