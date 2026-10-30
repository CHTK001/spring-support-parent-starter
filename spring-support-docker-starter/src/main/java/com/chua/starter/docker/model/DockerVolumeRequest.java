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

    /**
     * get Name
     * @return 返回值
    */
    public String getName() {
        return name;
    }

    /**
     * set Name
     * @param name 字符串
     * @return 返回值
    */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * get Driver
     * @return 返回值
    */
    public String getDriver() {
        return driver;
    }

    /**
     * set Driver
     * @param driver 字符串
     * @return 返回值
    */
    public void setDriver(String driver) {
        this.driver = driver;
    }

    /**
     * get Options
     * @return 返回值
    */
    public List<String> getOptions() {
        return options;
    }

    /**
     * set Options
     * @param options 参数
     * @return 返回值
    */
    public void setOptions(List<String> options) {
        this.options = options;
    }
}
