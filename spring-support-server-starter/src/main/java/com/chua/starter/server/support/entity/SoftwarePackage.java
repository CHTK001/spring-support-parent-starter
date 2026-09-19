package com.chua.starter.server.support.entity;

import lombok.Data;

/**
 * 软件包信息
 *
 * @author CH
 */
@Data
public class SoftwarePackage {

    /**
     * 软件包名称
     */
    private String name;

    /**
     * 软件包描述
     */
    private String summary;

    /**
     * 版本
     */
    private String version;
}
