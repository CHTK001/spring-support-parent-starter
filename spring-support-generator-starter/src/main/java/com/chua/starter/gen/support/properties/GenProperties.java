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
    private String author;


    /**
     * 临时路径
     */
    private String tempPath = "gen";
    /**
     * 生成包路径
     */
    private String packageName;
    /**
     * 模板路径
     */

    private String templatePath;
    /**
     * 自动去除表前缀，默认是false
     */
    private boolean autoRemovePre;

    /**
     * 表前缀(类名不会包含表前缀)
     */
    private String tablePrefix;
}
