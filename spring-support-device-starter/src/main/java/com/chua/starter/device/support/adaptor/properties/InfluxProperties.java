package com.chua.starter.device.support.adaptor.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.device.support.adaptor.properties.InfluxProperties.PRE;

/**
 * 注入特性
 *
 * @author CH
 * @since 2023/10/30
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class InfluxProperties {
    static final String PRE = "plugin.server.influx";

    private String url = "http://127.0.0.1:8086";

    private String token = "lgl6T843dsLGTFmOtEo_J_o9mrHXmLxSmC1SWuWSfUXyCTK0tS4KcSQBYPFzpdaAY-vdkzmLq4p5JLT25yzo3w==";
    private String username;
    private String password ;

    private String bucket = "device";

}
