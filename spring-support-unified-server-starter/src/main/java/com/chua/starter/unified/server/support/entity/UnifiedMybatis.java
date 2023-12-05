package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName(value = "unified_mybatis")
public class UnifiedMybatis implements Serializable {
    @TableId(value = "unified_mybatis_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedMybatisId;

    /**
     * 配置名称
     */
    @TableField(value = "unified_mybatis_name")
    @Size(max = 255, message = "配置名称最大长度要小于 255")
    private String unifiedMybatisName;

    /**
     * 配置名称
     */
    @TableField(value = "unified_mybatis_desc")
    @Size(max = 255, message = "描述")
    private String unifiedMybatisDesc;

    /**
     * 配置SQL
     */
    @TableField(value = "unified_mybatis_sql")
    @Size(max = 255, message = "配置SQL最大长度要小于 255")
    private String unifiedMybatisSql;

    /**
     * SQL类型； sql, xml
     */
    @TableField(value = "unified_mybatis_sql_type")
    @Size(max = 255, message = "SQL类型； sql, xml最大长度要小于 255")
    private String unifiedMybatisSqlType;

    /**
     * 实体类
     */
    @TableField(value = "unified_mybatic_model_type")
    @Size(max = 255, message = "实体类最大长度要小于 255")
    private String unifiedMybaticModelType;

    /**
     * Mapper类型
     */
    @TableField(value = "unified_mybatic_mapper_type")
    @Size(max = 255, message = "Mapper类型最大长度要小于 255")
    private String unifiedMybaticMapperType;

    /**
     * 环境
     */
    @TableField(value = "unified_mybatis_profile")
    @Size(max = 255, message = "环境最大长度要小于 255")
    private String unifiedMybatisProfile;

    /**
     * 应用名称
     */
    @TableField(value = "unified_appname")
    @Size(max = 255, message = "应用名称最大长度要小于 255")
    private String unifiedAppname;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}