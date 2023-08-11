package com.chua.starter.config.server.pojo;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

/**
 * 配置中心
 * @author CH
 * @since 2022/8/1 13:03
 */
@Data
@Entity(name = "CONFIGURATION_DISTRIBUTE_INFO")
@Table(name = "CONFIGURATION_DISTRIBUTE_INFO")
public class ConfigurationDistributeInfo {
    /**
     * 配置ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer distributeId;

    /**
     *  名称
     */
    @Column
    @Comment("所属配置模块")
    private String configItem;

    /**
     *  值
     */
    @Column
    @Comment("所属配置ID")
    private Integer configId;
    /**
     * 配置开启条件
     */
    @Column
    @Comment("处理条件")
    private String distributeCondition;
}
