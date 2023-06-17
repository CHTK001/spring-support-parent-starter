package com.chua.starter.oss.support.pojo;

import lombok.Data;

import java.util.Properties;

/**
 * oss
 *
 * @author CH
 */
@Data
public class OssSystem {
    /**
     * 文件地址
     */
    private String path;
    /**
     * 实现类型
     */
    private String type = "local";
    /**
     * appkey
     */
    public String appKey;
    /**
     * appSecret
     */
    public String appSecret;
    /**
     * 块大小
     */
    private int buffer = 2048;
    /**
     * 是否覆蓋已有的文件
     */
    private boolean covering;
    /**
     * 命名策略
     */
    private String nameStrategy = "original";
    /**
     * 拒绝策略
     */
    private String rejectStrategy = "null";
    /**
     * 基础配置
     */
    private Properties properties;
    /**
     * 插件
     */
    private String plugins;
}
