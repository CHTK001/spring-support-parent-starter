package com.chua.starter.common.support.pojo;

import com.chua.common.support.datasource.annotation.Column;
import com.chua.common.support.datasource.annotation.Id;
import com.chua.common.support.datasource.annotation.Table;
import lombok.Data;

import javax.persistence.GeneratedValue;


/**
 * 编排
 *
 * @author CH
 */
@Data
@Table(comment = "编排节点")
public class SysArrangeNode {

    @Id
    @GeneratedValue
    private Integer arrangeEdgeId;
    /**
     * 编排ID
     */
    @Column(comment = "编排ID")
    private Integer arrangeId;
    /**
     * 节点ID
     */
    @Column(comment = "节点ID")
    private String id;
    /**
     * 真实ID
     */
    @Column(comment = "真实ID")
    private String realId;
    /**
     * 节点用户数据
     */
    @Column(comment = "节点用户数据")
    private String userData;
    /**
     * 节点菜单
     */
    @Column(comment = "节点菜单")
    private String menus;


    /**
     * 位置
     */
    @Column(comment = "上方")
    private String top;

    /**
     * 位置
     */
    @Column(value = "`left`", comment = "左侧")
    private String left;
    /**
     * 节点样式
     */
    @Column(comment = "节点样式", defaultValue = "'icon-background-color'")
    private String className;
    /**
     * 节点图标类型
     */
    @Column(comment = "节点图标类型", defaultValue = "'icon-bofang'")
    private String iconType;
    /**
     * 配置名称
     */
    @Column(comment = "节点名称")
    private String label;

}
