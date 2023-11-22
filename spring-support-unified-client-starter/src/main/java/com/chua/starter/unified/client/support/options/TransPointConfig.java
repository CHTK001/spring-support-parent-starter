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

    private String path;

    private String endpoint = "unified";
}
