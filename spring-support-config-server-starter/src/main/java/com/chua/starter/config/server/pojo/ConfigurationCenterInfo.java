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
@Entity(name = "CONFIGURATION_CENTER_INFO")
@Table(name = "CONFIGURATION_CENTER_INFO")
public class ConfigurationCenterInfo {
    /**
     * 配置ID
     */
    @Id
    @com.chua.common.support.database.annotation.Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer configId;

    /**
     *  配置所在配置名称
     */
    @Column
    @Comment("配置所在配置名称")
    private String configMappingName;
    /**
     *  名称
     */
    @Column
    @Comment("配置项名称")
    private String configName;
    /**
     *  描述
     */
    @Column
    @Comment("配置项描述")
    private String configDesc;


    /**
     *  值
     */
    @Column(columnDefinition = "text")
    @Comment("配置项值")
    private String configValue;

    /**
     * 所属
     */
    @Column
    @Comment("所属配置模块")
    private String configItem;

    /**
     * 所属
     */
    @Column
    @Comment("配置环境")
    private String configProfile;
    /**
     * 配置开启条件
     */
    @Column
    @Comment("处理条件")
    private String configCondition;
    /**
     * 所属
     */
    @Column
    @Comment("是否禁用, 0: 开启")
    private Integer disable = 0;
}
