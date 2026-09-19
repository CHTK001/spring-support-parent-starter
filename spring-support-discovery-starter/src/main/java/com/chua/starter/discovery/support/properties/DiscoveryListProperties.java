package com.chua.starter.discovery.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;

/**
 * 发现配置
 * @since 2024/9/9
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = DiscoveryListProperties.PRE, ignoreInvalidFields = true)
public class DiscoveryListProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /** PRE */
    public static final String PRE = "plugin.discovery";

    /**
     * 发现配置
     */
    private List<DiscoveryProperties> properties = new LinkedList<>();
    
 // Lombok @数据 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    /**
     * 是否启用
     *
     * @return 是否enable的结果
     */
    public boolean isEnable() {
        return enable;
    }
    
    /**
     * 设置启用
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }
    
    /**
     * 获取属性
     *
     * @return 获取属性的结果
     */
    public List<DiscoveryProperties> getProperties() {
        return properties;
    }
    
    /**
     * 设置属性
     *
     * @param properties 属性
     */
    public void setProperties(List<DiscoveryProperties> properties) {
        this.properties = properties;
    }

}
