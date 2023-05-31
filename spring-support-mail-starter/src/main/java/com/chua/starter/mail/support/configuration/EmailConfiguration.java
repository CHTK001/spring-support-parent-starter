package com.chua.starter.mail.support.configuration;

import com.chua.starter.mail.support.properties.EmailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author CH
 */
@EnableConfigurationProperties(EmailProperties.class)
@ComponentScan("com.chua.starter.mail.support.controller")
public class EmailConfiguration {
}
