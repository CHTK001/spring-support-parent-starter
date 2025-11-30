package com.chua.starter.common.support.pojo;

import com.chua.common.support.datasource.annotation.ColumnDefinition;
import com.chua.common.support.datasource.annotation.Id;
import com.chua.common.support.datasource.annotation.TableDefinition;
import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;


/**
 * 编排
 *
 * @author CH
 */
@Data
@TableDefinition(comment = "编排关系")
@Entity
public class SysArrangeEdge {

    @Id
    @GeneratedValue
    private Integer arrangeNodeId;
    /**
     * 编排ID
     */
    @ColumnDefinition(comment = "编排ID")
    private Integer arrangeId;
    /**
     * 节点ID
     */
    @ColumnDefinition(comment = "节点ID")
    private String id;
    /**
     * 节点用户数据
     */
    @ColumnDefinition(comment = "节点用户数据")
    private String userData;
    /**
     * 来源
     */
    @ColumnDefinition(comment = "来源")
    private String sourceNode;
    /**
     * 目标
     */
    @ColumnDefinition(comment = "目标")
    private String targetNode;
    /**
     * 铆点来源
     */
    @ColumnDefinition(comment = "铆点来源", defaultValue = "'right'")
    private String source;
    /**
     * 铆点目标
     */
    @ColumnDefinition(comment = "铆点目标", defaultValue = "'left'")
    private String target;


}
