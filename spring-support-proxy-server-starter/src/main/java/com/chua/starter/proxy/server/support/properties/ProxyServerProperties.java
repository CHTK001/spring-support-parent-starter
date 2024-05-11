package com.chua.starter.proxy.server.support.properties;

import com.google.common.collect.Lists;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static com.chua.starter.proxy.support.properties.ProxyProperties.PRE;

/**
 * 代理
 *
 * @author CH
 * @since 2024/5/11
 */
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
@Data
public class ProxyServerProperties {

    public static final String PRE = "plugin.proxy.server";

    private List<Integer> ports = Lists.newArrayList(18480);
}
