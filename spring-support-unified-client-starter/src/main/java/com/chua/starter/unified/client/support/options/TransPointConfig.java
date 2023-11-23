package com.chua.starter.unified.client.support.options;

import lombok.Data;

/**
 * @author CH
 */
@Data
public class TransPointConfig {

    private String url;
    private String appName;

    private String port;
    /**
     * attach目录
     */
    private String path;
    /**
     * 热重载目录
     */
    private String hotspot;
    private String endpoint = "unified";
}
