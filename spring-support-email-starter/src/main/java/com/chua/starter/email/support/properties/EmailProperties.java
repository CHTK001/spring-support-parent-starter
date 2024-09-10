package com.chua.starter.email.support.properties;

import com.chua.common.support.lang.mail.MailConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ConfigurationProperties(prefix = "plugin.email")
public class EmailProperties extends MailConfiguration {
}
