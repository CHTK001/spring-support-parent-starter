package com.chua.starter.common.support.pojo;

import com.chua.common.support.database.entity.JdbcType;
import lombok.Data;

import javax.persistence.*;

/**
 * 编排
 *
 * @author CH
 */
@Data
@Table
@com.chua.common.support.database.annotation.Table(comment = "编排")
@Entity
public class SysArrange {

    @Id
    @com.chua.common.support.database.annotation.Id
    @GeneratedValue
    private Integer arrangeId;

    /**
     * 配置名称
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "配置名称")
    private String arrangeName;

    /**
     * 编排配置
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "编排配置", jdbcType = JdbcType.LONGTEXT)
    private String arrangeContent;

}
