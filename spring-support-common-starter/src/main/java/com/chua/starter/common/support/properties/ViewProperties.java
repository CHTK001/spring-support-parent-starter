package com.chua.starter.common.support.properties;

import com.chua.common.support.view.ViewConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 预览
 *
 * @author CH
 * @since 2022/8/1 17:03
 */
@Data
@ConfigurationProperties(prefix = "plugin.view", ignoreInvalidFields = true)
public class ViewProperties {
    /**
     * 开启文件预览
     */
    private boolean openView = false;
    /**
     * 基础配置
     */
    private Map<String, ViewConfig> config;

}
