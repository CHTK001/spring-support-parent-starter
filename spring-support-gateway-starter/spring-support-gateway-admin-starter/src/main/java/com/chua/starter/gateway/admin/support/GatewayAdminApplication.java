package com.chua.starter.gateway.admin.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;

/**
 * shenyu admin start.
 */
@SpringBootApplication(exclude = {LdapAutoConfiguration.class})
public class GatewayAdminApplication {

    /**
     * Main entrance.
     *
     * @param args startup arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(GatewayAdminApplication.class, args);
    }

}
