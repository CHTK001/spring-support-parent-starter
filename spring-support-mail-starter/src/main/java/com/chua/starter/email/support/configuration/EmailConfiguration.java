package com.chua.starter.email.support.configuration;

import com.chua.starter.email.support.properties.EmailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author CH
 */
@EnableConfigurationProperties(EmailProperties.class)
@ComponentScan("com.chua.starter.mail.support.controller")
public class EmailConfiguration {
}
