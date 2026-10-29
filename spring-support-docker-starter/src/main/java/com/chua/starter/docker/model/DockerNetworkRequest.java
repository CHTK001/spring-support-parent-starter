package com.chua.starter.docker.model;

import java.io.Serializable;
import java.util.List;

/**
 * 创建网络请求（`docker network create`）
 *
 * @author CH
 * @since 4.0.0.46
 */
public class DockerNetworkRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网络名称 */
    private String name;

    /** 驱动，默认 bridge */
    private String driver;

    /** 参数，如 {@code com.docker.network.bridge.name=br-x} */
    private List<String> options;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}
