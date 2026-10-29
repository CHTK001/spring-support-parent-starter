package com.chua.starter.docker.model;

import java.io.Serializable;
import java.util.List;

/**
 * 创建卷请求（`docker volume create`）
 *
 * @author CH
 * @since 4.0.0.46
 */
public class DockerVolumeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 卷名称 */
    private String name;

    /** 驱动，默认 local */
    private String driver;

    /** 参数，如 {@code label=foo} */
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
