package com.chua.starter.common.support.pojo;

import lombok.Data;

import javax.persistence.*;

/**
 * 编排
 *
 * @author CH
 */
@Data
@Table
@com.chua.common.support.database.annotation.Table(comment = "编排节点")
@Entity
public class SysArrangeNode {

    @Id
    @com.chua.common.support.database.annotation.Id
    @GeneratedValue
    private Integer arrangeEdgeId;
    /**
     * 编排ID
     */
    @com.chua.common.support.database.annotation.Column(comment = "编排ID")
    private Integer arrangeId;
    /**
     * 节点ID
     */
    @com.chua.common.support.database.annotation.Column(comment = "节点ID")
    private String id;
    /**
     * 真实ID
     */
    @com.chua.common.support.database.annotation.Column(comment = "真实ID")
    private String realId;
    /**
     * 节点用户数据
     */
    @com.chua.common.support.database.annotation.Column(comment = "节点用户数据")
    private String userData;
    /**
     * 节点菜单
     */
    @com.chua.common.support.database.annotation.Column(comment = "节点菜单")
    private String menus;


    /**
     * 位置
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "上方")
    private String top;

    /**
     * 位置
     */
    @Column
    @com.chua.common.support.database.annotation.Column(value = "`left`", comment = "左侧")
    private String left;
    /**
     * 节点样式
     */
    @com.chua.common.support.database.annotation.Column(comment = "节点样式", defaultValue = "'icon-background-color'")
    private String className;
    /**
     * 节点图标类型
     */
    @com.chua.common.support.database.annotation.Column(comment = "节点图标类型", defaultValue = "'icon-bofang'")
    private String iconType;
    /**
     * 配置名称
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "节点名称")
    private String label;

}
