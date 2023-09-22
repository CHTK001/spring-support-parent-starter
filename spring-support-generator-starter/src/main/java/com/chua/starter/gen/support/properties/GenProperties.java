package com.chua.starter.gen.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.gen")
public class GenProperties {

    /**
     * 作者
     */
    public String author;

    /**
     * 生成包路径
     */
    public String packageName;

    /**
     * 自动去除表前缀，默认是false
     */
    public boolean autoRemovePre;

    /**
     * 表前缀(类名不会包含表前缀)
     */
    public String tablePrefix;
}
