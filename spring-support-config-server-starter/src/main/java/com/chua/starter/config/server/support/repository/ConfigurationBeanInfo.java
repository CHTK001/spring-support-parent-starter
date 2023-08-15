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
@Entity(name = "CONFIGURATION_BEAN_INFO")
@Table(name = "CONFIGURATION_BEAN_INFO")
public class ConfigurationBeanInfo {
    /**
     * 配置ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer beanId;

    /**
     * 配置所在配置名称
     */
    @Column
    @Comment("类型")
    private String beanTypeName;
    /**
     * beanName
     */
    @Column
    @Comment("beanName")
    private String beanName;
    /**
     * 额外定义
     */
    @Column
    @Comment("额外定义")
    private String beanDefinition;
    /**
     * 名称
     */
    @Column
    @Comment("文件目录")
    private String beanFilePath;
    /**
     * 描述
     */
    @Column
    @Comment("所属应用")
    private String beanApplicationName;
    /**
     * 所属
     */
    @Column
    @Comment("配置环境")
    private String beanProfile;
    /**
     * 所属
     */
    @Column
    @Comment("描述")
    private String beanMarker;
    /**
     * 所属
     */
    @Comment("是否禁用, 0: 开启")
    @Column(columnDefinition = "int DEFAULT 0")
    private Integer disable = 0;
}
