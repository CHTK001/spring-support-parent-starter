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
@com.chua.common.support.database.annotation.Table(comment = "编排关系")
@Entity
public class SysArrangeEdge {

    @Id
    @com.chua.common.support.database.annotation.Id
    @GeneratedValue
    private Integer arrangeNodeId;
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
     * 节点用户数据
     */
    @com.chua.common.support.database.annotation.Column(comment = "节点用户数据")
    private String userData;
    /**
     * 来源
     */
    @com.chua.common.support.database.annotation.Column(comment = "来源")
    private String sourceNode;
    /**
     * 目标
     */
    @com.chua.common.support.database.annotation.Column(comment = "目标")
    private String targetNode;
    /**
     * 铆点来源
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "铆点来源", defaultValue = "'right'")
    private String source;
    /**
     * 铆点目标
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "铆点目标", defaultValue = "'left'")
    private String target;


}
