package com.chua.common.support.spi.definition;

/**
 * 服务定义类，描述 SPI 服务的基本信息
 *
 * @author CH
 */
public class ServiceDefinition {
    /**
     * 服务名称
     */
    private String name;

    /**
     * 构造服务定义实例
     *
     * @param name 服务名称
     */
    public ServiceDefinition(String name) {
        this.name = name;
    }

    /**
     * 获取服务名称
     *
     * @return 服务名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置服务名称
     *
     * @param name 服务名称
     */
    public void setName(String name) {
        this.name = name;
    }
}
