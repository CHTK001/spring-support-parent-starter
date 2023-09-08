package com.chua.starter.config.server.support.repository;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;


/**
 * 配置中心
 * @author CH
 * @since 2022/8/1 13:03
 */
@Data
@Deprecated
@Entity(name = "CONFIGURATION_SUBSCRIBE_INFO")
@Table(name = "CONFIGURATION_SUBSCRIBE_INFO")
public class ConfigurationSubscribeInfo {
    /**
     * 配置ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer subscribeId;

    /**
     * 订阅的应用
     */
    @Column
    @Comment("订阅的应用")
    private String subscribeApplicationName;

    /**
     * 订阅的主机
     */
    @Column
    @Comment("订阅的主机")
    private String subscribeHost;
    /**
     * 订阅的主机端口
     */
    @Column
    @Comment("订阅的主机端口")
    private String subscribePort;
    /**
     * 订阅类型, config, bean
     */
    @Column
    @Comment("订阅类型, config, bean")
    private String subscribeType;
    /**
     * 订阅环境
     */
    @Column
    @Comment("订阅环境")
    private String subscribeProfile;

    @Override
    public String toString() {
        return "ConfigurationSubscribeInfo{" +
                "subscribeApplicationName='" + subscribeApplicationName + '\'' +
                ", subscribeHost='" + subscribeHost + '\'' +
                ", subscribePort='" + subscribePort + '\'' +
                ", subscribeType='" + subscribeType + '\'' +
                ", subscribeProfile='" + subscribeProfile + '\'' +
                '}';
    }
}
