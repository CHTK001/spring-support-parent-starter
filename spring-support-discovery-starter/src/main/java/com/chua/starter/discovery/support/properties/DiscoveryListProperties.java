package com.chua.starter.discovery.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;

/**
 * 发现配置
 * @author CH
 * @since 2024/9/9
 */
@Data
@ConfigurationProperties(prefix = DiscoveryListProperties.PRE, ignoreInvalidFields = true)
public class DiscoveryListProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.discovery";

    /**
     * 发现配置
     */
    private List<DiscoveryProperties> properties = new LinkedList<>();
    
    // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    public boolean isEnable() {
        return enable;
    }
    
    public void setEnable(boolean enable) {
        this.enable = enable;
    }
    
    public List<DiscoveryProperties> getProperties() {
        return properties;
    }
    
    public void setProperties(List<DiscoveryProperties> properties) {
        this.properties = properties;
    }

}
