package com.chua.starter.config.plugin;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.entity.KeyValue;
import com.chua.starter.config.protocol.ProtocolProvider;
import lombok.Setter;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 执行器插件
 *
 * @author CH
 * @since 2023/09/07
 */
@Spi(ConfigConstant.ACTUATOR)
public class ActuatorPlugin implements Plugin, EnvironmentAware {
    private Environment environment;

    @Setter
    private ProtocolProvider protocolProvider;

    @Override
    public void onListener(KeyValue keyValue, Object response) {
        System.out.println();
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
