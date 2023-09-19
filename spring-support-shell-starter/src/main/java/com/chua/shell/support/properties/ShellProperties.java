package com.chua.shell.support.properties;

import com.google.common.collect.Lists;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @author CH
 */
@Data
@ConfigurationProperties("plugin.shell")
public class ShellProperties {
    /**
     *
     */
    private String welcome = "连接成功...";
    /**
     * 白名单
     */
    public List<String> ipPass = Lists.newArrayList("127.0.0.1", "0:0:0:0:0:0:0:1");
}
