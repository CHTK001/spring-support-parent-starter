package com.chua.starter.config.server.pojo;

import lombok.Data;
import org.hibernate.annotations.Comment;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;


/**
 * 配置中心
 *
 * @author CH
 * @since 2022/8/1 13:03
 */
@Data
@Entity(name = "CONFIGURATION_MAPPING")
@Table(name = "CONFIGURATION_MAPPING")
public class ConfigurationMapping {

    /**
     * 主键
     */
    @Id
    @com.chua.common.support.database.annotation.Id
    private String mapId;

    /**
     * 地址
     * e.q./v2/dept/batchDelete
     */
    @Column
    @Comment("Mapping: 请求地址, Bean: 文件地址")
    private String mapMapping;
    /**
     * 方法
     * e.q.DELETE,GET
     */
    @Column
    @Comment("方法")
    private String mapMethod;
    /**
     * 类
     * e.q.class com.chua.starter.system.support.controller.SysDeptController
     */
    @Column
    @Comment("Mapping:类, Bean: 接口类型")
    private String mapHandlerType;
    /**
     * 方法名
     * e.q.batchDelete
     */
    @Column
    @Comment("Mapping:方法名, Bean: Bean名称")
    private String mapHandlerName;
    /**
     * 应用名称
     * e.q.system
     */
    @Comment("应用名称")
    private String mapApplicationName;
    /**
     * 类型
     * e.q.MAPPING,BEAN
     */
    @Column(columnDefinition = "DEFAULT 'MAPPING'")
    @Comment("类型;MAPPING,BEAN")
    private String mapType;
    /**
     * 是否禁用
     * e.q.system
     */
    @Column(columnDefinition = "DEFAULT 0")
    @Comment("是否禁用;0:启用")
    private Integer mapApplicationStatus;
    /**
     * 描述
     * e.q.system
     */
    @Column(length = 300)
    @Comment("描述")
    private String mapMarker;

    @Override
    public String toString() {
        return "SysMapping{" +
                "mapMapping='" + mapMapping + '\'' +
                ", mapMethod='" + mapMethod + '\'' +
                ", mapHandlerType='" + mapHandlerType + '\'' +
                ", mapHandlerName='" + mapHandlerName + '\'' +
                ", mapApplicationName='" + mapApplicationName + '\'' +
                ", mapType='" + mapType + '\'' +
                '}';
    }
}
