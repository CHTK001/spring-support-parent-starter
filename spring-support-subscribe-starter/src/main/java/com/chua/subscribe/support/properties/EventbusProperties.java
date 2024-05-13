package com.chua.subscribe.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.subscribe.support.properties.EventbusProperties.PRE;

/**
 * @author CH
 */

@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class EventbusProperties {

    public static final String PRE = "plugin.eventbus";

}
