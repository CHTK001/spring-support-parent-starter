package com.chua.starter.milvus.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * minio
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MilvusProperties.PRE)
public class MilvusProperties {

    public static final String PRE = "plugin.spring.milvus";
    /**
     * 集合名称
     */
    private String collect = "face";
    /**
     * 分区
     */
    private String partitionName = "part";
    /**
     * 服务器地址
     */
    private String address;
    /**
     * 账号
     */
    private String username = "root";

    /**
     * 密码
     */
    private String password = "milvus";

    /**
     * 默认bucket
     * @return nb
     */
    public String getDefaultCollection() {
        return collect;
    }
}
